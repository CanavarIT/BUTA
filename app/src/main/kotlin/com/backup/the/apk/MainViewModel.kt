package com.backup.the.apk

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiState(
    val apps: List<AppInfo> = emptyList(),
    val filteredApps: List<AppInfo> = emptyList(),
    val isLoading: Boolean = true,
    val selectedApps: Set<String> = emptySet(),
    val isExtracting: Boolean = false,
    val extractProgress: Int = 0,
    val extractMessage: String = "",
    val savedFolderUri: Uri? = null,
    val isRootAvailable: Boolean = false,
    val searchQuery: String = "",
    val selectedTab: Int = 0,
    val showOnlySplit: Boolean = false,
    val showOnlyWithLaunch: Boolean = false,
    val showOnlyGooglePlay: Boolean = false,
    val sortOrder: SortOrder = SortOrder.NAME_ASC,
    val favorites: Set<String> = emptySet(),
    val isDarkTheme: Boolean = false,
    val currentLanguage: String = "ru"
)

class MainViewModel(
    private val appScanner: AppScanner,
    private val apkExtractor: ApkExtractor,
    private val context: Context
) : ViewModel() {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("buta_prefs", Context.MODE_PRIVATE)
    
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    init {
        loadFavorites()
        loadTheme()
        loadLanguage()
        loadApps()
        checkRoot()
    }
    
    private fun loadFavorites() {
        val favs = prefs.getStringSet("favorites", emptySet()) ?: emptySet()
        _uiState.value = _uiState.value.copy(favorites = favs)
    }
    
    private fun saveFavorites() {
        prefs.edit().putStringSet("favorites", _uiState.value.favorites).apply()
    }
    
    private fun loadTheme() {
        val darkTheme = prefs.getBoolean("dark_theme", false)
        _uiState.value = _uiState.value.copy(isDarkTheme = darkTheme)
    }
    
    private fun loadLanguage() {
        val lang = prefs.getString("language", "ru") ?: "ru"
        _uiState.value = _uiState.value.copy(currentLanguage = lang)
    }
    
    fun setLanguage(language: String) {
        _uiState.value = _uiState.value.copy(currentLanguage = language)
        prefs.edit().putString("language", language).apply()
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
            val favs = _uiState.value.favorites
            val appsWithFavs = apps.map { app ->
                app.copy(isFavorite = favs.contains(app.packageName))
            }
            _uiState.value = _uiState.value.copy(
                apps = appsWithFavs,
                isLoading = false
            )
            applyFilters()
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
    
    fun invertSelection() {
        val current = _uiState.value.selectedApps
        val all = _uiState.value.filteredApps.map { it.packageName }.toSet()
        val inverted = all - current
        _uiState.value = _uiState.value.copy(selectedApps = inverted)
    }
    
    fun setSavedFolderUri(uri: Uri) {
        _uiState.value = _uiState.value.copy(savedFolderUri = uri)
        prefs.edit().putString("saved_folder_uri", uri.toString()).apply()
    }
    
    fun loadSavedFolderUri(): Uri? {
        val uriString = prefs.getString("saved_folder_uri", null)
        return uriString?.let { Uri.parse(it) }
    }
    
    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        applyFilters()
    }
    
    fun setSelectedTab(tab: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
        applyFilters()
    }
    
    fun toggleSplitFilter() {
        _uiState.value = _uiState.value.copy(showOnlySplit = !_uiState.value.showOnlySplit)
        applyFilters()
    }
    
    fun toggleLaunchFilter() {
        _uiState.value = _uiState.value.copy(showOnlyWithLaunch = !_uiState.value.showOnlyWithLaunch)
        applyFilters()
    }
    
    fun toggleGooglePlayFilter() {
        _uiState.value = _uiState.value.copy(showOnlyGooglePlay = !_uiState.value.showOnlyGooglePlay)
        applyFilters()
    }
    
    fun setSortOrder(order: SortOrder) {
        _uiState.value = _uiState.value.copy(sortOrder = order)
        applyFilters()
    }
    
    fun toggleFavorite(packageName: String) {
        val currentFavorites = _uiState.value.favorites.toMutableSet()
        if (currentFavorites.contains(packageName)) {
            currentFavorites.remove(packageName)
        } else {
            currentFavorites.add(packageName)
        }
        _uiState.value = _uiState.value.copy(favorites = currentFavorites)
        saveFavorites()
        
        val updatedApps = _uiState.value.apps.map { app ->
            if (app.packageName == packageName) {
                app.copy(isFavorite = currentFavorites.contains(packageName))
            } else {
                app
            }
        }
        _uiState.value = _uiState.value.copy(apps = updatedApps)
        applyFilters()
    }
    
    fun toggleTheme() {
        val newTheme = !_uiState.value.isDarkTheme
        _uiState.value = _uiState.value.copy(isDarkTheme = newTheme)
        prefs.edit().putBoolean("dark_theme", newTheme).apply()
    }
    
    private fun applyFilters() {
        val state = _uiState.value
        var filtered = state.apps
        
        filtered = when (state.selectedTab) {
            0 -> filtered
            1 -> filtered.filter { it.appType == AppType.USER }
            2 -> filtered.filter { it.appType == AppType.SYSTEM || it.appType == AppType.UPDATED_SYSTEM }
            3 -> filtered.filter { it.isFavorite }
            else -> filtered
        }
        
        if (state.searchQuery.isNotEmpty()) {
            filtered = filtered.filter {
                it.appName.contains(state.searchQuery, ignoreCase = true) ||
                it.packageName.contains(state.searchQuery, ignoreCase = true)
            }
        }
        
        if (state.showOnlySplit) {
            filtered = filtered.filter { it.isSplitRequired }
        }
        
        if (state.showOnlyWithLaunch) {
            filtered = filtered.filter { it.hasLaunchIntent }
        }
        
        if (state.showOnlyGooglePlay) {
            filtered = filtered.filter { it.installSource == InstallSource.GOOGLE_PLAY }
        }
        
        filtered = when (state.sortOrder) {
            SortOrder.NAME_ASC -> filtered.sortedBy { it.appName.lowercase() }
            SortOrder.NAME_DESC -> filtered.sortedByDescending { it.appName.lowercase() }
            SortOrder.SIZE_ASC -> filtered.sortedBy { it.apkSize }
            SortOrder.SIZE_DESC -> filtered.sortedByDescending { it.apkSize }
            SortOrder.DATE_ASC -> filtered.sortedBy { it.installTime }
            SortOrder.DATE_DESC -> filtered.sortedByDescending { it.installTime }
        }
        
        _uiState.value = state.copy(filteredApps = filtered)
    }
    
    fun extractSelected() {
        val selectedAppsList = _uiState.value.apps.filter { 
            _uiState.value.selectedApps.contains(it.packageName) 
        }
        val folderUri = _uiState.value.savedFolderUri
        
        if (selectedAppsList.isEmpty()) {
            _uiState.value = _uiState.value.copy(extractMessage = "select_apps")
            return
        }
        
        if (folderUri == null) {
            _uiState.value = _uiState.value.copy(extractMessage = "select_folder")
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isExtracting = true,
                extractMessage = "extracting"
            )
            
            val (successCount, failCount) = apkExtractor.extractMultipleApks(
                apps = selectedAppsList,
                destinationUri = folderUri,
                onProgress = { progress ->
                    _uiState.value = _uiState.value.copy(
                        extractProgress = progress.percent,
                        extractMessage = "extracting"
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
                extractMessage = "done:$successCount:$failCount",
                selectedApps = emptySet()
            )
        }
    }
    
    fun extractSingle(app: AppInfo) {
        val folderUri = _uiState.value.savedFolderUri
        
        if (folderUri == null) {
            _uiState.value = _uiState.value.copy(extractMessage = "select_folder")
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isExtracting = true,
                extractMessage = "extracting_single:${app.appName}"
            )
            
            val result = apkExtractor.extractApk(app, folderUri) { progress ->
                _uiState.value = _uiState.value.copy(
                    extractProgress = progress.percent
                )
            }
            
            _uiState.value = _uiState.value.copy(
                isExtracting = false,
                extractProgress = 0,
                extractMessage = when (result) {
                    is ExtractResult.Success -> "success:${app.appName}"
                    is ExtractResult.Error -> "error:${result.message}"
                    else -> ""
                }
            )
        }
    }
    
    fun clearMessage() {
        _uiState.value = _uiState.value.copy(extractMessage = "")
    }
}