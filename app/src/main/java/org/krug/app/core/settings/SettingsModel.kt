package org.krug.app.core.settings

enum class BatteryMode { CONSTANT, ADAPTIVE, HYBRID }

data class UserSettings(
    val batteryMode: BatteryMode = BatteryMode.HYBRID,
    val hybridThresholdPct: Int = 15,
    val shareLocationGlobal: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val language: String = "sr",
) {
    companion object {
        const val MIN_THRESHOLD = 5
        const val MAX_THRESHOLD = 40
    }
}
