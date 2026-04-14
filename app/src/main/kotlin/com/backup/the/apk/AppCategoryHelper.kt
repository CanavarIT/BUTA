package com.backup.the.apk

import android.content.Context
import android.content.pm.ApplicationInfo

object AppCategoryHelper {
    
    fun getAppType(appInfo: ApplicationInfo): AppType {
        return when {
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 -> {
                if ((appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
                    AppType.UPDATED_SYSTEM
                } else {
                    AppType.SYSTEM
                }
            }
            else -> AppType.USER
        }
    }
    
    fun getInstallSource(context: Context, packageName: String): InstallSource {
        return try {
            val installer = context.packageManager.getInstallerPackageName(packageName)
            when (installer) {
                "com.android.vending" -> InstallSource.GOOGLE_PLAY
                "com.sec.android.app.samsungapps" -> InstallSource.SAMSUNG_STORE
                "com.huawei.appmarket" -> InstallSource.HUAWEI_STORE
                "com.xiaomi.market" -> InstallSource.XIAOMI_STORE
                "com.amazon.venezia" -> InstallSource.AMAZON_STORE
                "com.google.android.packageinstaller" -> InstallSource.SIDELOAD
                null -> InstallSource.SYSTEM
                else -> InstallSource.SIDELOAD
            }
        } catch (e: Exception) {
            InstallSource.UNKNOWN
        }
    }
    
    fun getInstallerPackage(context: Context, packageName: String): String? {
        return try {
            context.packageManager.getInstallerPackageName(packageName)
        } catch (e: Exception) {
            null
        }
    }
    
    fun hasLaunchIntent(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getLaunchIntentForPackage(packageName) != null
        } catch (e: Exception) {
            false
        }
    }
}