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
            val icon = pm.getApplicationIcon(appInfo)
            val sourceDir = appInfo.sourceDir
            
            // ИСПРАВЛЕНО: используем appInfo вместо pkg.applicationInfo
            val isSplitRequired = appInfo.metaData?.getBoolean(
                "com.android.vending.splits.required", false
            ) ?: false
            
            val techStack = detectTechStack(appInfo)
            
            apps.add(
                AppInfo(
                    packageName = packageName,
                    appName = appName,
                    versionName = versionName,
                    icon = icon,
                    sourceDir = sourceDir,
                    isSplitRequired = isSplitRequired,
                    techStack = techStack
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
        
        if (File(libDir, "libflutter.so").exists()) {
            stacks.add("Flutter")
        }
        
        val assetsDir = File(sourceDir.parent, "assets")
        if (File(assetsDir, "index.android.bundle").exists()) {
            stacks.add("React Native")
        }
        
        if (File(libDir, "libunity.so").exists()) {
            stacks.add("Unity")
        }
        
        return stacks
    }
}