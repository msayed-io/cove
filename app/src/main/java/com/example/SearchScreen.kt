package com.example

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.domain.SearchResult
import com.example.domain.StreamInfo
import com.example.ui.theme.*

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SearchScreen(
    viewModel: DownloadViewModel,
    searchViewModel: SearchViewModel,
    playerViewModel: PlayerViewModel = viewModel()
) {
    val searchState by searchViewModel.state.collectAsState()
    val resolveState by searchViewModel.resolveState.collectAsState()
    val query by searchViewModel.query.collectAsState()
    val context = LocalContext.current

    // Handle stream resolution for PLAY ONLY (Ready state where forDownload is false) and errors
    LaunchedEffect(resolveState) {
        when (resolveState) {
            is StreamResolveState.Ready -> {
                val ready = resolveState as StreamResolveState.Ready
                if (!ready.forDownload) {
                    val searchState = searchViewModel.state.value
                    val results = (searchState as? SearchUiState.Success)?.results ?: emptyList()
                    val queue = results.map { r ->
                        PlayableItem(
                            id = r.videoId,
                            title = r.title,
                            artist = r.uploaderName,
                            url = "", // Will resolve dynamically inside PlayerViewModel
                            thumbnailUrl = r.thumbnailUrl,
                            isLocal = false,
                            durationSeconds = r.durationSeconds
                        )
                    }
                    val index = results.indexOfFirst { it.videoId == ready.info.videoId }

                    playerViewModel.playQueueItem(
                        PlayableItem(
                            id = ready.info.videoId,
                            title = ready.info.title,
                            artist = ready.info.uploader,
                            url = ready.info.primaryPlayUrl,
                            thumbnailUrl = ready.info.thumbnailUrl,
                            isLocal = false,
                            durationSeconds = ready.info.durationSeconds
                        ),
                        queue,
                        if (index != -1) index else 0
                    )
                    searchViewModel.resetResolveState()
                    playerViewModel.setShowFullPlayer(true)
                    Toast.makeText(context, "جاري تشغيل: ${ready.info.title}", Toast.LENGTH_SHORT).show()
                }
            }
            is StreamResolveState.Error -> {
                val errorMsg = (resolveState as StreamResolveState.Error).message
                Toast.makeText(context, "عذراً، فشل استخراج الرابط: $errorMsg", Toast.LENGTH_LONG).show()
                searchViewModel.resetResolveState()
            }
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Stream resolving top progress bar
            if (resolveState is StreamResolveState.Loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = OmniGetDesignTokens.Accent,
                    trackColor = Color.Transparent
                )
            }

            // Screen Content for search results
            Box(modifier = Modifier.weight(1f)) {
                when (val state = searchState) {
                    is SearchUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = OmniGetDesignTokens.Accent)
                        }
                    }
                    is SearchUiState.Success -> {
                        if (state.results.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(text = "لا توجد نتائج", color = OmniGetDesignTokens.TextSecondary)
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(top = 12.dp, bottom = 140.dp, start = 16.dp, end = 16.dp)
                            ) {
                                items(state.results) { result ->
                                    SearchResultItem(
                                        result = result,
                                        onDownloadClick = {
                                            searchViewModel.prepareDownload(result)
                                        },
                                        onPlayClick = {
                                            searchViewModel.resolveStream(result.videoId, forDownload = false)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    is SearchUiState.Blocked -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .glass(GlassMaterial.STRONG, RoundedCornerShape(24.dp))
                                    .border(1.dp, OmniGetDesignTokens.Accent.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Guidance/Light Icon with glow
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(OmniGetDesignTokens.Accent.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Favorite,
                                        contentDescription = null,
                                        tint = OmniGetDesignTokens.Accent,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                // Blocked Message
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 24.sp
                                    ),
                                    color = OmniGetDesignTokens.TextPrimary,
                                    textAlign = TextAlign.Center
                                )

                                // Decorative Divider
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.4f)
                                        .height(2.dp)
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(Color.Transparent, OmniGetDesignTokens.Accent.copy(alpha = 0.5f), Color.Transparent)
                                            )
                                        )
                                )

                                // Prophetic Hadith Box
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .glass(GlassMaterial.REGULAR, RoundedCornerShape(16.dp))
                                        .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "تذكير بحديث المصطفى ﷺ:",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = OmniGetDesignTokens.Accent
                                    )
                                    
                                    Text(
                                        text = state.hadith,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontStyle = FontStyle.Normal,
                                            lineHeight = 22.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = OmniGetDesignTokens.TextPrimary.copy(alpha = 0.9f),
                                        textAlign = TextAlign.Center
                                    )
                                }

                                // Supplication / Prayer Box
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Lightbulb,
                                        contentDescription = null,
                                        tint = OmniGetDesignTokens.Accent.copy(alpha = 0.7f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    
                                    Text(
                                        text = state.prayer,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            lineHeight = 18.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = OmniGetDesignTokens.TextSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                    is SearchUiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = state.message, color = OmniGetDesignTokens.DangerText, textAlign = TextAlign.Center)
                        }
                    }
                    is SearchUiState.Idle -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = "ابدأ البحث من الأعلى", color = OmniGetDesignTokens.TextSecondary)
                        }
                    }
                }
            }
        }

        // Premium Custom Bottom Sheet (البطاقة السفلية) slide-up layout matching Screenshot 2 instructions
        val isReadyForDownload = resolveState is StreamResolveState.Ready && (resolveState as StreamResolveState.Ready).forDownload
        
        AnimatedVisibility(
            visible = isReadyForDownload,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable { searchViewModel.resetResolveState() }
            )
        }

        AnimatedVisibility(
            visible = isReadyForDownload,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            if (resolveState is StreamResolveState.Ready) {
                val streamInfo = (resolveState as StreamResolveState.Ready).info
                DownloadOptionsSheetContent(
                    info = streamInfo,
                    onDismiss = { searchViewModel.resetResolveState() },
                    onDownloadSelected = { isAudio ->
                        val urlToDownload = if (streamInfo.videoId.startsWith("http")) streamInfo.videoId else "https://www.youtube.com/watch?v=${streamInfo.videoId}"
                        viewModel.downloadUrl(
                            title = streamInfo.title,
                            url = urlToDownload,
                            isVideo = !isAudio,
                            isAudioOnly = isAudio,
                            thumbnailUrl = streamInfo.thumbnailUrl
                        )
                        searchViewModel.resetResolveState()
                        Toast.makeText(context, "بدأ تحميل: ${streamInfo.title}", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }
}

@Composable
fun SearchResultItem(
    result: SearchResult,
    onDownloadClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glass(GlassMaterial.REGULAR, RoundedCornerShape(16.dp))
            .clickable { onPlayClick() }
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail container on the right (1st child in RTL Row)
            Box(
                modifier = Modifier
                    .size(120.dp, 72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = result.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                if (result.durationSeconds > 0) {
                    val hrs = result.durationSeconds / 3600
                    val mins = (result.durationSeconds % 3600) / 60
                    val secs = result.durationSeconds % 60
                    val durationStr = if (hrs > 0) {
                        String.format("%d:%02d:%02d", hrs, mins, secs)
                    } else {
                        String.format("%d:%02d", mins, secs)
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = durationStr,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Text/Details in the middle (2nd child in RTL Row)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            ) {
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        lineHeight = 20.sp
                    ),
                    color = OmniGetDesignTokens.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = result.uploaderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = OmniGetDesignTokens.TextSecondary
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                val viewsFormatted = if (result.viewCount > 1000000) {
                    "${result.viewCount / 1000000}M"
                } else if (result.viewCount > 1000) {
                    "${result.viewCount / 1000}K"
                } else {
                    "${result.viewCount}"
                }
                Text(
                    text = "مشاهدة $viewsFormatted",
                    style = MaterialTheme.typography.labelSmall,
                    color = OmniGetDesignTokens.TextSecondary.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Download button on the far left without circle background
            IconButton(
                onClick = { onDownloadClick() },
                modifier = Modifier
                    .align(Alignment.Bottom)
                    .size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = "تحميل",
                    tint = OmniGetDesignTokens.Accent, // Brand Accent color directly on glass card
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SearchWebView(url: String, viewModel: DownloadViewModel) {
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            return false
                        }
                    }
                    loadUrl(url)
                }
            },
            update = { webView ->
                if (webView.url != url) {
                    webView.loadUrl(url)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun DownloadOptionsSheetContent(
    info: StreamInfo,
    onDismiss: () -> Unit,
    onDownloadSelected: (isAudio: Boolean) -> Unit
) {
    var selectedFormat by remember { mutableStateOf("mp4") }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        color = OmniGetDesignTokens.Surface1,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Drag Handle / Indicator
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(5.dp)
                    .background(OmniGetDesignTokens.TextSecondary.copy(alpha = 0.5f), RoundedCornerShape(99.dp))
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Sheet Header Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "تنزيل الوسائط",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.End
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Media Info Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp, 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                ) {
                    AsyncImage(
                        model = info.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = info.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = info.uploader,
                        style = MaterialTheme.typography.labelSmall,
                        color = OmniGetDesignTokens.TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = Color(0x14FFFFFF))
            Spacer(modifier = Modifier.height(16.dp))

            // Audio Section Title (Changed from Music to Sound Clip)
            Text(
                text = "مقطع صوتي",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = OmniGetDesignTokens.TextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                textAlign = TextAlign.Right
            )

            SearchFormatRow(
                title = "MP3 كلاسيكي",
                isSelected = selectedFormat == "mp3",
                icon = Icons.Rounded.MusicNote,
                onClick = { selectedFormat = "mp3" }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Video Section Title
            Text(
                text = "فيديو",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = OmniGetDesignTokens.TextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                textAlign = TextAlign.Right
            )

            SearchFormatRow(
                title = "MP4 بجودة عالية",
                isSelected = selectedFormat == "mp4",
                icon = Icons.Rounded.PlayCircle,
                onClick = { selectedFormat = "mp4" }
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Solid Gradient Download Button with brand accent and fully circular edges (No Cancel Button)
            Button(
                onClick = {
                    val isAudioOnly = selectedFormat == "mp3"
                    onDownloadSelected(isAudioOnly)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.Black
                ),
                contentPadding = PaddingValues()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(OmniGetDesignTokens.Accent, OmniGetDesignTokens.AccentPressed)
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "تنزيل",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                        color = Color.Black
                    )
                }
            }
        }
    }
}

@Composable
fun SearchFormatRow(
    title: String,
    isSelected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(CircleShape)
            .background(if (isSelected) OmniGetDesignTokens.Surface2 else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (isSelected) OmniGetDesignTokens.Accent else Color(0x12FFFFFF),
                shape = CircleShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Radio selection indicator on the far left
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(1.5.dp, if (isSelected) OmniGetDesignTokens.Accent else OmniGetDesignTokens.TextMuted, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(OmniGetDesignTokens.Accent)
                )
            }
        }

        // Title and icon on the far right
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) OmniGetDesignTokens.TextPrimary else OmniGetDesignTokens.TextSecondary
                )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) OmniGetDesignTokens.Accent else OmniGetDesignTokens.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

