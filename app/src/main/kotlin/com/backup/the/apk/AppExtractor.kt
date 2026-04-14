package com.backup.the.apk

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed class ExtractResult {
    data class Success(val fileUri: Uri, val fileName: String) : ExtractResult()
    data class Error(val message: String) : ExtractResult()
    data class Progress(val percent: Int) : ExtractResult()
}

class ApkExtractor(private val context: Context) {
    
    suspend fun extractApk(
        appInfo: AppInfo,
        destinationUri: Uri,
        onProgress: (ExtractResult.Progress) -> Unit
    ): ExtractResult = withContext(Dispatchers.IO) {
        
        try {
            val sourceFile = File(appInfo.sourceDir)
            if (!sourceFile.exists()) {
                return@withContext ExtractResult.Error("Исходный APK не найден")
            }
            
            val fileName = "${appInfo.appName.replace(" ", "_")}_${appInfo.versionName}.apk"
                .replace("/", "_")
                .replace(":", "_")
            
            val hasRoot = Shell.getShell().isRoot
            
            if (hasRoot) {
                return@withContext extractWithRoot(sourceFile, destinationUri, fileName, onProgress)
            } else {
                return@withContext extractWithoutRoot(sourceFile, destinationUri, fileName, onProgress)
            }
            
        } catch (e: Exception) {
            return@withContext ExtractResult.Error("Ошибка: ${e.message}")
        }
    }
    
    private fun extractWithRoot(
        sourceFile: File,
        destinationUri: Uri,
        fileName: String,
        onProgress: (ExtractResult.Progress) -> Unit
    ): ExtractResult {
        val docFile = DocumentFile.fromTreeUri(context, destinationUri) 
            ?: return ExtractResult.Error("Не удалось получить доступ к папке")
        
        val cacheFile = File(context.cacheDir, fileName)
        
        val result = Shell.cmd("cp ${sourceFile.absolutePath} ${cacheFile.absolutePath}").exec()
        
        if (!result.isSuccess) {
            return ExtractResult.Error("Root-копирование не удалось")
        }
        
        onProgress(ExtractResult.Progress(100))
        
        val createdFile = docFile.createFile("application/vnd.android.package-archive", fileName)
            ?: return ExtractResult.Error("Не удалось создать файл")
        
        context.contentResolver.openOutputStream(createdFile.uri)?.use { output ->
            cacheFile.inputStream().use { input ->
                input.copyTo(output)
            }
        }
        
        cacheFile.delete()
        
        return ExtractResult.Success(createdFile.uri, fileName)
    }
    
    private fun extractWithoutRoot(
        sourceFile: File,
        destinationUri: Uri,
        fileName: String,
        onProgress: (ExtractResult.Progress) -> Unit
    ): ExtractResult {
        val docFile = DocumentFile.fromTreeUri(context, destinationUri) 
            ?: return ExtractResult.Error("Не удалось получить доступ к папке")
        
        val createdFile = docFile.createFile("application/vnd.android.package-archive", fileName)
            ?: return ExtractResult.Error("Не удалось создать файл")
        
        context.contentResolver.openOutputStream(createdFile.uri)?.use { output ->
            sourceFile.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalCopied = 0L
                val totalSize = sourceFile.length()
                
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalCopied += bytesRead
                    val progress = ((totalCopied * 100) / totalSize).toInt()
                    onProgress(ExtractResult.Progress(progress))
                }
            }
        }
        
        return ExtractResult.Success(createdFile.uri, fileName)
    }
    
    suspend fun extractMultipleApks(
        apps: List<AppInfo>,
        destinationUri: Uri,
        onProgress: (ExtractResult.Progress) -> Unit,
        onAppComplete: (String, Boolean) -> Unit
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        var successCount = 0
        var failCount = 0
        
        apps.forEach { app ->
            val result = extractApk(app, destinationUri) { progress ->
                onProgress(progress)
            }
            
            when (result) {
                is ExtractResult.Success -> {
                    successCount++
                    onAppComplete(app.appName, true)
                }
                is ExtractResult.Error -> {
                    failCount++
                    onAppComplete(app.appName, false)
                }
                else -> {}
            }
        }
        
        return@withContext Pair(successCount, failCount)
    }
    
    fun isRootAvailable(): Boolean {
        return try {
            Shell.getShell().isRoot
        } catch (e: Exception) {
            false
        }
    }
}