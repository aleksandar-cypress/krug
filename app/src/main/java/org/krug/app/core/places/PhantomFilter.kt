package org.krug.app.core.places

/**
 * Pure phantom-detection helper — izvučeno iz `GeofenceBroadcastReceiver.onReceive`
 * radi testabilnosti bez Play Services/Firebase dependency-ja. Zove ga receiver,
 * a unit testovi ga direktno pozivaju kroz sve edge case-ove.
 *
 * Decision matrix (ENTER=TYPE_ENTER, EXIT=TYPE_EXIT):
 *  | verify        | type  | prevType | outcome                          |
 *  |---------------|-------|----------|----------------------------------|
 *  | Confirmed IN  | EXIT  | *        | Skip (phantom, user je unutra)   |
 *  | Confirmed OUT | ENTER | *        | Skip (phantom, user je van)      |
 *  | Confirmed IN  | ENTER | ENTER    | Skip (repeated, semantic guard)  |
 *  | Confirmed OUT | EXIT  | EXIT     | Skip (repeated, semantic guard)  |
 *  | Confirmed IN  | ENTER | !ENTER   | Allow                            |
 *  | Confirmed OUT | EXIT  | !EXIT    | Allow                            |
 *  | Inconclusive  | EXIT  | fresh ENTER| Allow (imamo dokaz ulaska)     |
 *  | Inconclusive  | EXIT  | stale ENTER| Skip (fail-closed — TTL isteko)|
 *  | Inconclusive  | EXIT  | else     | Skip (fail-closed — phantom)     |
 *  | Inconclusive  | ENTER | ENTER    | Skip (repeated)                  |
 *  | Inconclusive  | ENTER | else     | Allow (ENTER se ne fail-close-uje)|
 */
object PhantomFilter {

    /**
     * Persist-ovan ENTER stariji od ovoga se tretira kao "nemamo dokaz ulaska" u
     * fail-closed logici za Inconclusive EXIT. Bug (Jul 2026, Jana): user je dobio
     * LEFT notif za place gde clan taj dan uopšte nije bio. Root cause: stari ENTER
     * (od pre nekoliko dana) je i dalje u prefs-u pa je Inconclusive EXIT "prošao
     * fail-closed" jer imamo prior ENTER, iako je taj ENTER hronološki nerelevantan.
     * 12h je izabran da pokrije normalan boravak preko noći (spavanje u placeu),
     * ali da odbaci stanje starije od jednog dana.
     */
    const val STALE_ENTER_TTL_MS = 12L * 60L * 60L * 1000L

    /**
     * Rezultat GPS verify koraka:
     *  - [Confirmed]: imamo fresh GPS + place info, distance izračunata → znamo da
     *    li je user fizički unutar ili van radius+PHANTOM_THRESHOLD_M threshold-a
     *  - [Inconclusive]: nešto je faili-lo (verifyLocation null zbog GPS timeout,
     *    placeInfo null zbog Firestore fetch fail) — ne znamo fizičko stanje
     */
    sealed class VerifyOutcome {
        data class Confirmed(val userInside: Boolean) : VerifyOutcome()
        object Inconclusive : VerifyOutcome()
    }

    sealed class Decision {
        data class Allow(val reason: String) : Decision()
        data class Skip(val reason: String) : Decision()
    }

    /**
     * Da li prihvatiti geofence transition kao legitiman event i upisati u Firestore.
     *
     * @param type novi transition type — `PlaceEventModel.TYPE_ENTER` ili `TYPE_EXIT`
     * @param prevType prethodni transition type za ovaj place iz `LocalPrefs`;
     *   null ako nikada nije upisan (fresh install, prvi event za ovo mesto)
     * @param prevTypeAgeMs koliko je stari `prevType` u milisekundama. Long.MAX_VALUE
     *   ako se ne zna (stari format bez timestamp-a) ili prevType je null. Koristi se
     *   za TTL check nad prior ENTER u fail-closed logici (bug: stari ENTER cuvao se
     *   danima pa je Inconclusive EXIT prosao kao „legitiman" iako nije bio).
     * @param verify GPS verify outcome — [VerifyOutcome.Confirmed] ili [VerifyOutcome.Inconclusive]
     */
    fun classify(
        type: String,
        prevType: String?,
        prevTypeAgeMs: Long = Long.MAX_VALUE,
        verify: VerifyOutcome,
    ): Decision {
        // Phantom check preko GPS verify-a (kad je moguć)
        when (verify) {
            is VerifyOutcome.Confirmed -> when (type) {
                PlaceEventModel.TYPE_EXIT ->
                    if (verify.userInside) return Decision.Skip("phantom EXIT: user still inside")
                PlaceEventModel.TYPE_ENTER ->
                    if (!verify.userInside) return Decision.Skip("phantom ENTER: user still outside")
            }
            VerifyOutcome.Inconclusive -> {
                // Fail-closed za EXIT: kad ne možemo da verifikujemo GPS-om, jedini
                // signal da je EXIT legitiman je fresh prior persisted ENTER. Bez toga,
                // 99% šansi da je Play Services „reconciled" fantomski event.
                if (type == PlaceEventModel.TYPE_EXIT) {
                    val hasFreshPriorEnter = prevType == PlaceEventModel.TYPE_ENTER &&
                        prevTypeAgeMs <= STALE_ENTER_TTL_MS
                    if (!hasFreshPriorEnter) {
                        val reason = when {
                            prevType != PlaceEventModel.TYPE_ENTER ->
                                "phantom EXIT (inconclusive verify, no prior ENTER)"
                            else ->
                                "phantom EXIT (inconclusive verify, stale ENTER ${prevTypeAgeMs}ms old)"
                        }
                        return Decision.Skip(reason)
                    }
                }
            }
        }
        // Semantički guard: ne dopusti dva uzastopna ENTER-a (ili EXIT-a) za isti place.
        // Hvata phantom koji je prošao distance filter (npr. GPS drift > radius+100m u
        // kratkom periodu, pa sledeći „inverse" event iz iste faze bude blokiran).
        if (prevType == type) {
            return Decision.Skip("repeated $type without opposite transition")
        }
        return Decision.Allow("passes phantom + semantic guards")
    }
}
