package org.krug.app.core.splash

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Global gate koji kontroliše Android 12+ sistemski splash screen.
 * MainActivity poziva `installSplashScreen().setKeepOnScreenCondition { !SplashGate.ready.get() }`
 * pa sistemski splash ostaje vidljiv dok `SplashViewModel.decide()` ne završi.
 *
 * Eliminiše "double splash" jump (sistemski splash logo → Compose splash logo razlčite veličine).
 */
object SplashGate {
    val ready = AtomicBoolean(false)
}
