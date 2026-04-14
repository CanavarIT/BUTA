package com.backup.the.apk

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.backup.the.apk.ui.theme.ApkBackuperTheme
import kotlinx.coroutines.launch
import java.util.*

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            }
        }
        
        setContent {
            val appScanner = remember { AppScanner(this) }
            val apkExtractor = remember { ApkExtractor(this) }
            
            val viewModel: MainViewModel = viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        return MainViewModel(appScanner, apkExtractor, this@MainActivity) as T
                    }
                }
            )
            
            val uiState by viewModel.uiState.collectAsState()
            
            LaunchedEffect(uiState.currentLanguage) {
                setAppLocale(this@MainActivity, uiState.currentLanguage)
            }
            
            ApkBackuperTheme(darkTheme = uiState.isDarkTheme) {
                MainScreen(viewModel = viewModel)
            }
        }
    }
    
    private fun setAppLocale(context: Context, languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val totalSize = uiState.selectedApps.sumOf { selectedPkg ->
        uiState.apps.find { it.packageName == selectedPkg }?.apkSize ?: 0
    }
    
    var showSortDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showSplitInfoDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var selectedAppForDetail by remember { mutableStateOf<AppInfo?>(null) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val showScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 5 }
    }
    
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.setSavedFolderUri(it)
            Toast.makeText(context, R.string.toast_folder_selected, Toast.LENGTH_SHORT).show()
        }
    }
    
    LaunchedEffect(Unit) {
        viewModel.loadSavedFolderUri()?.let {
            viewModel.setSavedFolderUri(it)
        }
    }
    
    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = {
                        Column {
                            Text(
                                stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (uiState.selectedApps.isNotEmpty()) {
                                Text(
                                    stringResource(R.string.selected_count, uiState.selectedApps.size, FileUtils.formatFileSize(totalSize)),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { showLanguageDialog = true }) {
                            Icon(
                                Icons.Default.Language,
                                stringResource(R.string.language),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSortDialog = true }) {
                            Icon(Icons.Default.Sort, stringResource(R.string.sort))
                        }
                        IconButton(onClick = { viewModel.invertSelection() }) {
                            Icon(Icons.Default.SwapHoriz, stringResource(R.string.invert))
                        }
                        IconButton(onClick = { viewModel.selectAll() }) {
                            Icon(Icons.Default.SelectAll, stringResource(R.string.select_all))
                        }
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Clear, stringResource(R.string.clear))
                        }
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, stringResource(R.string.more))
                        }
                        
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.filters)) },
                                onClick = {
                                    showFilterDialog = true
                                    showMoreMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.FilterList, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.export)) },
                                onClick = {
                                    val uri = FileUtils.exportAppsList(context, uiState.filteredApps)
                                    uri?.let { 
                                        FileUtils.shareExportedList(context, it) 
                                    } ?: Toast.makeText(context, R.string.toast_export_error, Toast.LENGTH_SHORT).show()
                                    showMoreMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.Share, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.theme)) },
                                onClick = {
                                    viewModel.toggleTheme()
                                    showMoreMenu = false
                                },
                                leadingIcon = { 
                                    Icon(
                                        if (uiState.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                        null
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.folder)) },
                                onClick = {
                                    folderPickerLauncher.launch(null)
                                    showMoreMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.Folder, null) }
                            )
                            Divider()
                            DropdownMenuItem(
                                text = { Text("GitHub") },
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/CanavarIT/BUTA"))
                                    context.startActivity(intent)
                                    showMoreMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(id = R.drawable.github),
                                        contentDescription = "GitHub",
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                
                ScrollableTabRow(
                    selectedTabIndex = uiState.selectedTab,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    edgePadding = 8.dp
                ) {
                    Tab(selected = uiState.selectedTab == 0, onClick = { viewModel.setSelectedTab(0) },
                        text = { Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Apps, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("${stringResource(R.string.all_apps)} (${uiState.apps.size})")
                        } }
                    )
                    Tab(selected = uiState.selectedTab == 1, onClick = { viewModel.setSelectedTab(1) },
                        text = { Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("${stringResource(R.string.user_apps)} (${uiState.apps.count { it.appType == AppType.USER }})")
                        } }
                    )
                    Tab(selected = uiState.selectedTab == 2, onClick = { viewModel.setSelectedTab(2) },
                        text = { Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Settings, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("${stringResource(R.string.system_apps)} (${uiState.apps.count { it.appType == AppType.SYSTEM || it.appType == AppType.UPDATED_SYSTEM }})")
                        } }
                    )
                    Tab(selected = uiState.selectedTab == 3, onClick = { viewModel.setSelectedTab(3) },
                        text = { Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("${stringResource(R.string.favorites)} (${uiState.apps.count { it.isFavorite }})")
                        } }
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, stringResource(R.string.clear))
                                }
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true
                    )
                    
                    if (uiState.isRootAvailable) {
                        Spacer(Modifier.width(8.dp))
                        Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF4CAF50)) {
                            Text(" ROOT ", style = MaterialTheme.typography.labelSmall, color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (uiState.selectedApps.isNotEmpty() && uiState.savedFolderUri != null) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.extractSelected() },
                    icon = { Icon(Icons.Default.Archive, null) },
                    text = { Text(stringResource(R.string.extract_button, uiState.selectedApps.size)) }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                uiState.isLoading -> {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.loading))
                        }
                    }
                }
                uiState.isExtracting -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                            CircularProgressIndicator(
                                progress = { uiState.extractProgress / 100f },
                                modifier = Modifier.fillMaxSize(),
                                strokeWidth = 8.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "${uiState.extractProgress}%",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                        Text(getExtractMessage(context, uiState.extractMessage))
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(uiState.filteredApps, key = { it.packageName }) { app ->
                            AppItemCard(
                                app = app,
                                isSelected = uiState.selectedApps.contains(app.packageName),
                                onToggleSelection = { viewModel.toggleAppSelection(app.packageName) },
                                onToggleFavorite = { viewModel.toggleFavorite(app.packageName) },
                                onShare = { FileUtils.shareApk(context, app.sourceDir, app.appName) },
                                onExtract = {
                                    if (uiState.savedFolderUri != null) {
                                        viewModel.extractSingle(app)
                                    } else {
                                        Toast.makeText(context, R.string.toast_select_folder_first, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onSplitClick = { showSplitInfoDialog = true },
                                onClick = { selectedAppForDetail = app }
                            )
                        }
                    }
                }
            }
            
            if (showScrollToTop && !uiState.isLoading && !uiState.isExtracting) {
                FloatingActionButton(
                    onClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.secondary
                ) {
                    Icon(Icons.Default.ArrowUpward, stringResource(R.string.scroll_to_top))
                }
            }
            
            if (uiState.extractMessage.isNotEmpty() && !uiState.isExtracting) {
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    action = { TextButton(onClick = { viewModel.clearMessage() }) { Text(stringResource(R.string.ok)) } }
                ) {
                    Text(getExtractMessage(context, uiState.extractMessage))
                }
            }
        }
    }
    
    // Диалог выбора языка
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.language)) },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            viewModel.setLanguage("ru")
                            showLanguageDialog = false
                        }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = uiState.currentLanguage == "ru", onClick = {})
                        Text("🇷🇺 Русский", modifier = Modifier.padding(start = 12.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            viewModel.setLanguage("en")
                            showLanguageDialog = false
                        }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = uiState.currentLanguage == "en", onClick = {})
                        Text("🇬🇧 English", modifier = Modifier.padding(start = 12.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLanguageDialog = false }) { Text(stringResource(R.string.ok)) } }
        )
    }
    
    // Диалог Split APK
    if (showSplitInfoDialog) {
        AlertDialog(
            onDismissRequest = { showSplitInfoDialog = false },
            title = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = Color(0xFFFF9800))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.split_info_title))
                }
            },
            text = {
                Column {
                    Text(stringResource(R.string.split_info_text))
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(stringResource(R.string.split_recommendations), fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(R.string.split_tip_1))
                            Text(stringResource(R.string.split_tip_2))
                            Text(stringResource(R.string.split_tip_3))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSplitInfoDialog = false }) {
                    Text(stringResource(R.string.split_got_it))
                }
            }
        )
    }
    
    // Диалог сортировки
    if (showSortDialog) {
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text(stringResource(R.string.sort)) },
            text = {
                Column {
                    listOf(
                        SortOrder.NAME_ASC to R.string.sort_name_asc,
                        SortOrder.NAME_DESC to R.string.sort_name_desc,
                        SortOrder.SIZE_ASC to R.string.sort_size_asc,
                        SortOrder.SIZE_DESC to R.string.sort_size_desc,
                        SortOrder.DATE_ASC to R.string.sort_date_asc,
                        SortOrder.DATE_DESC to R.string.sort_date_desc
                    ).forEach { (order, labelRes) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                viewModel.setSortOrder(order)
                                showSortDialog = false
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = uiState.sortOrder == order, onClick = {
                                viewModel.setSortOrder(order)
                                showSortDialog = false
                            })
                            Text(stringResource(labelRes), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSortDialog = false }) { Text(stringResource(R.string.close)) } }
        )
    }
    
    // Диалог фильтров
    if (showFilterDialog) {
        AlertDialog(
            onDismissRequest = { showFilterDialog = false },
            title = { Text(stringResource(R.string.filters)) },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.toggleSplitFilter() }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = uiState.showOnlySplit, onCheckedChange = { viewModel.toggleSplitFilter() })
                        Text(stringResource(R.string.filter_split_only), modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.toggleLaunchFilter() }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = uiState.showOnlyWithLaunch, onCheckedChange = { viewModel.toggleLaunchFilter() })
                        Text(stringResource(R.string.filter_with_launch), modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.toggleGooglePlayFilter() }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = uiState.showOnlyGooglePlay, onCheckedChange = { viewModel.toggleGooglePlayFilter() })
                        Text(stringResource(R.string.filter_google_play), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFilterDialog = false }) { Text(stringResource(R.string.close)) } }
        )
    }
    
    // Детальный диалог
    selectedAppForDetail?.let { app ->
        AppDetailDialog(
            app = app,
            onDismiss = { selectedAppForDetail = null },
            onExtract = {
                if (uiState.savedFolderUri != null) {
                    viewModel.extractSingle(app)
                } else {
                    Toast.makeText(context, R.string.toast_select_folder_first, Toast.LENGTH_SHORT).show()
                }
                selectedAppForDetail = null
            },
            onShare = { FileUtils.shareApk(context, app.sourceDir, app.appName) }
        )
    }
}

