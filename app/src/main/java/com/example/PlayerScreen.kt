package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.domain.StreamInfo
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun rememberMediaThumbnail(localUri: String?): Any? {
    if (localUri == null) return null
    val context = LocalContext.current
    return produceState<Any?>(initialValue = null, localUri) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(localUri)
                if (file.exists()) {
                    if (localUri.endsWith(".mp3", ignoreCase = true) || localUri.endsWith(".m4a", ignoreCase = true)) {
                        val retriever = android.media.MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(localUri)
                            val art = retriever.embeddedPicture
                            if (art != null) {
                                value = art
                            } else {
                                val companionThumb = File(file.parentFile, file.nameWithoutExtension + ".thumb.jpg")
                                if (companionThumb.exists()) {
                                    value = companionThumb
                                } else {
                                    value = null
                                }
                            }
                        } catch (e: Exception) {
                            val companionThumb = File(file.parentFile, file.nameWithoutExtension + ".thumb.jpg")
                            value = if (companionThumb.exists()) companionThumb else null
                        } finally {
                            retriever.release()
                        }
                    } else if (localUri.endsWith(".mp4", ignoreCase = true) || localUri.endsWith(".mkv", ignoreCase = true)) {
                        val retriever = android.media.MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(localUri)
                            val bitmap = retriever.getFrameAtTime(1000000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            value = bitmap
                        } catch (e: Exception) {
                            value = null
                        } finally {
                            retriever.release()
                        }
                    } else {
                        value = file
                    }
                } else {
                    value = null
                }
            } catch (e: Exception) {
                value = null
            }
        }
    }.value
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

