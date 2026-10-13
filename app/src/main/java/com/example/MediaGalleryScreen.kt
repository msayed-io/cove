package com.example

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.ui.theme.CG
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

internal enum class DownloadedMediaKind(val label: String, val mimeType: String) {
    IMAGE("صورة", "image/*"),
    VIDEO("فيديو", "video/*"),
    AUDIO("صوت", "audio/*"),
    UNKNOWN("ملف", "application/octet-stream")
}

internal fun downloadedMediaKind(item: DownloadItem): DownloadedMediaKind {
    val extension = item.localUri
        ?.substringBefore('?')
        ?.substringAfterLast('.', "")
        ?.lowercase()
        .orEmpty()

    return when {
        extension in IMAGE_EXTENSIONS -> DownloadedMediaKind.IMAGE
        extension in AUDIO_EXTENSIONS -> DownloadedMediaKind.AUDIO
        extension in VIDEO_EXTENSIONS -> DownloadedMediaKind.VIDEO
        item.isVideo -> DownloadedMediaKind.VIDEO
        else -> DownloadedMediaKind.UNKNOWN
    }
}

internal fun downloadedMediaFormat(item: DownloadItem): String {
    val extension = item.localUri
        ?.substringBefore('?')
        ?.substringAfterLast('.', "")
        ?.uppercase()
        .orEmpty()
    return extension.takeIf { it.isNotBlank() } ?: when (downloadedMediaKind(item)) {
        DownloadedMediaKind.IMAGE -> "IMG"
        DownloadedMediaKind.VIDEO -> "VIDEO"
        DownloadedMediaKind.AUDIO -> "AUDIO"
        DownloadedMediaKind.UNKNOWN -> "FILE"
    }
}

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif", "heic", "heif", "tif", "tiff")
private val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "mkv", "webm", "mov", "3gp", "ts")
private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "wav", "ogg", "opus", "flac")