@Composable
fun AppItemCard(
    app: AppInfo,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
    onExtract: () -> Unit,
    onSplitClick: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.Image(
                bitmap = app.icon.toBitmap().asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
            )
            
            Spacer(Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(app.appName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (app.isFavorite) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.Star, null, modifier = Modifier.size(16.dp), tint = Color(0xFFFFD700))
                    }
                }
                Text(
                    "${app.versionName} • ${FileUtils.formatFileSize(app.apkSize)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppTypeChip(app.appType)
                    Spacer(Modifier.width(4.dp))
                    InstallSourceChip(app.installSource)
                    if (app.isSplitRequired) {
                        Spacer(Modifier.width(4.dp))
                        Row(
                            modifier = Modifier
                                .background(Color(0xFFFF9800), RoundedCornerShape(4.dp))
                                .clickable { onSplitClick() }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                null,
                                modifier = Modifier.size(12.dp),
                                tint = Color.White
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                stringResource(R.string.chip_split),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            
            IconButton(onClick = onToggleFavorite) {
                Icon(if (app.isFavorite) Icons.Default.Star else Icons.Default.StarBorder, stringResource(R.string.favorite),
                    tint = if (app.isFavorite) Color(0xFFFFD700) else LocalContentColor.current)
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Default.Share, stringResource(R.string.share))
            }
            IconButton(onClick = onExtract) {
                Icon(Icons.Default.Download, stringResource(R.string.extract))
            }
            Checkbox(checked = isSelected, onCheckedChange = { onToggleSelection() })
        }
    }
}

