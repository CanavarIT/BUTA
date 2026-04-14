package com.backup.the.apk

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object FileUtils {
    
    fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
            size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024))
            else -> String.format("%.2f GB", size / (1024.0 * 1024 * 1024))
        }
    }
    
    fun formatTimestamp(timestamp: Long): String {
        if (timestamp == 0L) return "Неизвестно"
        val date = Date(timestamp)
        val format = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        return format.format(date)
    }
    
    fun shareApk(context: Context, sourcePath: String, appName: String) {
        try {
            val sourceFile = File(sourcePath)
            if (!sourceFile.exists()) {
                Toast.makeText(context, "Файл не найден", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Копируем в кэш для шаринга
            val safeName = appName.replace(" ", "_").replace("/", "_")
            val cacheFile = File(context.cacheDir, "${safeName}_share.apk")
            
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cacheFile
            )
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            context.startActivity(Intent.createChooser(shareIntent, "Поделиться APK"))
        } catch (e: Exception) {
            Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }
    
    fun exportAppsList(context: Context, apps: List<AppInfo>): Uri? {
        val fileName = "apps_list_${System.currentTimeMillis()}.txt"
        val file = File(context.cacheDir, fileName)
        
        return try {
            file.writeText(buildAppsListText(apps))
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun buildAppsListText(apps: List<AppInfo>): String {
        val sb = StringBuilder()
        sb.appendLine("=== BackUp The Apk - Список приложений (${apps.size}) ===")
        sb.appendLine("Дата экспорта: ${formatTimestamp(System.currentTimeMillis())}")
        sb.appendLine()
        
        val grouped = apps.groupBy { it.appType }
        grouped.forEach { (type, typeApps) ->
            sb.appendLine("--- $type (${typeApps.size}) ---")
            typeApps.sortedBy { it.appName }.forEach { app ->
                sb.appendLine("✓ ${app.appName}")
                sb.appendLine("  Пакет: ${app.packageName}")
                sb.appendLine("  Версия: ${app.versionName}")
                sb.appendLine("  Размер: ${formatFileSize(app.apkSize)}")
                sb.appendLine("  Источник: ${app.installSource}")
                sb.appendLine()
            }
        }
        
        return sb.toString()
    }
    
    fun shareExportedList(context: Context, uri: Uri) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Экспорт списка"))
        } catch (e: Exception) {
            Toast.makeText(context, "Ошибка экспорта", Toast.LENGTH_SHORT).show()
        }
    }
}