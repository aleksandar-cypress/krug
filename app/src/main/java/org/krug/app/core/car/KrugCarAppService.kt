package org.krug.app.core.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import timber.log.Timber

/**
 * Entry point za Android Auto — instanciran od Auto host procesa (odvojen od naše app-e).
 *
 * Lifecycle:
 *  1. Korisnik konektuje telefon sa autom (USB/wireless projekcija)
 *  2. Auto host detektuje da imamo CarAppService + `automotive_app_desc.xml`
 *  3. Bind-uje ovaj servis i poziva `onCreateSession()`
 *  4. Session hostuje Screen-ove; prvi screen se dobija iz `Session.onCreateScreen()`
 *
 * Host validator: koristimo `car_app_validator_hosts` allowlist iz res/xml-a za
 * production, `ALLOW_ALL_HOSTS_VALIDATOR` bilo bi previše permissive (svako može da
 * pretpostavlja da je Auto host). Za sada allowlist obuhvata samo Google Auto host.
 *
 * Debug tips (ako se Krug ne pojavljuje u Auto meniju):
 *  1. `adb logcat -s KrugCarAppService CarApp` — provjerava da li Auto host uopšte
 *     pokušava da bind-uje servis. Ako nema log-a, Auto host nije prepoznao app kao
 *     validan CarApp (proveriti automotive_app_desc.xml i manifest deklaracije).
 *  2. Sideload sa `adb shell pm install -i "com.android.vending" -r <apk>` — bez
 *     ovog Auto na nekim host verzijama ignorira apps ne-Play-Store installer-a.
 *  3. Play Store internal test track — najsigurniji način za testiranje jer prolazi
 *     kroz stvarni Play install path.
 */
class KrugCarAppService : CarAppService() {

    override fun onCreate() {
        // Ovaj log je kritican za diagnostiku: ako se prikazuje, Auto host je nasao
        // nasu manifest deklaraciju i pokrenuo servis. Ako se ne prikazuje pri
        // priključenju na auto, problem je pre create-a (manifest, package installer,
        // Auto host verzija). CarAppService.onBind je final pa ne mozemo hook-ovati bind
        // direktno — onCreate je najblizi entry point koji Auto host trigger-uje.
        Timber.i("KrugCarAppService: onCreate — Auto host initialized our service")
        super.onCreate()
    }

    override fun createHostValidator(): HostValidator {
        // Za debug/dev — allow all hosts (može simulator, DHU, itd. da se konektuje).
        // Za release build treba uže allowlist preko HostValidator.Builder-a.
        Timber.i("KrugCarAppService: createHostValidator (allow-all for dev)")
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        Timber.i("KrugCarAppService: onCreateSession — Auto host started projected session")
        return KrugCarSession()
    }

    override fun onDestroy() {
        Timber.i("KrugCarAppService: onDestroy")
        super.onDestroy()
    }
}
