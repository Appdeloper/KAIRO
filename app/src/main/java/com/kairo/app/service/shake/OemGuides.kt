package com.kairo.app.service.shake

/** An activity in a vendor's settings app; many differ across ROM versions, so we try several. */
data class SettingsTarget(val packageName: String, val className: String)

enum class OemFamily { XIAOMI, VIVO, OPPO, ONEPLUS, SAMSUNG }

/**
 * Vendor battery/autostart screens. None of these are documented Android APIs: they come from
 * community research (dontkillmyapp.com) and change between ROM versions, so every target is
 * tried in order and the caller falls back to the app-info page.
 */
data class OemGuide(val family: OemFamily, val targets: List<SettingsTarget>)

object OemGuides {
    private val byManufacturer = mapOf(
        "xiaomi" to OemFamily.XIAOMI, "redmi" to OemFamily.XIAOMI, "poco" to OemFamily.XIAOMI,
        "vivo" to OemFamily.VIVO, "iqoo" to OemFamily.VIVO,
        "oppo" to OemFamily.OPPO, "realme" to OemFamily.OPPO,
        "oneplus" to OemFamily.ONEPLUS,
        "samsung" to OemFamily.SAMSUNG,
    )

    private val targets = mapOf(
        OemFamily.XIAOMI to listOf(
            SettingsTarget("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            SettingsTarget("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"),
        ),
        OemFamily.VIVO to listOf(
            SettingsTarget("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            SettingsTarget("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
        ),
        OemFamily.OPPO to listOf(
            SettingsTarget("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            SettingsTarget("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            SettingsTarget("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        ),
        OemFamily.ONEPLUS to listOf(
            SettingsTarget("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
        ),
        OemFamily.SAMSUNG to listOf(
            SettingsTarget("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
            SettingsTarget("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"),
        ),
    )

    /** Matches Build.MANUFACTURER (and BRAND, which is "redmi"/"poco"/"iqoo" on sub-brands). */
    fun forDevice(manufacturer: String, brand: String = ""): OemGuide? {
        val family = byManufacturer[manufacturer.trim().lowercase()] ?: byManufacturer[brand.trim().lowercase()] ?: return null
        return OemGuide(family, targets.getValue(family))
    }
}
