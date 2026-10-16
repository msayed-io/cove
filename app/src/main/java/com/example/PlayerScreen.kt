package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
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
                .background(Color(0xFF000000))
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


@Composable
fun FullPlayerScreen(
    viewModel: PlayerViewModel,
    downloadViewModel: DownloadViewModel,
    onClose: () -> Unit
) {
    val currentMediaItem by viewModel.currentMediaItem.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isResolvingStream by viewModel.isResolvingStream.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val playbackError by viewModel.playbackError.collectAsState()
    val player by viewModel.player.collectAsState()
    val queue by viewModel.playQueue.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val durationMs by viewModel.playbackDuration.collectAsState()
    val progress by viewModel.playbackProgress.collectAsState()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val currentQueueItem = remember(queue, currentIndex) {
        queue.getOrNull(currentIndex)
    }
    val isLocal = currentQueueItem?.isLocal == true
    val currentTitle = currentMediaItem?.mediaMetadata?.title?.toString()
        ?.takeIf { it.isNotBlank() }
        ?: currentQueueItem?.title
        ?: "Cove Player"
    val artworkUri = currentMediaItem?.mediaMetadata?.artworkUri?.toString()
    val mediaThumbnail = if (isLocal) {
        rememberMediaThumbnail(artworkUri ?: currentQueueItem?.url)
    } else {
        artworkUri ?: currentQueueItem?.thumbnailUrl
    }
    val isVideo = currentQueueItem?.let { item ->
        !item.isLocal || item.url.endsWith(".mp4", true) || item.url.endsWith(".mkv", true) ||
            item.url.endsWith(".webm", true) || item.url.endsWith(".mov", true)
    } ?: true

    var controlsVisible by remember { mutableStateOf(true) }
    var showDownloadSheet by remember { mutableStateOf(false) }

    val closePlayer = {
        activity?.let(viewModel::restoreVideoOrientation)
        onClose()
    }

    BackHandler {
        if (showDownloadSheet) {
            showDownloadSheet = false
        } else {
            closePlayer()
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, isResolvingStream, isBuffering) {
        if (controlsVisible && isPlaying && !isResolvingStream && !isBuffering) {
            delay(3_200)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CG.Void)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ------------------------------------------------------------------
            // 1) Stage: fixed 16:9 player on top (Snaptube / YouTube layout).
            //    Uses the very same controls as the gallery video viewer.
            // ------------------------------------------------------------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { controlsVisible = !controlsVisible }
            ) {
                if (isVideo && player != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = player
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        },
                        update = { it.player = player },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(CG.Card, CG.Void, Color.Black))),
                        contentAlignment = Alignment.Center
                    ) {
                        val vinylTransition = rememberInfiniteTransition(label = "searchPlayerVinyl")
                        val vinylRotation by vinylTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(tween(18_000, easing = LinearEasing)),
                            label = "searchPlayerVinylRotation"
                        )
                        Box(
                            modifier = Modifier
                                .size(132.dp)
                                .graphicsLayer { rotationZ = if (isPlaying) vinylRotation else 0f }
                                .clip(CircleShape)
                                .background(Brush.radialGradient(listOf(CG.Elevated, CG.Void)))
                                .border(1.dp, CG.Border12, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = mediaThumbnail,
                                contentDescription = currentTitle,
                                modifier = Modifier
                                    .size(94.dp)
                                    .clip(CircleShape)
                                    .border(4.dp, CG.Card, CircleShape),
                                contentScale = ContentScale.Crop,
                                error = painterResource(id = android.R.drawable.ic_media_play)
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = controlsVisible,
                    modifier = Modifier.fillMaxSize(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(94.dp)
                                .align(Alignment.TopCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Black.copy(alpha = 0.60f), Color.Transparent)
                                    )
                                )
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(116.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f))
                                    )
                                )
                        )

                        // Exit on the physical left, rotate on the physical right — gallery parity.
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                GalleryRoundButton(
                                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                                    description = "إغلاق المشغل",
                                    onClick = closePlayer
                                )
                                GalleryRoundButton(
                                    icon = Icons.Rounded.ScreenRotation,
                                    description = if (isLandscape) "التبديل إلى الوضع الطولي" else "التبديل إلى الوضع العرضي",
                                    onClick = { activity?.let { viewModel.toggleVideoOrientation(it, isLandscape) } }
                                )
                            }
                        }

                        if (queue.size > 1) {
                            Row(
                                modifier = Modifier.align(Alignment.Center),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(28.dp)
                            ) {
                                GalleryRoundButton(
                                    icon = Icons.Rounded.SkipPrevious,
                                    description = "المقطع السابق",
                                    onClick = { viewModel.playPrevious() }
                                )
                                GalleryRoundButton(
                                    icon = Icons.Rounded.SkipNext,
                                    description = "المقطع التالي",
                                    onClick = { viewModel.playNext() }
                                )
                            }
                        }

                        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                            ModernVideoPlaybackBar(
                                isPlaying = isPlaying,
                                progress = progress,
                                player = player,
                                onPlayPause = { if (isPlaying) player?.pause() else player?.play() },
                                onSeek = { fraction ->
                                    if (durationMs > 0L) viewModel.seekTo((durationMs * fraction).toLong())
                                },
                                withNavigationBarPadding = false
                            )
                        }
                    }
                }

                if (isResolvingStream || isBuffering) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.Black.copy(alpha = 0.78f))
                            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = CG.Platinum,
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = if (isResolvingStream) "جاري تجهيز البث..." else "جاري تحميل الفيديو...",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // ------------------------------------------------------------------
            // 2) Scrollable details below the fixed player (title always visible,
            //    plain text under the video — never inside a capsule).
            // ------------------------------------------------------------------
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 14.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = currentTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        lineHeight = 22.sp
                    ),
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                val viewsText = currentQueueItem?.viewCountText.orEmpty().trim()
                val metaText = when {
                    isLocal -> "من ملفاتي المحملة"
                    viewsText.isNotEmpty() -> "مشاهدة مباشرة • $viewsText مشاهدة"
                    else -> "مشاهدة مباشرة"
                }
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.55f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            if (isLocal) {
                                Toast.makeText(context, "هذا الملف محمل بالفعل على جهازك!", Toast.LENGTH_SHORT).show()
                            } else {
                                showDownloadSheet = true
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(99.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CG.Platinum,
                            contentColor = CG.Void
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                tint = CG.Void,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = if (isLocal) "محمل بالفعل" else "تحميل الوسائط",
                                color = CG.Void,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    GalleryRoundButton(
                        icon = Icons.Rounded.Headphones,
                        description = "الاستماع في الخلفية",
                        onClick = {
                            Toast.makeText(
                                context,
                                "تم تشغيل وضع الاستماع بالخلفية! يمكنك إغلاق التطبيق الآن.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )

                    GalleryRoundButton(
                        icon = Icons.Rounded.Share,
                        description = "مشاركة المقطع",
                        onClick = {
                            val source = currentQueueItem?.id.orEmpty()
                            val shareUrl = if (source.startsWith("http", ignoreCase = true)) {
                                source
                            } else {
                                "https://www.youtube.com/watch?v=$source"
                            }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareUrl)
                            }
                            context.startActivity(Intent.createChooser(intent, "مشاركة الرابط"))
                        }
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // "قائمة التشغيل التالية" — cards identical to the search-result cards.
                Text(
                    text = "قائمة التشغيل التالية",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )

                val upNext = queue.mapIndexedNotNull { index, item ->
                    if (index == currentIndex) null else index to item
                }
                if (upNext.isEmpty()) {
                    Text(
                        text = "لا توجد مقاطع تالية في القائمة",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                } else {
                    upNext.forEach { (index, item) ->
                        PlayerUpNextCard(
                            item = item,
                            onClick = { viewModel.playQueueItem(item, queue, index) }
                        )
                    }
                }
            }
        }

        playbackError?.let { error ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(CG.Elevated.copy(alpha = 0.96f))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "تعذر تشغيل المقطع",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = error,
                    color = CG.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = { viewModel.retryCurrentQueueItem() },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = CG.Platinum, contentColor = CG.Void)
                ) {
                    Text("إعادة المحاولة", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (currentQueueItem != null && showDownloadSheet) {
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
            CoveDownloadOptionsSheet(
                info = streamInfo,
                onDismiss = { showDownloadSheet = false },
                onDownloadSelected = { isAudioOnly ->
                    val source = currentQueueItem.id
                    val downloadUrl = if (source.startsWith("http", ignoreCase = true)) {
                        source
                    } else {
                        "https://www.youtube.com/watch?v=$source"
                    }
                    downloadViewModel.downloadUrl(
                        title = streamInfo.title,
                        url = downloadUrl,
                        isVideo = !isAudioOnly,
                        isAudioOnly = isAudioOnly,
                        thumbnailUrl = currentQueueItem.thumbnailUrl
                    )
                    showDownloadSheet = false
                    Toast.makeText(context, "بدأ تنزيل المقطع الآن بنجاح!", Toast.LENGTH_LONG).show()
                }
            )
        }
    }
}

/**
 * "التالية" row inside the player — a 1:1 match of the search-result card
 * (`SearchResultItem`) so both surfaces share one card language.
 */
@Composable
private fun PlayerUpNextCard(
    item: PlayableItem,
    onClick: () -> Unit
) {
    val thumbnail = if (item.isLocal) {
        rememberMediaThumbnail(item.thumbnailUrl.ifBlank { item.url })
    } else {
        item.thumbnailUrl
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glass(GlassMaterial.REGULAR, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp, 72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = thumbnail,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = painterResource(id = android.R.drawable.ic_media_play)
                )

                if (item.durationSeconds > 0) {
                    val hrs = item.durationSeconds / 3600
                    val mins = (item.durationSeconds % 3600) / 60
                    val secs = item.durationSeconds % 60
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
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            ) {
                Text(
                    text = item.title,
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
                    text = item.artist,
                    style = MaterialTheme.typography.labelSmall,
                    color = OmniGetDesignTokens.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