@Composable
fun AppTypeChip(type: AppType) {
    val (textRes, color) = when (type) {
        AppType.SYSTEM -> R.string.chip_system to Color(0xFF607D8B)
        AppType.UPDATED_SYSTEM -> R.string.chip_updated to Color(0xFFFF9800)
        AppType.USER -> R.string.chip_user to Color(0xFF4CAF50)
    }
    Surface(shape = RoundedCornerShape(4.dp), color = color) {
        Text(stringResource(textRes), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}

@Composable
fun InstallSourceChip(source: InstallSource) {
    val textRes = when (source) {
        InstallSource.GOOGLE_PLAY -> R.string.chip_play
        InstallSource.SAMSUNG_STORE -> R.string.chip_samsung
        InstallSource.HUAWEI_STORE -> R.string.chip_huawei
        InstallSource.XIAOMI_STORE -> R.string.chip_xiaomi
        InstallSource.AMAZON_STORE -> R.string.chip_amazon
        InstallSource.SIDELOAD -> R.string.chip_apk
        InstallSource.SYSTEM -> R.string.chip_system_source
        InstallSource.UNKNOWN -> R.string.chip_unknown
    }
    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF2196F3)) {
        Text(stringResource(textRes), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}

@Composable
fun AppDetailDialog(
    app: AppInfo,
    onDismiss: () -> Unit,
    onExtract: () -> Unit,
    onShare: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.Image(
                    bitmap = app.icon.toBitmap().asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.width(12.dp))
                Text(app.appName, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                DetailRow(stringResource(R.string.detail_package), app.packageName)
                DetailRow(stringResource(R.string.detail_version), "${app.versionName} (${app.versionCode})")
                DetailRow(stringResource(R.string.detail_size), FileUtils.formatFileSize(app.apkSize))
                DetailRow(stringResource(R.string.detail_installed), FileUtils.formatTimestamp(app.installTime))
                DetailRow(stringResource(R.string.detail_updated), FileUtils.formatTimestamp(app.updateTime))
                DetailRow(stringResource(R.string.detail_sdk), "Min: ${app.minSdk} | Target: ${app.targetSdk}")
                DetailRow(stringResource(R.string.detail_type), app.appType.toString())
                DetailRow(stringResource(R.string.detail_source), app.installSource.toString())
                if (app.techStack.isNotEmpty()) {
                    DetailRow(stringResource(R.string.detail_stack), app.techStack.joinToString(", "))
                }
                DetailRow(stringResource(R.string.detail_launch), if (app.hasLaunchIntent) stringResource(R.string.yes) else stringResource(R.string.no))
                DetailRow(stringResource(R.string.detail_split), if (app.isSplitRequired) stringResource(R.string.warning_yes) else stringResource(R.string.ok_check))
            }
        },
        confirmButton = {
            TextButton(onClick = onExtract) {
                Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.extract))
            }
        },
        dismissButton = {
            TextButton(onClick = onShare) {
                Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.share))
            }
        }
    )
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text("$label: ", fontWeight = FontWeight.Medium, modifier = Modifier.width(95.dp))
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun getExtractMessage(context: Context, message: String): String {
    return when {
        message == "extracting" -> context.getString(R.string.extracting)
        message == "select_apps" -> context.getString(R.string.toast_select_apps)
        message == "select_folder" -> context.getString(R.string.toast_select_folder_for_save)
        message.startsWith("extracting_single:") -> {
            val appName = message.substringAfter(":")
            context.getString(R.string.extracting) + " $appName"
        }
        message.startsWith("done:") -> {
            val parts = message.split(":")
            context.getString(R.string.toast_extract_complete, parts[1].toInt(), parts[2].toInt())
        }
        message.startsWith("success:") -> {
            val appName = message.substringAfter(":")
            context.getString(R.string.toast_extract_single_success, appName)
        }
        message.startsWith("error:") -> {
            val error = message.substringAfter(":")
            context.getString(R.string.toast_extract_single_error, error)
        }
        else -> message
    }
}