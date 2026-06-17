package org.krug.app.core.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth
import timber.log.Timber

/**
 * Restart FGS posle restart-a telefona — bez ovog korisnik bi morao da otvori app ručno
 * da bi tracking počeo. Aktivira se i pri update-u app-a (MY_PACKAGE_REPLACED).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val user = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()
        if (user == null) {
            Timber.d("BootReceiver: no signed-in user, skipping FGS start")
            return
        }
        Timber.d("BootReceiver: starting LocationTrackingService after $action")
        runCatching { LocationTrackingService.start(context) }
            .onFailure { Timber.w(it, "BootReceiver: failed to start FGS") }
    }
}
