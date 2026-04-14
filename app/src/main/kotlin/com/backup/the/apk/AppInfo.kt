package com.backup.the.apk

import android.graphics.drawable.Drawable

enum class AppType {
    SYSTEM, UPDATED_SYSTEM, USER
}

enum class InstallSource {
    GOOGLE_PLAY, SAMSUNG_STORE, HUAWEI_STORE, XIAOMI_STORE,
    AMAZON_STORE, SIDELOAD, SYSTEM, UNKNOWN
}

enum class SortOrder {
    NAME_ASC, NAME_DESC, SIZE_ASC, SIZE_DESC, DATE_ASC, DATE_DESC
}

data class AppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val icon: Drawable,
    val sourceDir: String,
    val apkSize: Long,
    val installTime: Long,
    val updateTime: Long,
    val targetSdk: Int,
    val minSdk: Int,
    val isSplitRequired: Boolean,
    val hasLaunchIntent: Boolean,
    val techStack: List<String>,
    val appType: AppType,
    val installSource: InstallSource,
    val installerPackage: String?,
    val isFavorite: Boolean = false
)