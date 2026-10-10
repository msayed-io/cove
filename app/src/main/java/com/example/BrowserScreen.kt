package com.example
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.border

import com.example.ui.theme.OmniGetDesignTokens
import com.example.ui.theme.glass
import com.example.ui.theme.GlassMaterial

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(viewModel: DownloadViewModel, modifier: Modifier = Modifier) {
    val url = viewModel.browserUrl
    var inputUrl by remember { mutableStateOf(url) }
    var webView: WebView? by remember { mutableStateOf(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    // Professional cleanup of WebView resources
    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                loadUrl("about:blank")
                clearHistory()
                removeAllViews()
                destroy()
            }
        }
    }

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Address Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glass(GlassMaterial.STRONG, RoundedCornerShape(0.dp))
        ) {
            Column {
                Row(
                     modifier = Modifier
                        .fillMaxWidth()
                        .padding(OmniGetDesignTokens.Space8),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { webView?.goBack() }, enabled = canGoBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack, 
                            contentDescription = "Back", 
                            tint = if (canGoBack) OmniGetDesignTokens.TextPrimary else OmniGetDesignTokens.TextMuted
                        )
                    }
                    IconButton(onClick = { webView?.goForward() }, enabled = canGoForward) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward, 
                            contentDescription = "Forward", 
                            tint = if (canGoForward) OmniGetDesignTokens.TextPrimary else OmniGetDesignTokens.TextMuted
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(OmniGetDesignTokens.RadiusXl))
                            .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(OmniGetDesignTokens.RadiusXl))
                            .padding(horizontal = OmniGetDesignTokens.Space12),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = inputUrl,
                            onValueChange = { inputUrl = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = TextStyle(color = OmniGetDesignTokens.TextPrimary, fontSize = 14.sp),
                            cursorBrush = SolidColor(OmniGetDesignTokens.Accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = {
                                var target = inputUrl.trim()
                                if (target.isNotEmpty()) {
                                    if (!target.startsWith("http://") && !target.startsWith("https://")) {
                                        target = "https://www.google.com/search?q=$target"
                                    }
                                    if (ContentFilter.isBlockedUrl(target) || ContentFilter.containsBlockedContent(target)) {
                                        android.widget.Toast.makeText(context, "عذراً، تم حظر هذا البحث أو العنوان تماشياً مع قيمنا الإسلامية لتجنب الذنوب الأغاني.", android.widget.Toast.LENGTH_LONG).show()
                                    } else {
                                        viewModel.browserUrl = target
                                        focusManager.clearFocus()
                                    }
                                }
                            }),
                            decorationBox = { innerTextField ->
                                if (inputUrl.isEmpty()) {
                                    Text("بحث أو أدخل عنوان...", color = OmniGetDesignTokens.TextMuted, fontSize = 14.sp)
                                }
                                innerTextField()
                            }
                        )
                    }

                    IconButton(onClick = { 
                        if (isLoading) webView?.stopLoading() else webView?.reload() 
                    }) {
                        Icon(if (isLoading) Icons.Rounded.Close else Icons.Rounded.Refresh, "Reload/Stop", tint = OmniGetDesignTokens.TextPrimary)
                    }
                    
                    // Improved Menu Implementation
                    var showMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Rounded.MoreVert, "Menu", tint = OmniGetDesignTokens.TextPrimary)
                    }

                    if (showMenu) {
                        ModalBottomSheet(
                            onDismissRequest = { showMenu = false },
                            containerColor = OmniGetDesignTokens.Surface1,
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                        ) {
                            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                                @Composable
                                fun MenuItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
                                    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(onClick = onClick)
                                                .padding(horizontal = 24.dp, vertical = 18.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = text, 
                                                color = OmniGetDesignTokens.TextPrimary, 
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                                )
                                            )
                                            Icon(
                                                imageVector = icon, 
                                                contentDescription = null, 
                                                tint = OmniGetDesignTokens.TextPrimary, 
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }

                                MenuItem("فتح علامة تبويب جديدة", Icons.Rounded.Add) {
                                    webView?.loadUrl("about:blank")
                                    showMenu = false
                                }
                                MenuItem("السجل", Icons.Rounded.History) {
                                    viewModel.requestedTab = ScreenTab.HISTORY
                                    showMenu = false
                                }
                                MenuItem("التنزيلات", Icons.Rounded.Download) {
                                    try {
                                        val downloadIntent = android.content.Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)
                                        downloadIntent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                        context.startActivity(downloadIntent)
                                    } catch (e: Exception) {
                                        // Fallback if no download manager is found
                                    }
                                    showMenu = false
                                }
                                MenuItem("بحث في الصفحة", Icons.Rounded.Search) {
                                    webView?.showFindDialog(null, true)
                                    showMenu = false
                                }
                                MenuItem("مسح بيانات التصفح", Icons.Rounded.Delete) {
                                    CookieManager.getInstance().removeAllCookies(null)
                                    webView?.clearCache(true)
                                    webView?.clearHistory()
                                    showMenu = false
                                }
                                var isDesktopMode by remember { mutableStateOf(false) }
                                MenuItem(if (isDesktopMode) "وضع الهاتف" else "وضع سطح المكتب", Icons.Rounded.Computer) {
                                    isDesktopMode = !isDesktopMode
                                    val desktopAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36"
                                    val mobileAgent = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
                                    webView?.settings?.userAgentString = if (isDesktopMode) desktopAgent else mobileAgent
                                    webView?.reload()
                                    showMenu = false
                                }
                                MenuItem("مشاركة", Icons.Rounded.Share) {
                                    val sendIntent = android.content.Intent().apply {
                                        action = android.content.Intent.ACTION_SEND
                                        putExtra(android.content.Intent.EXTRA_TEXT, webView?.url)
                                        type = "text/plain"
                                    }
                                    val shareIntent = android.content.Intent.createChooser(sendIntent, null)
                                    context.startActivity(shareIntent)
                                    showMenu = false
                                }
                            }
                        }
                    }
                }
                
                if (isLoading) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = OmniGetDesignTokens.Accent,
                        trackColor = Color.Transparent
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        
                        // Professional Professional Configuration
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            
                            // Compatibility & Performance
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            cacheMode = WebSettings.LOAD_DEFAULT
                            javaScriptCanOpenWindowsAutomatically = true
                            mediaPlaybackRequiresUserGesture = false
                            
                            // Professional User Agent (Modern Android Chrome)
                            userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
                        }
                        
                        // Session & Cookie Management
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                if (url != null && (ContentFilter.isBlockedUrl(url) || ContentFilter.containsBlockedContent(url))) {
                                    view?.stopLoading()
                                    view?.loadUrl("about:blank")
                                    android.widget.Toast.makeText(context, "عذراً، تم حظر هذه الصفحة تماشياً مع قيمنا الإسلامية لمنع المحتوى الموسيقي.", android.widget.Toast.LENGTH_LONG).show()
                                    isLoading = false
                                } else {
                                    isLoading = true
                                    url?.let { inputUrl = it }
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                canGoBack = view?.canGoBack() ?: false
                                canGoForward = view?.canGoForward() ?: false
                                url?.let { 
                                    inputUrl = it 
                                    // Sync back to ViewModel to preserve session context
                                    viewModel.browserUrl = it
                                }
                                // Ensure cookies are persisted immediately
                                CookieManager.getInstance().flush()
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val urlStr = request?.url?.toString()
                                if (urlStr != null && (ContentFilter.isBlockedUrl(urlStr) || ContentFilter.containsBlockedContent(urlStr))) {
                                    android.widget.Toast.makeText(context, "عذراً، تم حظر هذا الرابط لتجنب المحتوى الموسيقي والأغاني.", android.widget.Toast.LENGTH_LONG).show()
                                    return true
                                }
                                return false
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                if (request?.isForMainFrame == true) {
                                    isLoading = false
                                }
                            }
                        }
                        
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                            }
                        }
                        
                        loadUrl(url)
                        webView = this
                    }
                },
                update = { view ->
                    // Only load if the ViewModel source changed and differs from the current webview state
                    if (view.url != url && !isLoading) {
                        view.loadUrl(url)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Floating Download Button (Snaptube style)
            val currentWebUrl = webView?.url ?: ""
            val lowerWebUrl = currentWebUrl.lowercase()
            val isSearchEngine = lowerWebUrl.contains("google.com/search") || 
                                 lowerWebUrl.contains("google.com/?") || 
                                 lowerWebUrl == "https://www.google.com" || 
                                 lowerWebUrl == "https://www.google.com/" ||
                                 lowerWebUrl.contains("bing.com") || 
                                 lowerWebUrl.contains("duckduckgo.com") ||
                                 lowerWebUrl.startsWith("about:") ||
                                 currentWebUrl.isEmpty()
            val isDownloadable = !isSearchEngine

            var showDownloadSheet by remember { mutableStateOf(false) }

            if (showDownloadSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showDownloadSheet = false },
                    containerColor = OmniGetDesignTokens.Surface1,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 16.dp)
                                .padding(bottom = 24.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = "خيارات التحميل السريع ⚡",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    color = OmniGetDesignTokens.TextPrimary,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text(
                                text = currentWebUrl.take(50) + if (currentWebUrl.length > 50) "..." else "",
                                style = MaterialTheme.typography.bodySmall.copy(color = OmniGetDesignTokens.TextMuted),
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            Button(
                                onClick = {
                                    showDownloadSheet = false
                                    viewModel.onUrlInput(currentWebUrl)
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = OmniGetDesignTokens.Accent),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Rounded.Download, contentDescription = null, tint = Color(0xFF241205))
                                    Text("تحميل تلقائي / فحص الجودات", color = Color(0xFF241205), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedButton(
                                onClick = {
                                    showDownloadSheet = false
                                    viewModel.downloadDirectUrl(context, "فيديو مشترك", currentWebUrl, isVideo = true, isAudioOnly = false)
                                    viewModel.requestedTab = ScreenTab.DOWNLOAD
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Rounded.Movie, contentDescription = null, tint = OmniGetDesignTokens.Accent)
                                    Text("تحميل مباشر (فيديو MP4)", color = OmniGetDesignTokens.TextPrimary, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedButton(
                                onClick = {
                                    showDownloadSheet = false
                                    viewModel.downloadDirectUrl(context, "صوت مشترك", currentWebUrl, isVideo = false, isAudioOnly = true)
                                    viewModel.requestedTab = ScreenTab.DOWNLOAD
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = OmniGetDesignTokens.Accent)
                                    Text("تحميل صوت فقط (صوت MP3)", color = OmniGetDesignTokens.TextPrimary, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedButton(
                                onClick = {
                                    showDownloadSheet = false
                                    viewModel.downloadDirectUrl(context, "صورة مشتركة", currentWebUrl, isVideo = false, isAudioOnly = false)
                                    viewModel.requestedTab = ScreenTab.DOWNLOAD
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Rounded.Image, contentDescription = null, tint = OmniGetDesignTokens.Accent)
                                    Text("تحميل صورة (JPG/PNG)", color = OmniGetDesignTokens.TextPrimary, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                }
                            }
                        }
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = isDownloadable,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 80.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        showDownloadSheet = true
                    },
                    containerColor = OmniGetDesignTokens.Accent,
                    contentColor = Color(0xFF241205),
                    shape = RoundedCornerShape(OmniGetDesignTokens.RadiusSm)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Rounded.Download, "Download from this page")
                        Text("تحميل", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
