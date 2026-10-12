package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.*
import com.example.domain.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import java.util.regex.Pattern

data class DownloadRequest(
    val title: String,
    val url: String,
    val isVideo: Boolean,
    val isAudioOnly: Boolean,
    val thumbnailUrl: String? = null
)

class ShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedText = intent?.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        val url = extractUrl(sharedText)

        if (url == null) {
            Toast.makeText(this, "الرابط المشترك غير صالح!", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContent {
            CoveTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    var showBottomSheet by remember { mutableStateOf(true) }
                    var showStartedDialog by remember { mutableStateOf(false) }

                    Box(modifier = Modifier.fillMaxSize()) {
                        if (showBottomSheet) {
                            ShareBottomSheetFlow(
                                url = url,
                                onDismiss = { finish() },
                                onStartDownloadList = { requests ->
                                    requests.forEach { req ->
                                        triggerDownload(req)
                                    }
                                    showBottomSheet = false
                                    showStartedDialog = true
                                }
                            )
                        }

                        if (showStartedDialog) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .clickable(enabled = true, onClick = {}, onClickLabel = null, role = null),
                                contentAlignment = Alignment.Center
                            ) {
                                Card(
                                    shape = RoundedCornerShape(32.dp),
                                    colors = CardDefaults.cardColors(containerColor = CG.Void),
                                    modifier = Modifier
                                        .width(280.dp)
                                        .padding(16.dp),
                                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
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
                                                tint = Color.White,
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
                                            Button(
                                                onClick = {
                                                    val intent = Intent(this@ShareActivity, MainActivity::class.java).apply {
                                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                                        putExtra("tab", "HISTORY")
                                                    }
                                                    startActivity(intent)
                                                    finish()
                                                },
                                                shape = RoundedCornerShape(20.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = AccentSkyBlue,
                                                    contentColor = Color.White
                                                ),
                                                modifier = Modifier.weight(1f).height(38.dp),
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text(
                                                    text = "عرض",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }

                                            Button(
                                                onClick = {
                                                    finish()
                                                },
                                                shape = RoundedCornerShape(20.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color.White.copy(alpha = 0.08f),
                                                    contentColor = Color.White
                                                ),
                                                modifier = Modifier.weight(1f).height(38.dp),
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

    private fun triggerDownload(req: DownloadRequest) {
        // Route shared links through the same background pipeline as in-app downloads,
        // so they keep running after this sheet is dismissed.
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val downloadRepository = com.example.api.NetworkModule.downloadRepository
                val downloadId = com.example.download.DownloadScheduler.generateDownloadId()

                val item = downloadRepository.registerPendingDownload(
                    context = applicationContext,
                    downloadId = downloadId,
                    title = req.title,
                    url = req.url,
                    isVideo = req.isVideo,
                    isAudioOnly = req.isAudioOnly,
                    thumbnailUrl = req.thumbnailUrl
                )

                com.example.download.DownloadScheduler.enqueue(
                    context = applicationContext,
                    downloadId = downloadId,
                    title = req.title,
                    url = req.url,
                    isVideo = req.isVideo,
                    isAudioOnly = req.isAudioOnly,
                    thumbnailUrl = req.thumbnailUrl,
                    outputPath = item.localUri
                )
            } catch (e: Exception) {
                Log.e("ShareActivity", "Download enqueue failed for url: ${req.url}", e)
            }
        }
    }
}

sealed interface ShareScrapingState {
    object Loading : ShareScrapingState
    data class Success(val media: ScrapedMedia) : ShareScrapingState
    data class Error(val message: String) : ShareScrapingState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareBottomSheetFlow(
    url: String,
    onDismiss: () -> Unit,
    onStartDownloadList: (List<DownloadRequest>) -> Unit
) {
    var state by remember { mutableStateOf<ShareScrapingState>(ShareScrapingState.Loading) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    val downloadRepository = remember { com.example.api.NetworkModule.downloadRepository }

    // Trigger scraping on launch
    LaunchedEffect(url) {
        try {
            val scraped = downloadRepository.fetchMediaInfo(url)
            state = ShareScrapingState.Success(scraped)
        } catch (e: Exception) {
            state = ShareScrapingState.Error("حدث خطأ أثناء فك شفرة الرابط: ${e.localizedMessage}")
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OledSurface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = SilverZinc.copy(alpha = 0.5f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Title aligned right (RTL design)
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

            // Body depending on State
            when (val currentState = state) {
                is ShareScrapingState.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = AccentSkyBlue,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.analyzing_link),
                            style = MaterialTheme.typography.bodyMedium,
                            color = SilverZinc,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                is ShareScrapingState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = ObsidianError,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = currentState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ObsidianError,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
                is ShareScrapingState.Success -> {
                    val media = currentState.media
                    val hasCarousel = media.items.size > 1

                    if (hasCarousel) {
                        // Carousel Multi-selection Grid (Screenshot 2 style)
                        var selectedUrls by remember { mutableStateOf(media.items.map { it.url }.toSet()) }
                        val selectAll = selectedUrls.size == media.items.size

                        // Select All row (aligned to right in RTL)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedUrls = if (selectAll) {
                                        emptySet()
                                    } else {
                                        media.items.map { it.url }.toSet()
                                    }
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "تم تحديد الكل",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(if (selectAll) AccentSkyBlue else Color.Transparent)
                                    .border(2.dp, if (selectAll) AccentSkyBlue else SilverZinc, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectAll) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Grid of items
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val chunkedItems = media.items.chunked(3)
                            chunkedItems.forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowItems.forEach { item ->
                                        val isSelected = selectedUrls.contains(item.url)
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(Color.White.copy(alpha = 0.05f))
                                                .border(
                                                    width = 0.5.dp,
                                                    color = if (isSelected) AccentSkyBlue else Color.White.copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(16.dp)
                                                )
                                                .clickable {
                                                    selectedUrls = if (isSelected) {
                                                        selectedUrls - item.url
                                                    } else {
                                                        selectedUrls + item.url
                                                    }
                                                }
                                        ) {
                                            // Thumbnail Image
                                            if (item.thumbnailUrl != null) {
                                                AsyncImage(
                                                    model = item.thumbnailUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(Color.White.copy(alpha = 0.05f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = if (item.isVideo) Icons.Default.PlayCircleFilled else Icons.Default.Image,
                                                        contentDescription = null,
                                                        tint = SilverZinc,
                                                        modifier = Modifier.size(32.dp)
                                                    )
                                                }
                                            }

                                            // Checkbox indicator top right
                                            Box(
                                                modifier = Modifier
                                                    .padding(8.dp)
                                                    .size(22.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) AccentSkyBlue else Color.Black.copy(alpha = 0.4f))
                                                    .border(1.5.dp, if (isSelected) AccentSkyBlue else Color.White.copy(alpha = 0.6f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = Color.Black,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }

                                            // Video Play indicator bottom left
                                            if (item.isVideo) {
                                                Box(
                                                    modifier = Modifier
                                                        .padding(8.dp)
                                                        .size(24.dp)
                                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                                        .align(Alignment.BottomStart),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    // Empty padding if not a complete row of 3
                                    val emptySpaces = 3 - rowItems.size
                                    if (emptySpaces > 0) {
                                        repeat(emptySpaces) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Solid Gradient Download Button with brand accent and fully circular edges (Matching internal design)
                        Button(
                            onClick = {
                                if (selectedUrls.isNotEmpty()) {
                                    val requests = media.items.filter { selectedUrls.contains(it.url) }.map { item ->
                                        DownloadRequest(
                                            title = media.title,
                                            url = item.url,
                                            isVideo = item.isVideo,
                                            isAudioOnly = false
                                        )
                                    }
                                    onStartDownloadList(requests)
                                }
                            },
                            enabled = selectedUrls.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                contentColor = Color.Black,
                                disabledContainerColor = Color.White.copy(alpha = 0.1f),
                                disabledContentColor = SilverZinc.copy(alpha = 0.5f)
                            ),
                            contentPadding = PaddingValues()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        brush = if (selectedUrls.isNotEmpty()) Brush.verticalGradient(
                                            colors = listOf(OmniGetDesignTokens.Accent, OmniGetDesignTokens.AccentPressed)
                                        ) else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "تنزيل",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                                    color = if (selectedUrls.isNotEmpty()) Color.Black else SilverZinc.copy(alpha = 0.5f)
                                )
                            }
                        }

                    } else {
                        // Single Media format selector
                        val isVideo = media.isVideo
                        var selectedFormat by remember { mutableStateOf(if (isVideo) "mp4" else "image") }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.Start
                        ) {
                            if (isVideo) {
                                // Music format section
                                Text(
                                    text = "مقطع صوتي",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = SilverZinc,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )

                                FormatRow(
                                    title = "MP3 كلاسيكي",
                                    isSelected = selectedFormat == "mp3",
                                    icon = Icons.Default.MusicNote,
                                    onClick = { selectedFormat = "mp3" }
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Video format section
                                Text(
                                    text = "فيديو",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = SilverZinc,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )

                                FormatRow(
                                    title = "MP4 بجودة عالية",
                                    isSelected = selectedFormat == "mp4",
                                    icon = Icons.Default.PlayCircle,
                                    onClick = { selectedFormat = "mp4" }
                                )
                            } else {
                                // Image format section
                                Text(
                                    text = "الصور",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = SilverZinc,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )

                                FormatRow(
                                    title = "تحميل الصورة بدقة كاملة",
                                    isSelected = selectedFormat == "image",
                                    icon = Icons.Default.Image,
                                    onClick = { selectedFormat = "image" }
                                )
                            }

                            Spacer(modifier = Modifier.height(28.dp))

                            // Large Solid Pure CTA Download Button with brand accent and fully circular edges (Matching internal design)
                            Button(
                                onClick = {
                                    val isAudioOnly = selectedFormat == "mp3"
                                    val req = DownloadRequest(
                                        title = media.title,
                                        url = media.mediaUrl,
                                        isVideo = isVideo && !isAudioOnly,
                                        isAudioOnly = isAudioOnly,
                                        thumbnailUrl = media.thumbnailUrl
                                    )
                                    onStartDownloadList(listOf(req))
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
            }
        }
    }
}

@Composable
fun FormatRow(
    title: String,
    isSelected: Boolean,
    icon: ImageVector,
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
