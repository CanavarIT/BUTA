package com.backup.the.apk

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AppScanner(private val context: Context) {
    
    suspend fun getInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
        val apps = mutableListOf<AppInfo>()
        
        for (pkg in packages) {
            val appInfo = pkg.applicationInfo ?: continue
            
            val packageName = pkg.packageName
            val appName = pm.getApplicationLabel(appInfo).toString()
            val versionName = pkg.versionName ?: "Unknown"
            val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pkg.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkg.versionCode.toLong()
            }
            val icon = pm.getApplicationIcon(appInfo)
            val sourceDir = appInfo.sourceDir ?: ""
            val apkSize = File(sourceDir).length()
            val installTime = try {
                pm.getPackageInfo(packageName, 0).firstInstallTime
            } catch (e: Exception) {
                0L
            }
            val updateTime = try {
                pm.getPackageInfo(packageName, 0).lastUpdateTime
            } catch (e: Exception) {
                0L
            }
            val targetSdk = appInfo.targetSdkVersion
            val minSdk = appInfo.minSdkVersion
            
            val isSplitRequired = appInfo.metaData?.getBoolean(
                "com.android.vending.splits.required", false
            ) ?: false
            
            val hasLaunchIntent = AppCategoryHelper.hasLaunchIntent(context, packageName)
            val techStack = detectTechStack(appInfo)
            val appType = AppCategoryHelper.getAppType(appInfo)
            val installSource = AppCategoryHelper.getInstallSource(context, packageName)
            val installerPackage = AppCategoryHelper.getInstallerPackage(context, packageName)
            
            apps.add(
                AppInfo(
                    packageName = packageName,
                    appName = appName,
                    versionName = versionName,
                    versionCode = versionCode,
                    icon = icon,
                    sourceDir = sourceDir,
                    apkSize = apkSize,
                    installTime = installTime,
                    updateTime = updateTime,
                    targetSdk = targetSdk,
                    minSdk = minSdk,
                    isSplitRequired = isSplitRequired,
                    hasLaunchIntent = hasLaunchIntent,
                    techStack = techStack,
                    appType = appType,
                    installSource = installSource,
                    installerPackage = installerPackage
                )
            )
        }
        
        apps.sortBy { it.appName.lowercase() }
        return@withContext apps
    }
    
    private fun detectTechStack(appInfo: android.content.pm.ApplicationInfo): List<String> {
        val stacks = mutableListOf<String>()
        val sourceDir = File(appInfo.sourceDir)
        val libDir = File(appInfo.nativeLibraryDir)
        
        if (File(libDir, "libflutter.so").exists()) stacks.add("Flutter")
        if (File(File(sourceDir.parent, "assets"), "index.android.bundle").exists()) stacks.add("React Native")
        if (File(libDir, "libunity.so").exists()) stacks.add("Unity")
        if (File(libDir, "libkotlinx-coroutines-core.so").exists()) stacks.add("KMM")
        if (File(libDir, "libmonodroid.so").exists()) stacks.add("Xamarin")
        
        return stacks
    }
}