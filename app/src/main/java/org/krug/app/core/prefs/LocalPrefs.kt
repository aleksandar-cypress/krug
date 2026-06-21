package org.krug.app.core.prefs

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class LocalPrefs @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("krug_prefs", Context.MODE_PRIVATE)

    var onboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit(commit = true) { putBoolean(KEY_ONBOARDING_DONE, value) }

    private val _activeCircleId = MutableStateFlow(prefs.getString(KEY_ACTIVE_CIRCLE, null))

    /** Reactive — UI observe-uje, fallback na prvi krug ako null/nevažeći. */
    val activeCircleIdFlow: StateFlow<String?> = _activeCircleId.asStateFlow()

    fun setActiveCircleId(id: String?) {
        prefs.edit(commit = true) {
            if (id == null) remove(KEY_ACTIVE_CIRCLE) else putString(KEY_ACTIVE_CIRCLE, id)
        }
        _activeCircleId.value = id
    }

    /**
     * SOS dedup persistence — `LocationTrackingService` koristi ovo umesto in-memory mape
     * da ne re-fire-uje notifikaciju za isti `triggeredAt` posle FGS process restart-a
     * (Android može da nas ubije zbog memorije pa restartuje preko START_STICKY-a, ili
     * BootReceiver-a; bez persistence-a, isti SOS bi opet zvonio).
     *
     * Format: "uid1:ts1,uid2:ts2,..." (jeftin string serialize bez JSON-a).
     * TTL prune se radi pri svakom load-u — entry-ji stariji od SOS_TTL_MS se filtriraju.
     */
    fun loadSosNotified(ttlMs: Long): MutableMap<String, Long> {
        val raw = prefs.getString(KEY_SOS_NOTIFIED, null).orEmpty()
        if (raw.isBlank()) return mutableMapOf()
        val now = System.currentTimeMillis()
        val parsed = raw.split(',').mapNotNull { entry ->
            val parts = entry.split(':')
            if (parts.size != 2) return@mapNotNull null
            val uid = parts[0]
            val ts = parts[1].toLongOrNull() ?: return@mapNotNull null
            if (uid.isBlank() || now - ts > ttlMs) null else uid to ts
        }.toMap().toMutableMap()
        // Ako smo isfiltrirali zastarele entry-je, prepiši storage da ne raste.
        if (parsed.size != raw.count { it == ',' } + 1) saveSosNotified(parsed)
        return parsed
    }

    fun saveSosNotified(map: Map<String, Long>) {
        val serialized = map.entries.joinToString(",") { "${it.key}:${it.value}" }
        prefs.edit(commit = false) { putString(KEY_SOS_NOTIFIED, serialized) }
    }

    private companion object {
        const val KEY_ONBOARDING_DONE = "onboarding_completed"
        const val KEY_ACTIVE_CIRCLE = "active_circle_id"
        const val KEY_SOS_NOTIFIED = "sos_notified_ts"
    }
}