@Composable
fun MiniPlayer(
    viewModel: PlayerViewModel,
    onClick: () -> Unit
) {
    val currentMediaItem by viewModel.currentMediaItem.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val player by viewModel.player.collectAsState()

    AnimatedVisibility(
        visible = currentMediaItem != null,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(CircleShape)
                .background(Color(0xFF0D111A))
                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                .clickable { onClick() }
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val artworkUri = currentMediaItem?.mediaMetadata?.artworkUri?.toString()
            val isLocal = artworkUri?.startsWith("/") == true || artworkUri?.startsWith("file://") == true
            val mediaThumb = if (isLocal) rememberMediaThumbnail(artworkUri) else artworkUri

            AsyncImage(
                model = mediaThumb ?: currentMediaItem?.mediaMetadata?.artworkUri,
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
                error = painterResource(id = android.R.drawable.ic_media_play)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = currentMediaItem?.mediaMetadata?.title?.toString() ?: "Unknown",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { if (isPlaying) player?.pause() else player?.play() }) {
                Icon(imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, tint = CG.CobaltGlow)
            }
            IconButton(onClick = { player?.stop(); player?.clearMediaItems() }) {
                Icon(imageVector = Icons.Rounded.Close, contentDescription = null, tint = CG.TextMuted)
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerScreen(
    viewModel: PlayerViewModel,
    downloadViewModel: DownloadViewModel,
    onClose: () -> Unit
) {
    val currentMediaItem by viewModel.currentMediaItem.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val player by viewModel.player.collectAsState()
    
    val queue by viewModel.playQueue.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val context = LocalContext.current

    val currentQueueItem = remember(queue, currentIndex) {
        if (currentIndex in queue.indices) queue[currentIndex] else null
    }

    val isLocal = currentQueueItem?.isLocal == true
    val artworkUri = currentMediaItem?.mediaMetadata?.artworkUri?.toString()
    val mediaThumb = if (isLocal) rememberMediaThumbnail(artworkUri ?: currentQueueItem?.url) else (artworkUri ?: currentQueueItem?.thumbnailUrl)

    var showOverlayControls by remember { mutableStateOf(true) }
    var showDownloadSheet by remember { mutableStateOf(false) }

    // Auto-hide controls after 3 seconds if media is playing
    LaunchedEffect(showOverlayControls, isPlaying) {
        if (showOverlayControls && isPlaying) {
            delay(3500)
            showOverlayControls = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CG.Void) // Ultra premium deep black canvas
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // 1. Fixed Video Player / Album Art Area on top (Snaptube Style)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        showOverlayControls = !showOverlayControls
                    }
            ) {
                // Render true ExoPlayer Video texture if playing online or local MP4 video
                val isVideo = remember(currentQueueItem, currentMediaItem) {
                    val url = currentQueueItem?.url ?: ""
                    currentQueueItem?.isLocal == false || 
                    url.endsWith(".mp4", ignoreCase = true) == true ||
                    url.endsWith(".mkv", ignoreCase = true) == true
                }

                if (isVideo && player != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = player
                                this.useController = false // Custom overlay instead
                                this.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        },
                        update = { view ->
                            view.player = player
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Audio-only album art styling with slow vinyl rotating rotation
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(CG.Cobalt, CG.Void)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val rotationTransition = rememberInfiniteTransition(label = "VinylRotate")
                        val rotationAngle by rotationTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(15000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "Angle"
                        )

                        // Circular Vinyl wrapper
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .clip(CircleShape)
                                .background(CG.Elevated)
                                .border(6.dp, CG.Card, CircleShape)
                                .rotate(if (isPlaying) rotationAngle else 0f),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = mediaThumb,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop,
                                error = painterResource(id = android.R.drawable.ic_media_play)
                            )
                        }
                    }
                }

                // Smoothly animated Overlay Controls (Play/Pause, seek, back button)
                androidx.compose.animation.AnimatedVisibility(
                    visible = showOverlayControls,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        // Top bar inside overlay (Back and menu)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                                .align(Alignment.TopCenter),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onClose) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }
                            
                            IconButton(onClick = {}) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "Options",
                                    tint = Color.White
                                )
                            }
                        }

                        // Center play controls
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(28.dp)
                        ) {
                            IconButton(onClick = { viewModel.playPrevious() }) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipPrevious,
                                    contentDescription = "Previous",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            // Big circular Play / Pause
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.25f))
                                    .clickable {
                                        if (isPlaying) player?.pause() else player?.play()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            IconButton(onClick = { viewModel.playNext() }) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipNext,
                                    contentDescription = "Next",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        // Bottom real-time Seek progress bar inside overlay
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .align(Alignment.BottomCenter),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val positionMs by viewModel.playbackPosition.collectAsState()
                            val durationMs by viewModel.playbackDuration.collectAsState()
                            val progress by viewModel.playbackProgress.collectAsState()

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatTime(positionMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.8f)
                                )

                                CoveSeekBar(
                                    progress = progress,
                                    onSeek = { p ->
                                        val seekPos = (p * durationMs).toLong()
                                        viewModel.seekTo(seekPos)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 12.dp)
                                )

                                Text(
                                    text = formatTime(durationMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Scrollable details column below fixed video player
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title and Creator details section
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = currentMediaItem?.mediaMetadata?.title?.toString() ?: "Cove Player",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, lineHeight = 22.sp),
                        color = Color.White,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isLocal) "من ملفاتي المحملة" else "مشاهدة مباشرة • ${currentQueueItem?.viewCountText ?: "غير معروف"} مشاهدة",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Big "Download" and Quick Action Buttons with branding color
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Download pill matching app's brand accent color
                    Button(
                        onClick = {
                            if (isLocal) {
                                Toast.makeText(context, "هذا الملف محمل بالفعل على جهازك! 📂", Toast.LENGTH_SHORT).show()
                            } else {
                                showDownloadSheet = true
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(99.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OmniGetDesignTokens.Accent, // Brand Accent Color
                            contentColor = Color.White
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isLocal) "محمل بنجاح" else "تحميل الوسائط",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    // Background Audio play toggle
                    IconButton(
                        onClick = {
                            Toast.makeText(context, "تم تشغيل وضع الاستماع بالخلفية! يمكنك إغلاق التطبيق الآن.", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(CG.Card)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Headphones,
                            contentDescription = "Listen Background",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Share button
                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, currentQueueItem?.url ?: "")
                            }
                            context.startActivity(Intent.createChooser(intent, "مشاركة الرابط"))
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(CG.Card)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Share",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Recommendations list "التالي" (Next up queue items)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "قائمة التشغيل التالية",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )

                    if (queue.isEmpty() || queue.size <= 1) {
                        Text(
                            text = "لا توجد مقاطع تالية في القائمة",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        queue.forEachIndexed { index, item ->
                            // Don't show the currently active playing item in recommendations
                            if (index != currentIndex) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            viewModel.playQueueItem(item, queue, index)
                                        }
                                        .padding(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Thumbnail
                                    val recThumb = if (item.isLocal) rememberMediaThumbnail(item.thumbnailUrl) else item.thumbnailUrl
                                    Box(
                                        modifier = Modifier
                                            .size(90.dp, 52.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(CG.Card)
                                    ) {
                                        AsyncImage(
                                            model = recThumb,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                            error = painterResource(id = android.R.drawable.ic_media_play)
                                        )
                                    }

                                    // Metadatas
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = item.artist,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.5f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
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

    // Direct overlay popup of the Snaptube-like Download options dialog
    if (showDownloadSheet && currentQueueItem != null) {
        val streamInfo = StreamInfo(
            videoId = currentQueueItem.id,
            title = currentQueueItem.title,
            primaryPlayUrl = currentQueueItem.url,
            videoUrl = currentQueueItem.url,
            audioUrl = currentQueueItem.url,
            uploader = currentQueueItem.artist,
            thumbnailUrl = currentQueueItem.thumbnailUrl,
            durationSeconds = currentQueueItem.durationSeconds
        )
        AlertDialog(
            onDismissRequest = { showDownloadSheet = false },
            modifier = Modifier.padding(16.dp)
        ) {
            DownloadOptionsSheetContent(
                info = streamInfo,
                onDismiss = { showDownloadSheet = false },
                onDownloadSelected = { isAudioOnly ->
                    downloadViewModel.downloadUrl(
                        title = streamInfo.title,
                        url = "https://www.youtube.com/watch?v=" + streamInfo.videoId, // Full video url for yt-dlp resolver
                        isVideo = !isAudioOnly,
                        isAudioOnly = isAudioOnly
                    )
                    showDownloadSheet = false
                    Toast.makeText(context, "بدأ تنزيل المقطع الآن بنجاح! 🚀", Toast.LENGTH_LONG).show()
                }
            )
        }
    }
}

// ----------------------------------------------------------------------------
// شريط تقديم بأسلوب YouTube: مسار رفيع + جزء مُشغّل + مقبض يتمدد أثناء السحب
// ----------------------------------------------------------------------------
@Composable
private fun CoveSeekBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier
            .height(28.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        val w = size.width.toFloat()
                        onSeek((offset.x / w).coerceIn(0f, 1f))
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val w = size.width.toFloat()
                        onSeek((change.position.x / w).coerceIn(0f, 1f))
                    }
                )
            }
    ) {
        val w = constraints.maxWidth.toFloat()
        val trackH = if (dragging) 6.dp else 4.dp
        val thumbSize = if (dragging) 16.dp else 12.dp
        val thumbPx = with(density) { thumbSize.toPx() }
        val p = progress.coerceIn(0f, 1f)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(trackH)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f))
            )
            Box(
                Modifier
                    .fillMaxWidth(fraction = p)
                    .height(trackH)
                    .clip(CircleShape)
                    .background(Color.White)
            )
            Box(
                Modifier
                    .size(thumbSize)
                    .offset { IntOffset(((w - thumbPx) * p).roundToInt(), 0) }
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}
