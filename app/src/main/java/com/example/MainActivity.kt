package com.example

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import coil.compose.AsyncImage
import com.example.ui.theme.*
import com.example.domain.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

fun hasStoragePermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var searchViewModel: SearchViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Publish dynamic shortcuts for Direct Share Target integration
        publishShareShortcuts()

        setContent {
            downloadViewModel = viewModel()
            searchViewModel = viewModel()
            
            var showSplash by remember { mutableStateOf(true) }
            
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(2000) // 2.0 seconds delay for splash
                showSplash = false
            }

            val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) { _ -> }

            LaunchedEffect(Unit) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            
            // Handle initial intent
            LaunchedEffect(intent) {
                handleSharedIntent(intent)
            }

            CoveTheme {
                // Enforce RTL layout direction for professional Arabic experience
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    AnimatedContent(
                        targetState = showSplash,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(800, easing = LinearOutSlowInEasing)) togetherWith 
                            fadeOut(animationSpec = tween(800, easing = FastOutSlowInEasing))
                        },
                        label = "SplashTransition"
                    ) { isSplash ->
                        if (isSplash) {
                            SplashScreen()
                        } else {
                            Scaffold(
                                modifier = Modifier.fillMaxSize(),
                                containerColor = MaterialTheme.colorScheme.background
                            ) { innerPadding ->
                                MainScreen(
                                    viewModel = downloadViewModel,
                                    searchViewModel = searchViewModel,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSharedIntent(intent)
    }

    private fun handleSharedIntent(intent: Intent?) {
        if (intent?.getStringExtra("tab") == "HISTORY") {
            if (::downloadViewModel.isInitialized) {
                downloadViewModel.requestedTab = ScreenTab.HISTORY
            }
        }
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrEmpty()) {
                val url = extractUrl(sharedText)
                if (url != null) {
                    if (::downloadViewModel.isInitialized) {
                        downloadViewModel.setUrl(url)
                        downloadViewModel.fetchMedia()
                        Toast.makeText(this, getString(R.string.share_success_alert), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun extractUrl(text: String): String? {
        val urlRegex = "https?://[\\w\\-]+(\\.[\\w\\-]+)+[/#?]?.*"
        val pattern = Pattern.compile(urlRegex, Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(text)
        if (matcher.find()) {
            return matcher.group()
        }
        return null
    }

    private fun publishShareShortcuts() {
        try {
            val shortcutInfoList = listOf(
                ShortcutInfoCompat.Builder(this, "share_shortcut")
                    .setShortLabel("تحميل وسائط")
                    .setLongLabel("تحميل من إنستغرام وفيسبوك")
                    .setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher))
                    .setCategories(setOf("com.example.category.TEXT_SHARE_TARGET"))
                    .setIntent(Intent(this, ShareActivity::class.java).apply {
                        action = Intent.ACTION_SEND
                        type = "text/plain"
                    })
                    .setLongLived(true)
                    .build()
            )
            ShortcutManagerCompat.addDynamicShortcuts(this, shortcutInfoList)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

enum class ScreenTab {
    DOWNLOAD, HISTORY, BROWSER
}


private fun tabOrder(tab: ScreenTab): Int = when (tab) {
    ScreenTab.DOWNLOAD -> 0
    ScreenTab.BROWSER -> 1
    ScreenTab.HISTORY -> 2
}

@Composable
fun PopularSiteItem(
    name: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = name,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = Color.White
        )
    }
}


@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainScreen(viewModel: DownloadViewModel, searchViewModel: SearchViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val playerViewModel: PlayerViewModel = viewModel()
    
    var hasPermissionState by remember { mutableStateOf(hasStoragePermission(context)) }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissionState = hasStoragePermission(context)
        com.example.api.NetworkModule.downloadRepository.scanDownloadedFiles()
    }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasPermissionState = hasStoragePermission(context)
                com.example.api.NetworkModule.downloadRepository.scanDownloadedFiles()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val requestStoragePermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    context.startActivity(intent)
                } catch (ex: Exception) {
                    Toast.makeText(context, "لم نتمكن من فتح الإعدادات تلقائياً. يرجى تفعيلها يدوياً.", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            )
        }
    }
    val scrapingState by viewModel.scrapingState.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    var currentTab by remember { mutableStateOf(ScreenTab.DOWNLOAD) }
    var showDownloadStartedDialog by remember { mutableStateOf(false) }
    val showFullPlayer by playerViewModel.showFullPlayer.collectAsState()

    val searchQuery by searchViewModel.query.collectAsState()
    var fileSearchQuery by remember { mutableStateOf("") }
    var selectedFileTab by remember { mutableStateOf("الكل") }

    val searchResolveState by searchViewModel.resolveState.collectAsState()
    val isReadyForDownload = searchResolveState is StreamResolveState.Ready && (searchResolveState as StreamResolveState.Ready).forDownload

    LaunchedEffect(Unit) {
        playerViewModel.initController(context)
    }

    LaunchedEffect(viewModel.requestedTab) {
        viewModel.requestedTab?.let {
            currentTab = it
            viewModel.requestedTab = null
        }
    }

    LaunchedEffect(currentTab) {
        if (currentTab != ScreenTab.DOWNLOAD) {
            searchViewModel.setSearching(false)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(com.example.ui.theme.CG.Void)
    ) {
        com.example.ui.theme.CobaltAtmosphericBackground()

        AnimatedContent(
            targetState = currentTab,
            transitionSpec = {
                val targetIndex = tabOrder(targetState)
                val currentIndex = tabOrder(initialState)
                if (targetIndex < currentIndex) {
                    (slideInHorizontally { width -> -width } + fadeIn()) togetherWith
                            (slideOutHorizontally { width -> width } + fadeOut())
                } else {
                    (slideInHorizontally { width -> width } + fadeIn()) togetherWith
                            (slideOutHorizontally { width -> -width } + fadeOut())
                }
            },
            label = "ScreenSwitch",
            modifier = Modifier.fillMaxSize()
        ) { tab ->
                when (tab) {
                    ScreenTab.DOWNLOAD -> {
                        val isSearching by searchViewModel.isSearching.collectAsState()
                        if (isSearching) {
                            val focusManager = LocalFocusManager.current
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Transparent)
                            ) {
                                // 1. Top Search Header matching Screenshot 1
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 10.dp)
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { searchViewModel.setSearching(false) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ArrowBack,
                                            contentDescription = "Back",
                                            tint = Color.White
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Pill-shaped search box matching Screenshot 1
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                            .background(OmniGetDesignTokens.Surface2, RoundedCornerShape(OmniGetDesignTokens.Radius2Xl))
                                            .border(0.5.dp, Color(0x14FFFFFF), RoundedCornerShape(OmniGetDesignTokens.Radius2Xl))
                                            .padding(horizontal = OmniGetDesignTokens.Space12),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Search,
                                            contentDescription = null,
                                            tint = OmniGetDesignTokens.TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )

                                        Spacer(modifier = Modifier.width(OmniGetDesignTokens.Space8))

                                        // Actual input text field
                                        val focusRequester = remember { FocusRequester() }
                                        LaunchedEffect(Unit) {
                                            focusRequester.requestFocus()
                                        }

                                        BasicTextField(
                                            value = searchQuery,
                                            onValueChange = { searchViewModel.onQueryChange(it) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .focusRequester(focusRequester),
                                            textStyle = TextStyle(color = OmniGetDesignTokens.TextPrimary, fontSize = 14.sp),
                                            cursorBrush = SolidColor(OmniGetDesignTokens.Accent),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                            keyboardActions = KeyboardActions(onSearch = {
                                                if (searchQuery.isNotBlank()) {
                                                    searchViewModel.performSearch(searchQuery)
                                                    focusManager.clearFocus()
                                                }
                                            }),
                                            decorationBox = { innerTextField ->
                                                if (searchQuery.isEmpty()) {
                                                    Text(
                                                        text = "بحث أو أدخل عنوان...",
                                                        color = OmniGetDesignTokens.TextMuted,
                                                        fontSize = 14.sp
                                                    )
                                                }
                                                innerTextField()
                                            }
                                        )

                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(
                                                onClick = { searchViewModel.onQueryChange("", fetchAutocomplete = false) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Close,
                                                    contentDescription = "Clear",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // 2. Main Search Body Content
                                val suggestionsActive by searchViewModel.searchSuggestionsActive.collectAsState()
                                val suggestionList by searchViewModel.suggestions.collectAsState()
                                val searchState by searchViewModel.state.collectAsState()

                                if (suggestionsActive && suggestionList.isNotEmpty()) {
                                    // Suggestions view matching autocomplete specs
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(bottom = 80.dp)
                                    ) {
                                        items(suggestionList) { suggestion ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        searchViewModel.performSearch(suggestion)
                                                        focusManager.clearFocus()
                                                    }
                                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Search,
                                                    contentDescription = null,
                                                    tint = SilverZinc,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(16.dp))
                                                Text(
                                                    text = suggestion,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = Color.White
                                                )
                                            }
                                            HorizontalDivider(
                                                color = Color(0xFF1C1C1E),
                                                modifier = Modifier.padding(horizontal = 24.dp)
                                            )
                                        }
                                    }
                                } else if (searchState is SearchUiState.Idle && searchQuery.isEmpty()) {
                                    val searchHistory by searchViewModel.searchHistory.collectAsState()
                                    
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                            .padding(horizontal = 20.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "سجل البحث",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = OmniGetDesignTokens.TextPrimary
                                            )
                                            if (searchHistory.isNotEmpty()) {
                                                Text(
                                                    text = "مسح السجل",
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                    color = OmniGetDesignTokens.Accent,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .clickable { searchViewModel.clearSearchHistory() }
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }

                                        if (searchHistory.isEmpty()) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 48.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.History,
                                                    contentDescription = null,
                                                    tint = OmniGetDesignTokens.TextSecondary.copy(alpha = 0.4f),
                                                    modifier = Modifier.size(64.dp)
                                                )
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Text(
                                                    text = "لا توجد عمليات بحث سابقة",
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                                    color = OmniGetDesignTokens.TextSecondary
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "ابدأ بالبحث عن مقاطع الفيديو والمحتوى النافع المفضل لديك",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = OmniGetDesignTokens.TextSecondary.copy(alpha = 0.7f),
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                )
                                            }
                                        } else {
                                            searchHistory.forEach { historyQuery ->
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp)
                                                        .glass(GlassMaterial.REGULAR, RoundedCornerShape(12.dp))
                                                        .clickable {
                                                            searchViewModel.performSearch(historyQuery)
                                                            focusManager.clearFocus()
                                                        }
                                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Rounded.History,
                                                                contentDescription = null,
                                                                tint = OmniGetDesignTokens.TextSecondary,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(12.dp))
                                                            Text(
                                                                text = historyQuery,
                                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                                                color = OmniGetDesignTokens.TextPrimary,
                                                                maxLines = 1,
                                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                            )
                                                        }
                                                        
                                                        IconButton(
                                                            onClick = { searchViewModel.removeSearchQueryFromHistory(historyQuery) },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Rounded.Close,
                                                                contentDescription = "حذف من السجل",
                                                                tint = OmniGetDesignTokens.TextSecondary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // Search results list screen (handled by SearchScreen component inside)
                                    SearchScreen(viewModel = viewModel, searchViewModel = searchViewModel)
                                }
                            }
                        } else {
                            CobaltHome(
                                viewModel = viewModel,
                                hasPermission = hasPermissionState,
                                onRequestPermission = requestStoragePermission,
                                onShowStarted = { showDownloadStartedDialog = it }
                            )
                        }
                    }
                    ScreenTab.BROWSER -> {
                        BrowserScreen(
                            viewModel = viewModel,
                            modifier = Modifier.padding(bottom = 76.dp)
                        )
                    }
                    ScreenTab.HISTORY -> {
                        val storageDetails = remember {
                            try {
                                val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
                                val bytesAvailable = stat.blockSizeLong * stat.availableBlocksLong
                                val totalBytes = stat.blockSizeLong * stat.blockCountLong
                                val gbAvailable = bytesAvailable / (1024.0 * 1024.0 * 1024.0)
                                val gbTotal = totalBytes / (1024.0 * 1024.0 * 1024.0)
                                val usedPercent = ((totalBytes - bytesAvailable).toFloat() / totalBytes.toFloat()) * 100
                                Triple(gbAvailable, gbTotal, usedPercent)
                            } catch (e: Exception) {
                                Triple(0.0, 0.0, 0f)
                            }
                        }

                        val filteredDownloads = remember(downloads, selectedFileTab, fileSearchQuery) {
                            downloads.filter { item ->
                                val matchesCategory = when (selectedFileTab) {
                                    "فيديو" -> item.isVideo
                                    "صوت" -> !item.isVideo && (item.localUri?.endsWith(".m4a") == true || item.localUri?.endsWith(".mp3") == true || item.title.contains("(صوت)"))
                                    "صور" -> item.localUri?.endsWith(".jpg") == true || item.localUri?.endsWith(".png") == true
                                    else -> true
                                }
                                val matchesSearch = item.title.contains(fileSearchQuery, ignoreCase = true)
                                matchesCategory && matchesSearch
                            }
                        }

                        val downloadingList = filteredDownloads.filter { item ->
                            val isCompleted = item.progress == 1f || item.statusText.contains("بنجاح")
                            !isCompleted
                        }

                        val completedList = filteredDownloads.filter { item ->
                            item.progress == 1f || item.statusText.contains("بنجاح")
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Transparent),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                bottom = 140.dp,
                                top = 16.dp + WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = stringResource(R.string.title_history),
                                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White
                                        )
                                        Text(
                                            text = "إدارة وسائطك والتحكم بها من مكان واحد",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = OmniGetDesignTokens.TextSecondary
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(AccentSkyBlue.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.FolderOpen,
                                            contentDescription = null,
                                            tint = AccentSkyBlue,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }

                            item {
                                if (storageDetails.first > 0.0) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = OmniGetDesignTokens.Surface2),
                                        border = BorderStroke(0.5.dp, Color(0x14FFFFFF))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Storage,
                                                contentDescription = null,
                                                tint = AccentSkyBlue,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = "مساحة الهاتف الداخلية",
                                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                        color = Color.White
                                                    )
                                                    Text(
                                                        text = String.format("متوفر %.1f جيجا من %.1f جيجا", storageDetails.first, storageDetails.second),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = OmniGetDesignTokens.TextSecondary
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                LinearProgressIndicator(
                                                    progress = { storageDetails.third / 100f },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(5.dp)
                                                        .clip(CircleShape),
                                                    color = AccentSkyBlue,
                                                    trackColor = Color(0x12FFFFFF)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (!hasPermissionState) {
                                item {
                                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                                        CobaltPermissionCard(onRequest = requestStoragePermission)
                                    }
                                }
                            }

                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .background(OmniGetDesignTokens.Surface2, RoundedCornerShape(24.dp))
                                        .border(0.5.dp, Color(0x14FFFFFF), RoundedCornerShape(24.dp))
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = null,
                                        tint = OmniGetDesignTokens.TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    BasicTextField(
                                        value = fileSearchQuery,
                                        onValueChange = { fileSearchQuery = it },
                                        modifier = Modifier.weight(1f),
                                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                                        cursorBrush = SolidColor(AccentSkyBlue),
                                        singleLine = true,
                                        decorationBox = { innerTextField ->
                                            if (fileSearchQuery.isEmpty()) {
                                                Text(
                                                    text = "البحث في ملفاتك المحملة...",
                                                    color = OmniGetDesignTokens.TextMuted,
                                                    fontSize = 14.sp
                                                )
                                            }
                                            innerTextField()
                                        }
                                    )
                                    if (fileSearchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { fileSearchQuery = "" },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "Clear",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            item {
                                val fileCategories = listOf(
                                    "الكل" to Icons.Rounded.Category,
                                    "فيديو" to Icons.Rounded.PlayCircle,
                                    "صوت" to Icons.Rounded.MusicNote,
                                    "صور" to Icons.Rounded.Image
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    fileCategories.forEach { (catName, icon) ->
                                        val isSelected = selectedFileTab == catName
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(20.dp))
                                                .background(if (isSelected) AccentSkyBlue else OmniGetDesignTokens.Surface2)
                                                .border(0.5.dp, if (isSelected) AccentSkyBlue else Color(0x14FFFFFF), RoundedCornerShape(20.dp))
                                                .clickable { selectedFileTab = catName }
                                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    tint = if (isSelected) OmniGetDesignTokens.Surface0 else Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = catName,
                                                    color = if (isSelected) OmniGetDesignTokens.Surface0 else Color.White,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (filteredDownloads.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 40.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        EmptyHistoryCard()
                                    }
                                }
                            } else {
                                if (downloadingList.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = "جاري تنزيلها الآن (${downloadingList.size})",
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                            color = AccentSkyBlue,
                                            modifier = Modifier.padding(top = 8.dp)
                                        )
                                    }
                                    items(downloadingList, key = { "downloading_${it.id}" }) { item ->
                                        DownloadHistoryItemCard(
                                            item = item,
                                            context = context,
                                            downloadViewModel = viewModel
                                        )
                                    }
                                }

                                if (completedList.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = "الملفات المكتملة (${completedList.size})",
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White,
                                            modifier = Modifier.padding(top = 8.dp)
                                        )
                                    }
                                    items(completedList, key = { "completed_${it.id}" }) { item ->
                                        DownloadHistoryItemCard(
                                            item = item,
                                            context = context,
                                            downloadViewModel = viewModel
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

        // Beautiful glassmorphic bottom navigation docked floating at the bottom (hidden when download window is active)
        if (!isReadyForDownload) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MiniPlayer(
                    viewModel = playerViewModel,
                    onClick = { playerViewModel.setShowFullPlayer(true) }
                )
                
                Spacer(modifier = Modifier.height(8.dp))

                CoveDock(
                    currentTab = currentTab,
                    onTabSelected = { currentTab = it }
                )
            }
        }

        if (showFullPlayer) {
            FullPlayerScreen(
                viewModel = playerViewModel,
                downloadViewModel = viewModel,
                onClose = { playerViewModel.setShowFullPlayer(false) }
            )
        }

        // Ultra-polished compact started download overlay with fully rounded edges
        if (showDownloadStartedDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable(enabled = true, onClick = {}, onClickLabel = null, role = null),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1c1c1e)),
                    modifier = Modifier
                        .width(280.dp)
                        .padding(16.dp),
                    border = BorderStroke(1.dp, Color(0x12FFFFFF))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Big fully rounded circle with white downward arrow in brand Sky Blue
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(AccentSkyBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = null,
                                tint = OmniGetDesignTokens.Surface0, // on-primary
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "بدأ التنزيل!",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // "عرض" (Show) - brand blue container, white text, fully rounded
                            Button(
                                onClick = {
                                    showDownloadStartedDialog = false
                                    currentTab = ScreenTab.HISTORY
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentSkyBlue,
                                    contentColor = OmniGetDesignTokens.Surface0 // on-primary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = "عرض",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            // "ليس الآن" (Not now) - dark semi-trans background, white text, fully rounded
                            Button(
                                onClick = {
                                    showDownloadStartedDialog = false
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2c2c2e),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = "ليس الآن",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}



@Composable
fun EmptyHistoryCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.FolderZip,
            contentDescription = null,
            tint = SilverZinc.copy(alpha = 0.5f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.empty_history),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.empty_history_desc),
            style = MaterialTheme.typography.bodySmall,
            color = SilverZinc,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

@Composable
fun DownloadHistoryItemCard(
    item: DownloadItem, 
    context: Context,
    downloadViewModel: DownloadViewModel = viewModel(),
    playerViewModel: PlayerViewModel = viewModel()
) {
    val isCompleted = item.progress == 1f || item.statusText.contains("بنجاح")
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val downloads by downloadViewModel.downloads.collectAsState()
    val mediaThumb = rememberMediaThumbnail(item.localUri)

    // Read real file size
    val fileSizeText = remember(item.localUri, isCompleted) {
        if (item.localUri != null) {
            val file = File(item.localUri)
            if (file.exists()) {
                val bytes = file.length()
                if (bytes > 0) {
                    val mb = bytes / (1024.0 * 1024.0)
                    if (mb >= 1.0) {
                        String.format("%.1f MB", mb)
                    } else {
                        val kb = bytes / 1024.0
                        String.format("%.1f KB", kb)
                    }
                } else "غير معروف"
            } else {
                if (isCompleted) "محذوف" else "جاري التحضير"
            }
        } else {
            "قيد التحميل"
        }
    }

    // Determine format badge details
    val (formatText, badgeColor) = remember(item.localUri, item.isVideo) {
        if (item.isVideo) {
            Pair("MP4", OmniGetDesignTokens.Accent)
        } else {
            if (item.localUri?.endsWith(".jpg", true) == true || item.localUri?.endsWith(".png", true) == true) {
                Pair("JPG", OmniGetDesignTokens.TextSecondary)
            } else {
                Pair("MP3", OmniGetDesignTokens.Accent)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glass(GlassMaterial.REGULAR, RoundedCornerShape(16.dp))
            .clickable {
                if (isCompleted && item.localUri != null && File(item.localUri).exists()) {
                    if (item.isVideo || formatText == "MP3") {
                        val completedList = downloads.filter { 
                            val isItemCompleted = it.progress == 1f || it.statusText.contains("بنجاح")
                            val isAudio = it.localUri?.endsWith(".mp3", true) == true || it.localUri?.endsWith(".m4a", true) == true
                            isItemCompleted && (it.isVideo || isAudio)
                        }
                        
                        val queue = completedList.map { c ->
                            val isAudio = c.localUri?.endsWith(".mp3", true) == true || c.localUri?.endsWith(".m4a", true) == true
                            PlayableItem(
                                id = c.id.toString(),
                                title = c.title,
                                artist = if (isAudio) "موسيقى Cove" else "فيديو Cove",
                                url = c.localUri ?: "",
                                thumbnailUrl = c.localUri ?: "",
                                isLocal = true
                            )
                        }
                        
                        val index = completedList.indexOfFirst { it.id == item.id }
                        playerViewModel.playQueueItem(
                            PlayableItem(
                                id = item.id.toString(),
                                title = item.title,
                                artist = if (formatText == "MP3") "موسيقى Cove" else "فيديو Cove",
                                url = item.localUri ?: "",
                                thumbnailUrl = item.localUri ?: "",
                                isLocal = true
                            ),
                            queue,
                            if (index != -1) index else 0
                        )
                        playerViewModel.setShowFullPlayer(true)
                    } else {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse(item.localUri), "image/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "لا يوجد تطبيق لعرض الصور", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Sleek Compact Thumbnail with Type Indicator
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(badgeColor.copy(alpha = 0.12f))
                        .border(0.5.dp, Color(0x14FFFFFF), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val thumbModel = mediaThumb ?: item.thumbnailUrl
                    if (thumbModel != null) {
                        AsyncImage(
                            model = thumbModel,
                            contentDescription = item.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            error = painterResource(id = android.R.drawable.ic_media_play)
                        )
                    } else {
                        Icon(
                            imageVector = if (item.isVideo) Icons.Rounded.PlayCircleOutline else (if (formatText == "JPG") Icons.Rounded.Image else Icons.Rounded.MusicNote),
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Bottom-right play overlay on the thumbnail
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(2.dp)
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (item.isVideo) Icons.Rounded.PlayArrow else (if (formatText == "JPG") Icons.Rounded.Image else Icons.Rounded.MusicNote),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // 2. Clean Typography and Meta Information
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = OmniGetDesignTokens.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Extension Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(badgeColor.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = formatText,
                                color = badgeColor,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp)
                            )
                        }

                        // Size and Date joined as a single non-wrapping block
                        val sdf = remember { SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()) }
                        val formattedDate = remember(item.timestamp) { sdf.format(Date(item.timestamp)) }
                        val metaText = remember(fileSizeText, formattedDate) {
                            if (isCompleted) {
                                "$fileSizeText  •  $formattedDate"
                            } else {
                                fileSizeText
                            }
                        }
                        Text(
                            text = metaText,
                            style = MaterialTheme.typography.bodySmall,
                            color = OmniGetDesignTokens.TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 3. Compact Actions: Share and Delete (Play is handled via clicking the card)
                val isFailed = item.statusText.contains("فشل")
                if ((isCompleted && item.localUri != null && File(item.localUri).exists()) || isFailed) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (!isFailed) {
                            // Share
                            IconButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = if (item.isVideo) "video/*" else (if (formatText == "JPG") "image/*" else "audio/*")
                                            putExtra(Intent.EXTRA_STREAM, Uri.parse(item.localUri))
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        val chooser = Intent.createChooser(intent, "مشاركة الملف")
                                        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(chooser)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "فشلت مشاركة الملف", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Share,
                                    contentDescription = "Share",
                                    tint = OmniGetDesignTokens.TextPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Delete
                        IconButton(
                            onClick = { 
                                if (isFailed) {
                                    // Delete directly without confirmation for failed items to keep experience slick
                                    downloadViewModel.deleteDownload(item.id)
                                } else {
                                    showDeleteConfirm = true 
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "Delete",
                                tint = OmniGetDesignTokens.TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Real-Time Download Progress Indicator for active downloads
            if (item.progress >= 0f && item.progress < 1.0f && !isCompleted && !item.statusText.contains("فشل")) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape),
                    color = OmniGetDesignTokens.Accent,
                    trackColor = OmniGetDesignTokens.Surface2
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = buildString {
                            append(item.statusText)
                            val etaText = com.example.download.DownloadFormat.eta(item.etaSeconds)
                            if (etaText.isNotEmpty()) {
                                append(" • ")
                                append(etaText)
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = OmniGetDesignTokens.TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = buildString {
                            append("${(item.progress * 100).toInt()}%")
                            val speedText = com.example.download.DownloadFormat.speed(item.speedBytesPerSec)
                            if (speedText.isNotEmpty()) {
                                append(" • ")
                                append(speedText)
                            }
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = OmniGetDesignTokens.Accent,
                        fontSize = 11.sp
                    )
                    if (!item.statusText.contains("فشل") && item.statusText != "تم إلغاء التنزيل") {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .clickable { downloadViewModel.cancelDownload(item.id) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "إلغاء التنزيل",
                                tint = OmniGetDesignTokens.DangerText,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            } else if (item.progress == -1f && !isCompleted) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape),
                    color = OmniGetDesignTokens.Accent,
                    trackColor = OmniGetDesignTokens.Surface2
                )
            } else if (item.statusText.contains("فشل")) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.statusText,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = OmniGetDesignTokens.DangerText,
                    fontSize = 11.sp
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    text = "حذف الملف نهائياً؟",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = OmniGetDesignTokens.TextPrimary
                )
            },
            text = {
                Text(
                    text = "سيؤدي هذا إلى حذف الملف من جهازك بشكل نهائي ولا يمكن التراجع عن هذا الإجراء.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OmniGetDesignTokens.TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        downloadViewModel.deleteDownload(item.id)
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OmniGetDesignTokens.DangerBg,
                        contentColor = OmniGetDesignTokens.DangerText
                    ),
                    shape = RoundedCornerShape(OmniGetDesignTokens.RadiusSm),
                    border = BorderStroke(1.dp, OmniGetDesignTokens.DangerText.copy(alpha = 0.2f))
                ) {
                    Text(text = "حذف", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(text = "إلغاء", color = OmniGetDesignTokens.TextPrimary)
                }
            },
            containerColor = OmniGetDesignTokens.Surface1,
            shape = RoundedCornerShape(OmniGetDesignTokens.RadiusLg),
            modifier = Modifier.border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(OmniGetDesignTokens.RadiusLg))
        )
    }
}

