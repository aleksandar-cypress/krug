package org.krug.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import org.krug.app.core.sos.SosFocusBus
import org.krug.app.core.sos.SosNotifier
import org.krug.app.core.splash.SplashGate
import org.krug.app.navigation.KrugNavHost
import org.krug.app.ui.theme.KrugTheme

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface MainActivityEntryPoint {
    fun localPrefs(): org.krug.app.core.prefs.LocalPrefs
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Drži sistemski splash dok SplashViewModel ne završi `decide()`. Bez ovoga
        // user vidi sistemski splash logo pa Compose splash logo (različite veličine)
        // pa stvarni ekran — "double splash" jump.
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !SplashGate.ready.get() }
        // Override sistemskog "icon exit animation"-a (Android 12+) — bez ovog, splash icon
        // se automatski zoom-out-uje (raste i fade-uje) pre dismiss-a, što user vidi kao
        // "logo se pojavi veliki na sekund" pre Compose animacije. provider.remove() odmah
        // skloni splash bez animacije — Compose splash glatko preuzima.
        splashScreen.setOnExitAnimationListener { provider -> provider.remove() }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleSosFocusExtra(intent)
        handlePlaceFocusExtra(intent)
        handleInviteDeepLink(intent)
        setContent {
            KrugApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // launchMode=singleTask znači da drugi tap na SOS notif dok je app open ide
        // ovde umesto u onCreate. Refresh extra u Activity intent-u da bi sledeći
        // getIntent() call vraćao novi intent, pa proslediti u bus.
        setIntent(intent)
        handleSosFocusExtra(intent)
        handlePlaceFocusExtra(intent)
        handleInviteDeepLink(intent)
    }

    /**
     * Parsira `krug://invite/{code}` deep link i emituje kod u InviteFocusBus.
     * KrugNavHost/EnterCodeScreen collect-uje i prikazuje sa prefilled-code-om.
     *
     * Bez ovog: manifest ima intent-filter za scheme=krug, ali kod je ignoran →
     * user vidi splash → Auth → Map, ali code se gubi. Morao bi ručno da otvori
     * "Pridruži se krugu" → unese kod.
     */
    private fun handleInviteDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "krug" || uri.host != "invite") return
        val code = uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: return
        org.krug.app.core.circle.InviteFocusBus.request(code)
        // Odbaci data iz intent-a da rotacija/configChange ne re-trigger-uje.
        intent.data = null
    }

    private fun handlePlaceFocusExtra(intent: Intent?) {
        val placeId = intent?.getStringExtra(
            org.krug.app.core.places.PlaceEventNotifier.EXTRA_FOCUS_PLACE_ID,
        )?.takeIf { it.isNotBlank() } ?: return
        val circleId = intent.getStringExtra(
            org.krug.app.core.places.PlaceEventNotifier.EXTRA_FOCUS_CIRCLE_ID,
        )?.takeIf { it.isNotBlank() }
        if (circleId != null) {
            // Postavi aktivni krug (LocalPrefs) tako da MapScreen prikaže pin.
            // Injection u Activity — koristi EntryPointAccessors kao Auto session.
            val prefs = dagger.hilt.android.EntryPointAccessors.fromApplication(
                applicationContext,
                MainActivityEntryPoint::class.java,
            ).localPrefs()
            prefs.setActiveCircleId(circleId)
        }
        // Emit placeId u focus bus — MapScreen ga collect-uje, čeka da activePlaces
        // sadrži place, pa fly-to na tu koordinatu. Bez ovog, notif klik samo otvara
        // app i mapa ostaje gde je user prethodno bio (loše UX — pointless notification).
        org.krug.app.core.places.PlaceFocusBus.requestById(placeId)
        intent.removeExtra(org.krug.app.core.places.PlaceEventNotifier.EXTRA_FOCUS_PLACE_ID)
        intent.removeExtra(org.krug.app.core.places.PlaceEventNotifier.EXTRA_FOCUS_CIRCLE_ID)
    }

    private fun handleSosFocusExtra(intent: Intent?) {
        val uid = intent?.getStringExtra(SosNotifier.EXTRA_FOCUS_SOS_UID)
            ?.takeIf { it.isNotBlank() }
            ?: return
        // Pređi lock screen SAMO za SOS wake (full-screen notification). Bez ovog,
        // user koji primi SOS dok je telefon zaključan ne bi video mapu odmah, a SOS
        // je upravo use-case koji opravdava lock-screen bypass.
        enableShowWhenLocked()
        SosFocusBus.request(uid)
        // Skini extra da rotacija/configChange ne re-trigger-uje fokus.
        intent.removeExtra(SosNotifier.EXTRA_FOCUS_SOS_UID)
    }

    @Suppress("DEPRECATION")
    private fun enableShowWhenLocked() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }

    /**
     * Skida "show over lock screen" flag. Bez ovog, jednom kad je aktiviran za SOS
     * wake, Activity ostaje prikazana preko lock screen-a čak i kad user samo
     * običnо otvori app kasnije (npr. Places notif tap). Privacy leak: bilo ko sa
     * pristupom telefonu vidi mapu bez unlock-a. Reset se poziva onResume kad
     * pending SOS focus vise ne postoji — tj. user je već video/consumovao SOS.
     */
    @Suppress("DEPRECATION")
    private fun disableShowWhenLocked() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(false)
            setTurnScreenOn(false)
        } else {
            window.clearFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Reset lock-screen bypass ako smo ga jednom aktivirali za SOS wake. Reset radi
        // SAMO kad je SosFocusBus već consumovan (pending == null) — jer ako je pending
        // još u toku, MapScreen ga još nije video, pa flag mora ostati aktivan dok user
        // ne vidi SOS mapu. Bez ovog reset-a: aktivacija za SOS ostavlja flag zauvek
        // (privacy leak — svaka sledeca notif otvara app preko lock screen-a).
        if (SosFocusBus.pendingUid.value == null) {
            disableShowWhenLocked()
        }
    }
}

@Composable
fun KrugApp() {
    KrugTheme {
        Surface(
            modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
        ) {
            KrugNavHost()
        }
    }
}

@Preview
@Composable
private fun KrugAppPreview() {
    KrugApp()
}
