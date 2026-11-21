package com.example.domain

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.example.data.local.ReplySupportStatus
import com.example.data.local.SupportedAppDao
import com.example.data.local.SupportedAppEntity
import com.example.service.SmartReplyNotificationService

class AppPackageInspector(private val context: Context) {

    fun isNotificationListenerEnabled(): Boolean {
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
        if (enabledPackages.contains(context.packageName)) return true

        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        val cn = ComponentName(context, SmartReplyNotificationService::class.java)
        return flat.contains(cn.flattenToString()) || flat.contains(context.packageName)
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun isPackageInstalled(packageName: String): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        }.getOrDefault(false)
    }

    fun getAppLabel(packageName: String, fallback: String): String {
        return runCatching {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(packageName, 0)
            }
            context.packageManager.getApplicationLabel(appInfo).toString().ifBlank { fallback }
        }.getOrDefault(fallback)
    }

    fun loadAppIcon(packageName: String): Drawable? {
        return runCatching {
            context.packageManager.getApplicationIcon(packageName)
        }.getOrNull()
    }

    fun getInstalledKnownPackages(): Set<String> {
        val known = mutableSetOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.facebook.orca",
            "com.facebook.mlite",
            "com.imo.android.imoim",
            "org.telegram.messenger",
            "org.telegram.messenger.web",
            "com.instagram.android",
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.samsung.android.messaging",
            "org.thoughtcrime.securesms",
            "com.discord",
            "com.viber.voip",
            "com.Slack"
        )
        val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
        val smsReceivers = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.queryIntentActivities(
                    smsIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.queryIntentActivities(smsIntent, PackageManager.MATCH_DEFAULT_ONLY)
            }
        }.getOrDefault(emptyList())
        smsReceivers.forEach { info ->
            info.activityInfo?.packageName?.let { known.add(it) }
        }
        return known.filter { isPackageInstalled(it) }.toSet()
    }

    suspend fun syncInstalledApps(supportedAppDao: SupportedAppDao) {
        val existing = supportedAppDao.getAllOnce().associateBy { it.packageName }
        val installedNow = getInstalledKnownPackages()

        for (app in existing.values) {
            val installed = isPackageInstalled(app.packageName)
            if (installed != app.isInstalled) {
                supportedAppDao.upsert(app.copy(isInstalled = installed))
            }
        }

        // Register any newly discovered SMS / alternative messaging variants
        for (pkg in installedNow) {
            if (!existing.containsKey(pkg)) {
                supportedAppDao.upsert(
                    SupportedAppEntity(
                        packageName = pkg,
                        displayName = getAppLabel(pkg, pkg),
                        isEnabled = pkg.contains("messaging") || pkg.contains("mms"),
                        isInstalled = true,
                        replySupportStatus = ReplySupportStatus.UNKNOWN_UNTIL_MESSAGE,
                        categoryHint = if (pkg.contains("messaging") || pkg.contains("mms")) "SMS" else "Messaging"
                    )
                )
            }
        }
    }
}