internal data class MediaGalleryLaunch(
    val items: List<DownloadItem>,
    val initialItemId: Long
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LocalMediaGalleryScreen(
    items: List<DownloadItem>,
    initialItemId: Long,
    playerViewModel: PlayerViewModel,
    onClose: () -> Unit,
    onCurrentItemChanged: (Long) -> Unit
) {
    val galleryItems = remember(items) {
        items.filter { item ->
            val path = item.localUri
            path != null && downloadedMediaKind(item) != DownloadedMediaKind.UNKNOWN && runCatching { File(path).isFile }.getOrDefault(false)
        }
    }
    if (galleryItems.isEmpty()) return

    val startPage = galleryItems.indexOfFirst { it.id == initialItemId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = startPage, pageCount = { galleryItems.size })
    val scope = rememberCoroutineScope()
    val currentItem = galleryItems.getOrNull(pagerState.currentPage) ?: galleryItems.first()
    val currentKind = downloadedMediaKind(currentItem)

    val player by playerViewModel.player.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val positionMs by playerViewModel.playbackPosition.collectAsState()
    val durationMs by playerViewModel.playbackDuration.collectAsState()
    val progress by playerViewModel.playbackProgress.collectAsState()

    var controlsVisible by remember(currentItem.id) { mutableStateOf(true) }
    var zoomedItemId by remember { mutableStateOf<Long?>(null) }

    BackHandler(onBack = onClose)

    LaunchedEffect(currentItem.id, player) {
        onCurrentItemChanged(currentItem.id)
        val path = currentItem.localUri ?: return@LaunchedEffect
        when (currentKind) {
            DownloadedMediaKind.VIDEO, DownloadedMediaKind.AUDIO -> {
                val mediaUri = Uri.fromFile(File(path))
                if (player != null && player.currentMediaItem?.localConfiguration?.uri != mediaUri) {
                    playerViewModel.playLocalFile(mediaUri, currentItem.title)
                }
            }
            DownloadedMediaKind.IMAGE, DownloadedMediaKind.UNKNOWN -> {
                player?.pause()
                player?.clearMediaItems()
            }
        }
        zoomedItemId = null
    }

    LaunchedEffect(currentItem.id, currentKind, isPlaying, controlsVisible) {
        if (currentKind == DownloadedMediaKind.VIDEO && isPlaying && controlsVisible) {
            delay(3_200)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CG.Void)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = zoomedItemId == null,
            key = { page -> galleryItems[page].id }
        ) { page ->
            val item = galleryItems[page]
            GalleryMediaPage(
                item = item,
                kind = downloadedMediaKind(item),
                isActive = page == pagerState.currentPage,
                isPlaying = isPlaying,
                player = player,
                isZoomed = zoomedItemId == item.id,
                onToggleZoom = {
                    zoomedItemId = if (zoomedItemId == item.id) null else item.id
                },
                onTapMedia = { controlsVisible = !controlsVisible }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.78f), Color.Transparent)))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))))
        )

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GalleryRoundButton(
                    icon = Icons.Rounded.Close,
                    description = "إغلاق المعرض",
                    onClick = onClose
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentItem.title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${currentKind.label}  •  ${pagerState.currentPage + 1} / ${galleryItems.size}",
                        color = Color.White.copy(alpha = 0.68f),
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
                if (currentKind == DownloadedMediaKind.IMAGE) {
                    GalleryRoundButton(
                        icon = if (zoomedItemId == currentItem.id) Icons.Rounded.ZoomOut else Icons.Rounded.ZoomIn,
                        description = if (zoomedItemId == currentItem.id) "تصغير الصورة" else "تكبير الصورة",
                        onClick = {
                            zoomedItemId = if (zoomedItemId == currentItem.id) null else currentItem.id
                        }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            if (currentKind == DownloadedMediaKind.IMAGE) {
                ImageGalleryFooter(
                    item = currentItem,
                    index = pagerState.currentPage,
                    total = galleryItems.size,
                    onPrevious = {
                        if (pagerState.currentPage > 0) scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    },
                    onNext = {
                        if (pagerState.currentPage < galleryItems.lastIndex) scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                )
            } else {
                PlaybackGalleryFooter(
                    item = currentItem,
                    kind = currentKind,
                    index = pagerState.currentPage,
                    total = galleryItems.size,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    progress = progress,
                    onPlayPause = { if (isPlaying) player?.pause() else player?.play() },
                    onSeek = { fraction ->
                        if (durationMs > 0L) playerViewModel.seekTo((durationMs * fraction).toLong())
                    },
                    onPrevious = {
                        if (pagerState.currentPage > 0) scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    },
                    onNext = {
                        if (pagerState.currentPage < galleryItems.lastIndex) scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                )
            }
        }
    }
}

@Composable
private fun GalleryMediaPage(
    item: DownloadItem,
    kind: DownloadedMediaKind,
    isActive: Boolean,
    isPlaying: Boolean,
    player: Player?,
    isZoomed: Boolean,
    onToggleZoom: () -> Unit,
    onTapMedia: () -> Unit
) {
    val path = item.localUri.orEmpty()
    val file = remember(path) { File(path) }
    val mediaThumbnail = if (kind == DownloadedMediaKind.VIDEO || kind == DownloadedMediaKind.AUDIO) {
        rememberMediaThumbnail(path)
    } else null
    var imagePan by remember(item.id) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(isZoomed) {
        if (!isZoomed) imagePan = Offset.Zero
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(item.id, kind, isActive, player != null) {
                if (kind != DownloadedMediaKind.VIDEO || !isActive || player == null) {
                    detectTapGestures(onTap = { onTapMedia() }, onDoubleTap = {
                        if (kind == DownloadedMediaKind.IMAGE) onToggleZoom()
                    })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        when (kind) {
            DownloadedMediaKind.IMAGE -> {
                AsyncImage(
                    model = file,
                    contentDescription = item.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (isZoomed) {
                                Modifier.pointerInput(item.id, isZoomed) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        imagePan += Offset(dragAmount.x, dragAmount.y)
                                    }
                                }
                            } else Modifier
                        )
                        .graphicsLayer {
                            scaleX = if (isZoomed) 2.15f else 1f
                            scaleY = if (isZoomed) 2.15f else 1f
                            translationX = imagePan.x
                            translationY = imagePan.y
                        },
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            }
            DownloadedMediaKind.VIDEO -> {
                if (mediaThumbnail != null) {
                    AsyncImage(
                        model = mediaThumbnail,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                }
                if (isActive && player != null) {
                    AndroidView(
                        factory = { context ->
                            PlayerView(context).apply {
                                this.player = player
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        },
                        update = { it.player = player },
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(item.id) { detectTapGestures(onTap = { onTapMedia() }) }
                    )
                } else if (mediaThumbnail == null) {
                    GalleryMediaPlaceholder(kind = kind)
                }
            }
            DownloadedMediaKind.AUDIO -> AudioGalleryArtwork(
                item = item,
                artwork = mediaThumbnail,
                isPlaying = isPlaying && isActive
            )
            DownloadedMediaKind.UNKNOWN -> GalleryMediaPlaceholder(kind = kind)
        }
    }
}

@Composable
private fun AudioGalleryArtwork(
    item: DownloadItem,
    artwork: Any?,
    isPlaying: Boolean
) {
    val vinylTransition = rememberInfiniteTransition(label = "galleryVinyl")
    val vinylRotation by vinylTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(18_000, easing = LinearEasing)),
        label = "galleryVinylRotation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(CG.Cobalt.copy(alpha = 0.55f), CG.Void, Color.Black))),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(272.dp)
                .graphicsLayer { rotationZ = if (isPlaying) vinylRotation else 0f }
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(CG.Elevated, CG.Void)))
                .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (artwork != null) {
                AsyncImage(
                    model = artwork,
                    contentDescription = "غلاف ${item.title}",
                    modifier = Modifier
                        .size(198.dp)
                        .clip(CircleShape)
                        .border(5.dp, CG.Card, CircleShape),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = CG.CobaltGlow,
                    modifier = Modifier.size(88.dp)
                )
            }
        }
    }
}

