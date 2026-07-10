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
 *  | Inconclusive  | EXIT  | ENTER    | Allow (imamo prior ENTER)        |
 *  | Inconclusive  | EXIT  | else     | Skip (fail-closed — phantom)     |
 *  | Inconclusive  | ENTER | ENTER    | Skip (repeated)                  |
 *  | Inconclusive  | ENTER | else     | Allow (ENTER se ne fail-close-uje)|
 */
object PhantomFilter {

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
     * @param verify GPS verify outcome — [VerifyOutcome.Confirmed] ili [VerifyOutcome.Inconclusive]
     */
    fun classify(
        type: String,
        prevType: String?,
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
                // signal da je EXIT legitiman je prior persisted ENTER. Bez toga,
                // 99% šansi da je Play Services „reconciled" fantomski event.
                if (type == PlaceEventModel.TYPE_EXIT && prevType != PlaceEventModel.TYPE_ENTER) {
                    return Decision.Skip("phantom EXIT (inconclusive verify, no prior ENTER)")
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
