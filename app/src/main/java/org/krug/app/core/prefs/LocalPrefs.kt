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
     * Ključ izabranog map stila (MapStyleOption.name, npr. "STANDARD", "DARK", "SATELLITE",
     * "OUTDOORS"). Držimo string umesto enum vrednosti zbog toga što `LocalPrefs` ne treba
     * da zna za Mapbox tipove. Konzumenti pozivaju `MapStyleOption.fromKey(value)` za
     * mapiranje na URI. Nepoznata vrednost (npr. downgrade, obrisan enum) fallback-uje na
     * DEFAULT (Standard) bez crash-a.
     */
    private val _mapStyleKey = MutableStateFlow(prefs.getString(KEY_MAP_STYLE, null) ?: DEFAULT_MAP_STYLE_KEY)
    val mapStyleKeyFlow: StateFlow<String> = _mapStyleKey.asStateFlow()

    fun setMapStyleKey(key: String) {
        prefs.edit(commit = false) { putString(KEY_MAP_STYLE, key) }
        _mapStyleKey.value = key
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
     * Pending invite kod iz deep link-a. StateFlow u `InviteFocusBus` ne preživljava process
     * death. Ako user klikne `krug://invite/{code}` dok nije auth-ovan (ili se app crashe
     * pre nav-a na EnterCode), kod bi bio izgubljen. Ovim persist-ujemo do successful
     * consume-a — InviteFocusBus.request() poziva ovaj setter, consume() briše.
     */
    var pendingInviteCode: String?
        get() = prefs.getString(KEY_PENDING_INVITE_CODE, null)
        set(value) = prefs.edit(commit = false) {
            if (value == null) remove(KEY_PENDING_INVITE_CODE)
            else putString(KEY_PENDING_INVITE_CODE, value)
        }

    /**
     * Poslednji viđeni timestamp `placeEvent`-a per krug. LocationTrackingService čita ovo
     * pri startup-u umesto `System.currentTimeMillis()` fallback-a i ignoriše event-e
     * čiji je timestamp <= sačuvane vrednosti. Rešava replay bug: user ubije app dok je
     * Jelena u nekom Place-u; kad user restartuje, in-memory `placesListenerStartedAt`
     * bio bi trenutni, ali ako Jelenin klijent u međuvremenu upiše nov event (spurious
     * Play Services reconciliation), taj event bi propao kroz filter jer je timestamp
     * mlađi od restart-a. Persist-ujemo max viđeni ts po krugu da se guardujemo od svega.
     *
     * Format: "cid1:ts1,cid2:ts2,..." (isti pattern kao SOS dedup).
     */
    fun loadLastSeenPlaceEventTs(): MutableMap<String, Long> {
        val raw = prefs.getString(KEY_LAST_SEEN_PLACE_EVENT_TS, null).orEmpty()
        if (raw.isBlank()) return mutableMapOf()
        return raw.split(',').mapNotNull { entry ->
            val parts = entry.split(':')
            if (parts.size != 2) return@mapNotNull null
            val cid = parts[0]
            val ts = parts[1].toLongOrNull() ?: return@mapNotNull null
            if (cid.isBlank()) null else cid to ts
        }.toMap().toMutableMap()
    }

    fun saveLastSeenPlaceEventTs(map: Map<String, Long>) {
        val serialized = map.entries.joinToString(",") { "${it.key}:${it.value}" }
        prefs.edit(commit = false) { putString(KEY_LAST_SEEN_PLACE_EVENT_TS, serialized) }
    }

    /**
     * Battery alert dedup — LocationTrackingService okida notifikaciju kad član kruga
     * padne ispod 20% baterije. Da bismo izbegli spam, čuvamo timestamp poslednjeg
     * alert-a per uid. Ako je manje od 12h od zadnjeg (BATTERY_ALERT_TTL_MS),
     * ne re-fire-ujemo. Kad user napuni telefon iznad ~25% (hysteresis), FGS
     * uklanja entry pa sledeći drop opet trigeruje.
     *
     * Format: "uid1:ts1,uid2:ts2,..." (isti pattern kao SOS dedup).
     */
    fun loadBatteryAlerted(ttlMs: Long): MutableMap<String, Long> {
        val raw = prefs.getString(KEY_BATTERY_ALERTED, null).orEmpty()
        if (raw.isBlank()) return mutableMapOf()
        val now = System.currentTimeMillis()
        val parsed = raw.split(',').mapNotNull { entry ->
            val parts = entry.split(':')
            if (parts.size != 2) return@mapNotNull null
            val uid = parts[0]
            val ts = parts[1].toLongOrNull() ?: return@mapNotNull null
            if (uid.isBlank() || now - ts > ttlMs) null else uid to ts
        }.toMap().toMutableMap()
        if (parsed.size != raw.count { it == ',' } + 1) saveBatteryAlerted(parsed)
        return parsed
    }

    fun saveBatteryAlerted(map: Map<String, Long>) {
        val serialized = map.entries.joinToString(",") { "${it.key}:${it.value}" }
        prefs.edit(commit = false) { putString(KEY_BATTERY_ALERTED, serialized) }
    }

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
            remove(KEY_LAST_SEEN_PLACE_EVENT_TS)
            remove(KEY_BATTERY_ALERTED)
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
        const val KEY_PENDING_INVITE_CODE = "pending_invite_code"
        const val KEY_LAST_SEEN_PLACE_EVENT_TS = "last_seen_place_event_ts"
        const val KEY_MAP_STYLE = "map_style_key"
        const val DEFAULT_MAP_STYLE_KEY = "STANDARD"
        const val KEY_BATTERY_ALERTED = "battery_alerted_ts"
    }
}
