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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.ui.theme.CG
import com.example.ui.theme.glassSurface
import com.example.ui.theme.liquidGlass
import com.example.ui.theme.GlassMath
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
                val activePlayer = player
                if (activePlayer != null && activePlayer.currentMediaItem?.localConfiguration?.uri != mediaUri) {
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
                .height(110.dp)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.40f), Color.Transparent)))
        )
        if (currentKind != DownloadedMediaKind.IMAGE) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f))))
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            // LTR wrapper guarantees Close button is physically on the LEFT and Title is on the RIGHT
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp, start = 16.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 1. Physical Left side: Exit button & optional Zoom button (exact 42.dp height matching title)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GalleryRoundButton(
                            icon = Icons.Rounded.Close,
                            description = "إغلاق المعرض",
                            onClick = onClose
                        )
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

                    // 2. Physical Right side: Single-line Title Capsule with full circular pill ends and exact 42.dp height
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Box(
                            modifier = Modifier
                                .height(42.dp)
                                .width(160.dp)
                                .liquidGlass(
                                    fillColor = Color(0xFF000000),
                                    washAlpha = 0.68f,
                                    capsule = true,
                                    radius = 21.dp,
                                    borderColor = Color.White.copy(alpha = 0.16f),
                                    borderWidth = 0.8.dp,
                                    rimLightAlpha = 0.45f,
                                    pressFeedback = false
                                )
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currentItem.title,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // 3. Bottom Playback Bar: Only in Video/Audio mode, completely hidden in Photo mode
        if (currentKind != DownloadedMediaKind.IMAGE) {
            AnimatedVisibility(
                visible = controlsVisible,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                ModernVideoPlaybackBar(
                    isPlaying = isPlaying,
                    progress = progress,
                    player = player,
                    onPlayPause = { if (isPlaying) player?.pause() else player?.play() },
                    onSeek = { fraction ->
                        if (durationMs > 0L) playerViewModel.seekTo((durationMs * fraction).toLong())
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
internal fun ModernVideoPlaybackBar(
    isPlaying: Boolean,
    progress: Float,
    player: Player?,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit
) {
    var isMuted by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 22.dp)
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Speaker / Mute icon on the Left
                IconButton(
                    onClick = {
                        val p = player ?: return@IconButton
                        if (isMuted) {
                            p.volume = 1f
                            isMuted = false
                        } else {
                            p.volume = 0f
                            isMuted = true
                        }
                    },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = if (isMuted) "إلغاء كتم الصوت" else "كتم الصوت",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(Modifier.width(16.dp))

                // 2. Custom Sleek Progress Track matching Screenshot_٢٠٢٦١٠١٤_١٤٢١٠٠.jpg
                CoveModernVideoProgressBar(
                    progress = progress,
                    onSeek = onSeek,
                    modifier = Modifier.weight(1f)
                )

                Spacer(Modifier.width(16.dp))

                // 3. Play / Pause Button on the Right
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "إيقاف مؤقت" else "تشغيل",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CoveModernVideoProgressBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableStateOf(progress) }
    val currentProgress = if (isDragging) dragProgress else progress.coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = modifier
            .height(36.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (size.width > 0) {
                        val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onSeek(fraction)
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        if (size.width > 0) {
                            dragProgress = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        }
                    },
                    onDragEnd = {
                        isDragging = false
                        onSeek(dragProgress)
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        if (size.width > 0) {
                            val fraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            dragProgress = fraction
                            onSeek(fraction)
                        }
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val totalWidth = maxWidth
        // 1. Dark translucent pill background track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(11.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f))
        )

        // 2. Solid white active pill track
        if (currentProgress > 0f) {
            Box(
                modifier = Modifier
                    .width(totalWidth * currentProgress)
                    .height(11.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

@Composable
internal fun GalleryRoundButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .liquidGlass(
                fillColor = Color(0xFF000000),
                washAlpha = 0.68f,
                capsule = true,
                radius = 21.dp,
                borderColor = Color.White.copy(alpha = 0.18f),
                borderWidth = 0.8.dp,
                rimLightAlpha = 0.50f,
                pressFeedback = true
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}
