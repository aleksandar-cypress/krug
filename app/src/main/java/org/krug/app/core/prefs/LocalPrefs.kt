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
    private val prefs = context.getSharedPreferences("krug_prefs", Context.MODE_PRIVATE)

    var onboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit(commit = true) { putBoolean(KEY_ONBOARDING_DONE, value) }

    private val _activeCircleId = MutableStateFlow(prefs.getString(KEY_ACTIVE_CIRCLE, null))

    /** Reactive — UI observe-uje, fallback na prvi krug ako null/nevažeći. */
    val activeCircleIdFlow: StateFlow<String?> = _activeCircleId.asStateFlow()

    fun setActiveCircleId(id: String?) {
        prefs.edit(commit = true) {
            if (id == null) remove(KEY_ACTIVE_CIRCLE) else putString(KEY_ACTIVE_CIRCLE, id)
        }
        _activeCircleId.value = id
    }

    private companion object {
        const val KEY_ONBOARDING_DONE = "onboarding_completed"
        const val KEY_ACTIVE_CIRCLE = "active_circle_id"
    }
}
