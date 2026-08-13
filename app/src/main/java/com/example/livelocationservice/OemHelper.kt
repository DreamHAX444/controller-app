package com.example.livelocationservice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/**
 * Deep-links into vendor-specific "auto-start" / "background launch" managers
 * so the user can whitelist this app. Each entry is tried in order; the first
 * one that resolves on the running device is launched.
 */
object OemHelper {

    data class LaunchEntry(
        val manufacturer: String,
        val component: ComponentName,
        val description: String,
    )

    private val entries: List<LaunchEntry> = listOf(
        // Xiaomi (MIUI)
        LaunchEntry(
            manufacturer = "Xiaomi",
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            ),
            description = "MIUI autostart manager"
        ),
        // Oppo (ColorOS)
        LaunchEntry(
            manufacturer = "Oppo",
            component = ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            ),
            description = "ColorOS startup manager"
        ),
        LaunchEntry(
            manufacturer = "Oppo",
            component = ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.startupapp.StartupAppListActivity"
            ),
            description = "ColorOS startup manager (alt)"
        ),
        LaunchEntry(
            manufacturer = "Oppo",
            component = ComponentName(
                "com.oppo.safe",
                "com.oppo.safe.permission.startup.StartupAppListActivity"
            ),
            description = "Oppo safe startup manager"
        ),
        // Vivo
        LaunchEntry(
            manufacturer = "Vivo",
            component = ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            ),
            description = "Vivo background startup manager"
        ),
        LaunchEntry(
            manufacturer = "Vivo",
            component = ComponentName(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
            ),
            description = "iQOO phone optimize"
        ),
        // Huawei (EMUI / HarmonyOS)
        LaunchEntry(
            manufacturer = "Huawei",
            component = ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            ),
            description = "Huawei startup manager"
        ),
        LaunchEntry(
            manufacturer = "Huawei",
            component = ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"
            ),
            description = "Huawei protected apps"
        ),
        // Samsung
        LaunchEntry(
            manufacturer = "Samsung",
            component = ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.ui.battery.BatteryActivity"
            ),
            description = "Samsung device maintenance"
        ),
        LaunchEntry(
            manufacturer = "Samsung",
            component = ComponentName(
                "com.samsung.android.sm_cn",
                "com.samsung.android.sm.ui.battery.BatteryActivity"
            ),
            description = "Samsung device maintenance (CN)"
        ),
        // OnePlus (OxygenOS)
        LaunchEntry(
            manufacturer = "OnePlus",
            component = ComponentName(
                "com.oneplus.security",
                "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
            ),
            description = "OnePlus chain launch"
        ),
        // Realme
        LaunchEntry(
            manufacturer = "Realme",
            component = ComponentName(
                "com.realme.securitycenter",
                "com.realme.securitycenter.autostart.AutoStartActivity"
            ),
            description = "Realme autostart"
        ),
        // Letv
        LaunchEntry(
            manufacturer = "Letv",
            component = ComponentName(
                "com.letv.android.letvsafe",
                "com.letv.android.letvsafe.AutobootManageActivity"
            ),
            description = "Letv autoboot"
        ),
        // Asus
        LaunchEntry(
            manufacturer = "Asus",
            component = ComponentName(
                "com.asus.mobilemanager",
                "com.asus.mobilemanager.entry.FunctionActivity"
            ),
            description = "Asus mobile manager"
        ),
        LaunchEntry(
            manufacturer = "Asus",
            component = ComponentName(
                "com.asus.mobilemanager",
                "com.asus.mobilemanager.autostart.AutoStartActivity"
            ),
            description = "Asus autostart"
        ),
    )

    /**
     * Try to launch the vendor autostart screen. Returns true if a matching
     * vendor screen was found and the intent was started.
     */
    fun openAutoStartManager(context: Context): Boolean {
        val pm = context.packageManager
        for (entry in entries) {
            val intent = Intent().setComponent(entry.component)
            val resolved = pm.resolveActivity(
                intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PackageManager.MATCH_DEFAULT_ONLY
                } else {
                    0
                }
            )
            if (resolved != null) {
                return try {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    true
                } catch (e: Exception) {
                    false
                }
            }
        }
        return false
    }

    /**
     * Returns the first matching vendor entry for the current device, or null
     * if no specific vendor ROM is detected.
     */
    fun detectVendor(): LaunchEntry? {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return entries.firstOrNull { manufacturer.contains(it.manufacturer.lowercase()) }
    }
}
