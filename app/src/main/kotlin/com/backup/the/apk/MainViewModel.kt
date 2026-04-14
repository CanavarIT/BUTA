package com.backup.the.apk

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiState(
    val apps: List<AppInfo> = emptyList(),
    val isLoading: Boolean = true,
    val selectedApps: Set<String> = emptySet(),
    val isExtracting: Boolean = false,
    val extractProgress: Int = 0,
    val extractMessage: String = "",
    val savedFolderUri: Uri? = null,
    val isRootAvailable: Boolean = false,
    val searchQuery: String = "",
    val filteredApps: List<AppInfo> = emptyList()
)

class MainViewModel(
    private val appScanner: AppScanner,
    private val apkExtractor: ApkExtractor
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    init {
        loadApps()
        checkRoot()
    }
    
    private fun checkRoot() {
        _uiState.value = _uiState.value.copy(
            isRootAvailable = apkExtractor.isRootAvailable()
        )
    }
    
    fun loadApps() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val apps = appScanner.getInstalledApps()
            _uiState.value = _uiState.value.copy(
                apps = apps,
                filteredApps = apps,
                isLoading = false
            )
        }
    }
    
    fun toggleAppSelection(packageName: String) {
        val currentSelected = _uiState.value.selectedApps.toMutableSet()
        if (currentSelected.contains(packageName)) {
            currentSelected.remove(packageName)
        } else {
            currentSelected.add(packageName)
        }
        _uiState.value = _uiState.value.copy(selectedApps = currentSelected)
    }
    
    fun selectAll() {
        val allPackages = _uiState.value.filteredApps.map { it.packageName }.toSet()
        _uiState.value = _uiState.value.copy(selectedApps = allPackages)
    }
    
    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedApps = emptySet())
    }
    
    fun setSavedFolderUri(uri: Uri) {
        _uiState.value = _uiState.value.copy(savedFolderUri = uri)
    }
    
    fun updateSearchQuery(query: String) {
        val filtered = if (query.isEmpty()) {
            _uiState.value.apps
        } else {
            _uiState.value.apps.filter {
                it.appName.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
            }
        }
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            filteredApps = filtered
        )
    }
    
    fun extractSelected() {
        val selectedAppsList = _uiState.value.apps.filter { 
            _uiState.value.selectedApps.contains(it.packageName) 
        }
        val folderUri = _uiState.value.savedFolderUri
        
        if (selectedAppsList.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                extractMessage = "Выберите приложения"
            )
            return
        }
        
        if (folderUri == null) {
            _uiState.value = _uiState.value.copy(
                extractMessage = "Выберите папку для сохранения"
            )
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isExtracting = true,
                extractMessage = "Подготовка..."
            )
            
            val (successCount, failCount) = apkExtractor.extractMultipleApks(
                apps = selectedAppsList,
                destinationUri = folderUri,
                onProgress = { progress ->
                    _uiState.value = _uiState.value.copy(
                        extractProgress = progress.percent,
                        extractMessage = "Извлечение... ${progress.percent}%"
                    )
                },
                onAppComplete = { appName, isSuccess ->
                    val status = if (isSuccess) "✓" else "✗"
                    _uiState.value = _uiState.value.copy(
                        extractMessage = "$status $appName"
                    )
                }
            )
            
            _uiState.value = _uiState.value.copy(
                isExtracting = false,
                extractProgress = 0,
                extractMessage = "Готово! Успешно: $successCount, Ошибок: $failCount",
                selectedApps = emptySet()
            )
        }
    }
    
    fun clearMessage() {
        _uiState.value = _uiState.value.copy(extractMessage = "")
    }
}