@Composable
private fun GalleryMediaPlaceholder(kind: DownloadedMediaKind) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = if (kind == DownloadedMediaKind.IMAGE) Icons.Rounded.Image else Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.72f),
            modifier = Modifier.size(72.dp)
        )
        Text("تعذر عرض هذا الملف", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ImageGalleryFooter(
    item: DownloadItem,
    index: Int,
    total: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    GalleryFooterContainer {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (total > 1) {
                IconButton(onClick = onPrevious, enabled = index > 0) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "العنصر السابق", tint = Color.White)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${downloadedMediaFormat(item)}  •  ${galleryFileSize(item.localUri)}",
                    color = Color.White.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            if (total > 1) {
                Text("${index + 1} / $total", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.labelMedium)
                IconButton(onClick = onNext, enabled = index < total - 1) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "العنصر التالي", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun PlaybackGalleryFooter(
    item: DownloadItem,
    kind: DownloadedMediaKind,
    index: Int,
    total: Int,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    progress: Float,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    GalleryFooterContainer {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${kind.label}  •  ${downloadedMediaFormat(item)}  •  ${galleryFileSize(item.localUri)}",
                        color = Color.White.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text("${index + 1} / $total", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.labelMedium)
            }

            Spacer(Modifier.height(8.dp))
            Slider(
                value = progress.coerceIn(0f, 1f),
                onValueChange = onSeek,
                enabled = durationMs > 0L,
                modifier = Modifier.fillMaxWidth().height(30.dp),
                colors = SliderDefaults.colors(
                    thumbColor = CG.CobaltGlow,
                    activeTrackColor = CG.CobaltGlow,
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTime(positionMs), color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.labelSmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IconButton(onClick = onPrevious, enabled = index > 0) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "السابق", tint = if (index > 0) Color.White else Color.White.copy(alpha = 0.3f))
                    }
                    Surface(
                        modifier = Modifier.size(54.dp).clickable(onClick = onPlayPause),
                        shape = CircleShape,
                        color = CG.CobaltGlow
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "إيقاف مؤقت" else "تشغيل",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                    IconButton(onClick = onNext, enabled = index < total - 1) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "التالي", tint = if (index < total - 1) Color.White else Color.White.copy(alpha = 0.3f))
                    }
                }
                Text(formatTime(durationMs), color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun GalleryFooterContainer(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xD9101420),
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(24.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun GalleryRoundButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.size(42.dp).clickable(onClick = onClick),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.40f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

private fun galleryFileSize(path: String?): String {
    val bytes = path?.let { runCatching { File(it).length() }.getOrDefault(0L) } ?: 0L
    if (bytes <= 0L) return "الحجم غير متاح"
    return when {
        bytes >= 1024L * 1024L * 1024L -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.0f KB", bytes / 1024.0)
    }
}
