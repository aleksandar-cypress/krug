package org.krug.app.core.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import org.krug.app.R
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
 */
class KrugCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        // Za debug/dev — allow all hosts (može simulator, DHU, itd. da se konektuje).
        // Za release build treba uže allowlist preko HostValidator.Builder-a.
        Timber.d("KrugCarAppService: createHostValidator (allow-all for dev)")
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        Timber.i("KrugCarAppService: onCreateSession")
        return KrugCarSession()
    }
}
