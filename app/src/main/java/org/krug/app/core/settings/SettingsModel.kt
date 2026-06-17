package org.krug.app.core.settings

enum class BatteryMode {
    /** Uvek LOW profil — najmanje grejanja, ali sporiji updates. */
    SAVER,
    /** Default — LOW kao baseline. SOS/refresh ping povlači sveži fix on-demand. */
    BALANCED,
    /** Uvek HIGH profil — najbrži updates, ali grejanje + drain. */
    MAX,
}

data class UserSettings(
    val batteryMode: BatteryMode = BatteryMode.BALANCED,
    val shareLocationGlobal: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val language: String = "sr",
)
