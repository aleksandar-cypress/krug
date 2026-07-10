package org.krug.app.core.places

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Testovi za `PhantomFilter.classify` — pure decision logic izvučena iz
 * `GeofenceBroadcastReceiver` za phantom EXIT fix (1.2.3, Jul 2026).
 *
 * Regresija-hvatanje: kritičan pattern je fail-closed za EXIT kad GPS verify
 * nije mogao da potvrdi lokaciju. Ako neko slučajno vrati stari „propusti bez
 * provere" ponašanje, ovi testovi padaju.
 */
class PhantomFilterTest {

    private val ENTER = PlaceEventModel.TYPE_ENTER
    private val EXIT = PlaceEventModel.TYPE_EXIT
    private val insideVerify = PhantomFilter.VerifyOutcome.Confirmed(userInside = true)
    private val outsideVerify = PhantomFilter.VerifyOutcome.Confirmed(userInside = false)
    private val inconclusiveVerify = PhantomFilter.VerifyOutcome.Inconclusive

    // === Confirmed verify: distance-based phantom detection ===

    @Test fun `EXIT sa user inside → Skip (phantom, user nije stvarno izasao)`() {
        val decision = PhantomFilter.classify(EXIT, prevType = ENTER, verify = insideVerify)
        assertThat(decision).isInstanceOf(PhantomFilter.Decision.Skip::class.java)
    }

    @Test fun `ENTER sa user outside → Skip (phantom, user nije stvarno ušao)`() {
        val decision = PhantomFilter.classify(ENTER, prevType = null, verify = outsideVerify)
        assertThat(decision).isInstanceOf(PhantomFilter.Decision.Skip::class.java)
    }

    @Test fun `EXIT sa user outside → Allow (legitiman izlazak)`() {
        val decision = PhantomFilter.classify(EXIT, prevType = ENTER, verify = outsideVerify)
        assertThat(decision).isInstanceOf(PhantomFilter.Decision.Allow::class.java)
    }

    @Test fun `ENTER sa user inside → Allow (legitiman ulazak)`() {
        val decision = PhantomFilter.classify(ENTER, prevType = EXIT, verify = insideVerify)
        assertThat(decision).isInstanceOf(PhantomFilter.Decision.Allow::class.java)
    }

    @Test fun `prvi ENTER (prevType null) sa user inside → Allow`() {
        val decision = PhantomFilter.classify(ENTER, prevType = null, verify = insideVerify)
        assertThat(decision).isInstanceOf(PhantomFilter.Decision.Allow::class.java)
    }

    @Test fun `prvi EXIT (prevType null) sa user outside → Allow`() {
        // Legitiman scenario: place je kreiran dok je user bio unutra, ENTER
        // nikad nije upisan (INITIAL_TRIGGER=0), sad user fizički izlazi.
        val decision = PhantomFilter.classify(EXIT, prevType = null, verify = outsideVerify)
        assertThat(decision).isInstanceOf(PhantomFilter.Decision.Allow::class.java)
    }

    // === Confirmed verify: semantic guard (repeated transitions) ===

    @Test fun `ENTER kada je prev vec ENTER → Skip (repeated)`() {
        val decision = PhantomFilter.classify(ENTER, prevType = ENTER, verify = insideVerify)
        assertThat(decision).isInstanceOf(PhantomFilter.Decision.Skip::class.java)
    }

    @Test fun `EXIT kada je prev vec EXIT → Skip (repeated)`() {
        val decision = PhantomFilter.classify(EXIT, prevType = EXIT, verify = outsideVerify)
        assertThat(decision).isInstanceOf(PhantomFilter.Decision.Skip::class.java)
    }

    // === Inconclusive verify: fail-closed za EXIT (Jelena bug fix) ===

    @Test fun `Inconclusive EXIT bez prior ENTER (prevType null) → Skip (fail-closed)`() {
        // Ovo je JELENA scenario: place gde nikad nije bila fizički, verify je
        // fail-uje (Doze wake), prefs nemaju prior ENTER — mora se skip-ovati.
        val decision = PhantomFilter.classify(EXIT, prevType = null, verify = inconclusiveVerify)
        assertThat(decision).isInstanceOf(PhantomFilter.Decision.Skip::class.java)
    }

    @Test fun `Inconclusive EXIT sa prior EXIT → Skip (fail-closed, no legit ENTER)`() {
        val decision = PhantomFilter.classify(EXIT, prevType = EXIT, verify = inconclusiveVerify)
        assertThat(decision).isInstanceOf(PhantomFilter.Decision.Skip::class.java)
    }

    @Test fun `Inconclusive EXIT sa prior ENTER → Allow (imamo dokaz ulaska)`() {
        // User je bio unutra (persisted ENTER), sad izlazi, GPS ne može da potvrdi
        // ali imamo prior state — puštamo.
        val decision = PhantomFilter.classify(EXIT, prevType = ENTER, verify = inconclusiveVerify)
        assertThat(decision).isInstanceOf(PhantomFilter.Decision.Allow::class.java)
    }

    // === Inconclusive verify: ENTER se ne fail-close-uje ===

    @Test fun `Inconclusive ENTER bez prior → Allow (miss real ENTER lošije od fail-close-a)`() {
        val decision = PhantomFilter.classify(ENTER, prevType = null, verify = inconclusiveVerify)
        assertThat(decision).isInstanceOf(PhantomFilter.Decision.Allow::class.java)
    }

    @Test fun `Inconclusive ENTER sa prior EXIT → Allow (user se vraća)`() {
        val decision = PhantomFilter.classify(ENTER, prevType = EXIT, verify = inconclusiveVerify)
        assertThat(decision).isInstanceOf(PhantomFilter.Decision.Allow::class.java)
    }

    @Test fun `Inconclusive ENTER sa prior ENTER → Skip (repeated, guard prvi)`() {
        val decision = PhantomFilter.classify(ENTER, prevType = ENTER, verify = inconclusiveVerify)
        assertThat(decision).isInstanceOf(PhantomFilter.Decision.Skip::class.java)
    }
}
