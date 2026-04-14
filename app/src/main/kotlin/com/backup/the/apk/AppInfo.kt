package com.backup.the.apk

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val icon: Drawable,
    val sourceDir: String,
    val isSplitRequired: Boolean,
    val techStack: List<String>
)