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
    // Sve write-ove radimo sa apply() (commit=false) — async write na background thread,
    // istog momenta visible u istom procesu. Sync commit() je sync disk I/O na Main →
    // ANR risk čak i pri 5s budget-u, naročito kad app spori prefs fajl. Ako se proces
    // ubije pre nego što flush stigne na disk, samo dio writes-a se gubi — to je
    // prihvatljiv tradeoff za ANR-free initial sign-in / circle switch.
    private val prefs = context.getSharedPreferences("krug_prefs", Context.MODE_PRIVATE)

    var onboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit(commit = false) { putBoolean(KEY_ONBOARDING_DONE, value) }

    /** Poslednji versionCode za koji je user video "šta je novo" modal. */
    var lastSeenWhatsNewVersion: Int
        get() = prefs.getInt(KEY_LAST_SEEN_WHATS_NEW, 0)
        set(value) = prefs.edit(commit = false) { putInt(KEY_LAST_SEEN_WHATS_NEW, value) }

    private val _activeCircleId = MutableStateFlow(prefs.getString(KEY_ACTIVE_CIRCLE, null))

    /** Reactive — UI observe-uje, fallback na prvi krug ako null/nevažeći. */
    val activeCircleIdFlow: StateFlow<String?> = _activeCircleId.asStateFlow()

    fun setActiveCircleId(id: String?) {
        prefs.edit(commit = false) {
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

    /**
     * GDPR delete recovery — kad user pokrene "Obriši nalog", upisujemo njegov uid
     * pre nego što ulećemo u Firestore/RTDB cleanup. Ako Firebase Auth.delete() traži
     * recent re-login i fail-uje, ovaj flag ostaje i SplashViewModel pri sledećem
     * startu detektuje "ghost" stanje (data obrisana, auth još živ) pa retry-uje
     * cleanup ili force-uje signOut.
     */
    var pendingDeleteUid: String?
        get() = prefs.getString(KEY_PENDING_DELETE_UID, null)
        set(value) = prefs.edit(commit = false) {
            if (value == null) remove(KEY_PENDING_DELETE_UID)
            else putString(KEY_PENDING_DELETE_UID, value)
        }

    /**
     * Activity Recognition (ACTIVITY_RECOGNITION) rationale prompt — flag se postavlja
     * čim user vidi naš pre-prompt dialog (bilo "Dozvoli" ili "Ne sada"). Sprečava da se
     * dijalog ponovo pojavljuje pri svakom ulasku na Mapu. Ako user kasnije hoće da
     * uključi, ide kroz sistemska podešavanja (Settings → Apps → Krug → Permissions).
     */
    var activityRecPromptShown: Boolean
        get() = prefs.getBoolean(KEY_ACTIVITY_REC_PROMPT_SHOWN, false)
        set(value) = prefs.edit(commit = false) { putBoolean(KEY_ACTIVITY_REC_PROMPT_SHOWN, value) }

    /**
     * Poslednja ms vrednost kad je user video battery-optimization re-prompt dialog.
     * Onboarding pita ovo jednom; ako user skip-uje, OEM battery restrikcije će početi
     * da mu gase FGS i lokacija će ispadati offline. Prikazujemo dijalog ponovo max
     * jednom nedeljno (7d cooldown) da user ima šansu da fix-uje bez UI noise-a.
     * 0 = nikada nije viđen (fresh install ili pre uvođenja feature-a).
     */
    var lastBatteryPromptMs: Long
        get() = prefs.getLong(KEY_LAST_BATTERY_PROMPT_MS, 0L)
        set(value) = prefs.edit(commit = false) { putLong(KEY_LAST_BATTERY_PROMPT_MS, value) }

    /**
     * GDPR — pozvati nakon delete-account ili reinstall recovery-ja. Briše sve per-account
     * state da novi sign-in ne nasledi stari `activeCircleId` (ne postoji više), `sos_notified`
     * dedup ili `onboardingCompleted` flag (novi nalog treba čist onboarding). `pendingDeleteUid`
     * čisti pozivalac eksplicitno (recovery logic to drži).
     */
    fun clearForAccountReset() {
        prefs.edit(commit = false) {
            remove(KEY_ONBOARDING_DONE)
            remove(KEY_ACTIVE_CIRCLE)
            remove(KEY_SOS_NOTIFIED)
            remove(KEY_ACTIVITY_REC_PROMPT_SHOWN)
        }
        _activeCircleId.value = null
    }

    private companion object {
        const val KEY_ONBOARDING_DONE = "onboarding_completed"
        const val KEY_ACTIVE_CIRCLE = "active_circle_id"
        const val KEY_SOS_NOTIFIED = "sos_notified_ts"
        const val KEY_PENDING_DELETE_UID = "pending_delete_uid"
        const val KEY_ACTIVITY_REC_PROMPT_SHOWN = "activity_rec_prompt_shown"
        const val KEY_LAST_SEEN_WHATS_NEW = "last_seen_whats_new_version"
        const val KEY_LAST_BATTERY_PROMPT_MS = "last_battery_prompt_ms"
    }
}
