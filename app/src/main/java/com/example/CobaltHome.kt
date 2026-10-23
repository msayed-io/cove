package com.example

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.download.DownloadFormat
import com.example.ui.theme.CG
import com.example.ui.theme.CobaltAtmosphericBackground
import com.example.ui.theme.GlassMaterial
import com.example.ui.theme.glass
import com.example.ui.theme.pressClickEffect
import com.example.ui.theme.CoveArabic
import com.example.ui.theme.CoveBrand
import com.example.ui.theme.ArabicHeaviest
import com.example.ui.theme.glassSurface
import com.example.ui.theme.liquidGlass
import com.example.ui.theme.GlassMath

// ============================================================================
// Cove — Cobalt Glass Home (S3)
// يطابق المرجع (Image_2_(3).html + الصورة) حرفيًا، ويعيد استخدام منطق التطبيق
// القائم (ViewModels) دون كتابة منطق تنزيل/فحص جديد.
// التخطيط: عمود قابل للتمرير بارتفاع أدنى = المساحة المتاحة، وتوزيع
// SpaceBetween بين الكتل الخمس (4.1)، والدوك طبقة ثابتة (لا تتمرر).
// ============================================================================

enum class QuickMode { UHD, MP3 }

@Composable
fun CobaltHome(
    viewModel: DownloadViewModel,
    searchViewModel: SearchViewModel,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onShowStarted: (Boolean) -> Unit,
    onSearchQuery: (String) -> Unit,
) {
    val scrapingState by viewModel.scrapingState.collectAsState()
    val searchSuggestions by searchViewModel.suggestions.collectAsState()
    val suggestionsActive by searchViewModel.searchSuggestionsActive.collectAsState()
    val suggestionsLoading by searchViewModel.suggestionsLoading.collectAsState()
    var quickMode by remember { mutableStateOf(QuickMode.UHD) }
    var pasteToast by remember { mutableStateOf(false) }
    var emptyToast by remember { mutableStateOf(false) }
    var showTerms by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    var searchFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current

    // توست اللصق يختفي بعد ثانيتين (مطابق لسلوك HTML).
    LaunchedEffect(pasteToast) {
        if (pasteToast) {
            kotlinx.coroutines.delay(2000)
            pasteToast = false
        }
    }
    LaunchedEffect(emptyToast) {
        if (emptyToast) {
            kotlinx.coroutines.delay(2000)
            emptyToast = false
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val availableHeight = maxHeight
        val scrollState = rememberScrollState()

        // المحتوى القابل للتمرير موزع بتناسق رأسي كامل
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp)
                .padding(bottom = 112.dp)
                .defaultMinSize(minHeight = (availableHeight - 112.dp).coerceAtLeast(0.dp)),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1) الترويسة في أعلى الصفحة
            CobaltTopBar()

            // 2) منتصف الصفحة تمامًا: كتلة الهيرو (النص) وخانة البحث والنتائج
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 2) الهيرو (النصوص والعناوين)
                CobaltHero()
                Spacer(Modifier.height(18.dp))

                // 3) شريط البحث بتصميم الكبسولة الزجاجية
                CobaltSearchBar(
                    value = viewModel.inputUrl,
                    suggestions = searchSuggestions,
                    suggestionsActive = suggestionsActive,
                    suggestionsLoading = suggestionsLoading,
                    searchFocused = searchFocused,
                    onSearchFocusChanged = { searchFocused = it },
                    onValueChange = {
                        viewModel.setUrl(it)
                        val trimmed = it.trim()
                        if (trimmed.isNotEmpty() && android.util.Patterns.WEB_URL.matcher(trimmed).matches()) {
                            searchViewModel.onQueryChange("", fetchAutocomplete = false)
                        } else {
                            searchViewModel.onQueryChange(it)
                        }
                    },
                    onSuggestionClick = { suggestion ->
                        viewModel.setUrl(suggestion)
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        onSearchQuery(suggestion)
                    },
                    onSearch = {
                        val q = viewModel.inputUrl.trim()
                        when {
                            q.isBlank() -> { emptyToast = true; runCatching { focusRequester.requestFocus() } }
                            else -> {
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
                                if (android.util.Patterns.WEB_URL.matcher(q).matches()) {
                                    viewModel.fetchMedia()
                                } else {
                                    onSearchQuery(q)
                                }
                            }
                        }
                    },
                    onPaste = {
                        val clip = clipboard.getText()?.text.orEmpty()
                        if (clip.isNotBlank()) {
                            viewModel.setUrl(clip)
                            pasteToast = true
                            if (android.util.Patterns.WEB_URL.matcher(clip.trim()).matches()) {
                                viewModel.fetchMedia()
                            }
                        }
                        runCatching { focusRequester.requestFocus() }
                    },
                    pasteToast = pasteToast,
                    emptyToast = emptyToast,
                    focusRequester = focusRequester,
                )
                Spacer(Modifier.height(16.dp))

                // D1: بطاقة إذن المجلد — تظهر فقط عند غياب الإذن
                if (!hasPermission) {
                    CobaltPermissionCard(onRequestPermission)
                    Spacer(Modifier.height(16.dp))
                }

                // D2/D3: بطاقة المعاينة / التحميل / الخطأ
                AnimatedVisibility(
                    visible = scrapingState is ScrapingState.Success || scrapingState is ScrapingState.Error || scrapingState is ScrapingState.Loading,
                    enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 2 },
                    exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 2 },
                ) {
                    Column {
                        when (val s = scrapingState) {
                            is ScrapingState.Loading -> CobaltLoadingCard()
                            is ScrapingState.Success -> CobaltPreviewCard(
                                media = s.media,
                                defaultAudioOnly = quickMode == QuickMode.MP3,
                                onStartDownloads = { requests ->
                                    requests.forEach { r -> viewModel.downloadUrl(r.title, r.url, r.isVideo, r.isAudioOnly, r.thumbnailUrl) }
                                    onShowStarted(true)
                                }
                            )
                            is ScrapingState.Error -> CobaltErrorCard(s.message)
                            else -> {}
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }

            // 3) أسفل الصفحة: رابط الشروط فقط بعد حذف زر التنزيل السريع غير الضروري
            CobaltFootnote(onTerms = { showTerms = true })
        }

        if (showTerms) CobaltTermsSheet(onDismiss = { showTerms = false })
    }
}

// ----------------------------------------------------------------------------
// الترويسة
// ----------------------------------------------------------------------------
@Composable
private fun CobaltTopBar() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                imageVector = Icons.Default.AllInclusive,
                contentDescription = null,
                tint = CG.TextPrimary,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = "Cove",
                fontFamily = CoveBrand,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                letterSpacing = (-0.025).sp,
                color = CG.TextPrimary
            )
        }
    }
}

// ----------------------------------------------------------------------------
// الهيرو
// ----------------------------------------------------------------------------
@Composable
private fun CobaltHero() {
    val heroBrush = Brush.horizontalGradient(listOf(CG.CobaltGlow, CG.CobaltLight, CG.Indigo))
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.hero_title_line_1),
            fontFamily = CoveArabic,
            fontWeight = ArabicHeaviest,
            fontSize = 30.sp,
            lineHeight = 37.5.sp,
            letterSpacing = (-0.025).sp,
            color = CG.TextPrimary,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.hero_title_line_2),
            fontFamily = CoveArabic,
            fontWeight = ArabicHeaviest,
            fontSize = 30.sp,
            lineHeight = 37.5.sp,
            letterSpacing = (-0.025).sp,
            style = TextStyle(brush = heroBrush),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.hero_subtitle),
            fontFamily = CoveArabic,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 22.75.sp,
            color = CG.TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp)
        )
    }
}

// ----------------------------------------------------------------------------
// شريط البحث
// ----------------------------------------------------------------------------
@Composable
private fun CobaltSearchBar(
    value: String,
    suggestions: List<String>,
    suggestionsActive: Boolean,
    suggestionsLoading: Boolean,
    searchFocused: Boolean,
    onSearchFocusChanged: (Boolean) -> Unit,
    onValueChange: (String) -> Unit,
    onSuggestionClick: (String) -> Unit,
    onSearch: () -> Unit,
    onPaste: () -> Unit,
    pasteToast: Boolean,
    emptyToast: Boolean,
    focusRequester: FocusRequester,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glassSurface(
                    fillColor = if (searchFocused) CG.Void else CG.SearchBar,
                    tintAlpha = if (searchFocused) 1f else 0.52f,
                    radius = 30.dp,
                    capsule = true,
                    borderColor = CG.Border10,
                    borderWidth = 0.8.dp,
                    rimWidth = GlassMath.EDGE_RIM_WIDTH_DP.dp
                )
                .clip(CircleShape)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // زر اللصق/النسخ (يمين في RTL — البارز)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(CG.Cobalt, CG.CobaltLight)))
                    .pressClickEffect(onPaste),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = "لصق",
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
            }
            // حقل الإدخال
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = CG.TextPrimary, fontSize = 12.sp, fontFamily = CoveArabic),
                cursorBrush = SolidColor(CG.CobaltLight),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { onSearchFocusChanged(it.isFocused) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                text = stringResource(R.string.search_input_placeholder),
                                color = CG.TextMuted,
                                fontSize = 12.sp,
                                fontFamily = CoveArabic
                            )
                        }
                        inner()
                    }
                }
            )
            // زر البحث (يسار في RTL)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .pressClickEffect(onSearch),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "بحث",
                    tint = CG.TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        if (suggestionsActive) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .height(0.5.dp)
                    .background(CG.Border10)
            )
            SearchSuggestionsPanel(
                suggestions = suggestions,
                loading = suggestionsLoading,
                topCornerRadius = 30.dp,
                onSelect = onSuggestionClick
            )
        }

        // التوست
        Box(modifier = Modifier.fillMaxWidth().height(22.dp), contentAlignment = Alignment.TopCenter) {
            androidx.compose.animation.AnimatedVisibility(visible = pasteToast, enter = fadeIn(tween(300)), exit = fadeOut(tween(300))) {
                Text(
                    text = stringResource(R.string.paste_toast_success),
                    color = CG.CobaltGlow,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = CoveArabic
                )
            }
            androidx.compose.animation.AnimatedVisibility(visible = emptyToast, enter = fadeIn(tween(300)), exit = fadeOut(tween(300))) {
                Text(
                    text = "الصق رابطًا أولًا",
                    color = CG.CobaltGlow,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = CoveArabic
                )
            }
        }
    }
}

// ----------------------------------------------------------------------------
// البطاقة الزجاجية الكبرى
// ----------------------------------------------------------------------------

@Composable
private fun SegmentedPill(label: String, active: Boolean, onClick: () -> Unit) {
    val modifier = if (active) {
        Modifier
            .clip(CircleShape)
            .background(Brush.horizontalGradient(listOf(CG.Cobalt, CG.CobaltLight)))
            .pressClickEffect(onClick)
            .padding(horizontal = 20.dp, vertical = 6.dp)
    } else {
        Modifier
            .clip(CircleShape)
            .pressClickEffect(onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    }
    Text(
        text = label,
        color = if (active) Color.White else CG.TextMuted,
        fontSize = 12.sp,
        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
        fontFamily = CoveArabic,
        modifier = modifier
    )
}

private enum class FeatureBadge { LOCK, CHECK }


// ----------------------------------------------------------------------------
// تذييل الصفحة — رابط الشروط الخفيف
// ----------------------------------------------------------------------------
@Composable
private fun CobaltFootnote(onTerms: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = stringResource(R.string.footnote_pre),
            color = CG.TextMuted,
            fontSize = 11.sp,
            lineHeight = 17.9.sp,
            fontFamily = CoveArabic
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.footnote_terms_link),
            color = CG.CobaltGlow,
            fontSize = 11.sp,
            fontFamily = CoveArabic,
            modifier = Modifier.pressClickEffect(onTerms)
        )
        Text(text = ".", color = CG.TextMuted, fontSize = 11.sp, fontFamily = CoveArabic)
    }
}

// ----------------------------------------------------------------------------
// بطاقة إذن المجلد (D1)
// ----------------------------------------------------------------------------
@Composable
fun CobaltPermissionCard(onRequest: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(
                fillColor = Color(0xFF000000),
                washAlpha = 0.78f,
                capsule = true,
                radius = 28.dp,
                borderColor = Color.White.copy(alpha = 0.16f),
                borderWidth = 0.8.dp,
                rimLightAlpha = 0.45f,
                pressFeedback = false
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(CG.Cobalt.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                tint = CG.CobaltGlow,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = stringResource(R.string.permission_title),
            color = CG.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = CoveArabic,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .height(40.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(CG.Cobalt, CG.CobaltGlow)))
                .pressClickEffect(onRequest)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.permission_btn),
                color = Color.Black,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = CoveArabic
            )
        }
    }
}

// ----------------------------------------------------------------------------
// بطاقات المعاينة / الخطأ / التحميل (Cobalt)
// ----------------------------------------------------------------------------
@Composable
private fun CobaltLoadingCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CG.CardAlpha)
            .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(20.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = CG.CobaltLight)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.analyzing_link), color = CG.TextMuted, fontSize = 13.sp, fontFamily = CoveArabic)
        }
    }
}

@Composable
private fun CobaltErrorCard(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CG.CardAlpha)
            .border(1.dp, CG.ErrorContainer, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(CG.ErrorContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = Icons.Default.Error, contentDescription = null, tint = CG.Error, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(message, color = CG.Error, fontSize = 13.sp, fontFamily = CoveArabic, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CobaltPreviewCard(
    media: com.example.domain.ScrapedMedia,
    defaultAudioOnly: Boolean,
    onStartDownloads: (List<DownloadRequest>) -> Unit,
) {
    var audioOnly by remember { mutableStateOf(defaultAudioOnly) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CG.RadiusCard))
            .background(CG.CardAlpha)
            .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(CG.RadiusCard))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = media.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Text(media.title, color = CG.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = CoveArabic, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(CG.SegTrack)
                .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SegmentedPill(label = stringResource(R.string.preview_video), active = !audioOnly, onClick = { audioOnly = false })
            SegmentedPill(label = stringResource(R.string.download_audio_only), active = audioOnly, onClick = { audioOnly = true })
        }
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(CG.Cobalt, CG.CobaltLight, CG.Cobalt)))
                .pressClickEffect {
                    onStartDownloads(listOf(DownloadRequest(media.title, media.mediaUrl, media.isVideo && !audioOnly, audioOnly, media.thumbnailUrl)))
                },
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.btn_download), color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = CoveArabic)
        }
    }
}

// ----------------------------------------------------------------------------
// ورقة شروط الخدمة (D8)
// ----------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CobaltTermsSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CG.Elevated,
        contentColor = CG.TextPrimary,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 40.dp)) {
            Text(stringResource(R.string.dialog_terms_title), color = CG.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = CoveArabic)
            Spacer(Modifier.height(12.dp))
            Text(
                "يُستخدم التطبيق للتنزيل الشخصي فقط. أنت المسؤول عن احترام حقوق النشر وشروط المنصات التي تحمّل منها.",
                color = CG.TextMuted,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                fontFamily = CoveArabic
            )
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(50.dp)
                    .clip(CircleShape).background(CG.Cobalt)
                    .pressClickEffect(onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.dialog_terms_close), color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = CoveArabic)
            }
        }
    }
}

// ----------------------------------------------------------------------------
// الدوك العائم (D9)
// ----------------------------------------------------------------------------
@Composable
fun CoveDock(currentTab: ScreenTab, onTabSelected: (ScreenTab) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .glassSurface(
                fillColor = Color(0xFF000000),
                tintAlpha = 0.56f,
                capsule = true,
                borderColor = Color.White.copy(alpha = 0.15f),
                borderWidth = 0.8.dp,
                rimWidth = GlassMath.EDGE_RIM_WIDTH_DP.dp
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DockTab(
                icon = Icons.Default.Search,
                label = stringResource(R.string.tab_search),
                active = currentTab == ScreenTab.DOWNLOAD,
                onClick = { onTabSelected(ScreenTab.DOWNLOAD) }
            )
            DockIcon(
                icon = Icons.Default.Folder,
                active = currentTab == ScreenTab.HISTORY,
                onClick = { onTabSelected(ScreenTab.HISTORY) }
            )
        }
    }
}

@Composable
private fun DockTab(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (active) CG.TabActiveBg else Color.Transparent)
            .border(if (active) 1.dp else 0.dp, if (active) CG.BadgeCheckBorder else Color.Transparent, CircleShape)
            .pressClickEffect(onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = if (active) CG.CobaltGlow else CG.TextMuted, modifier = Modifier.size(16.dp))
        if (active) {
            Text(label, color = CG.CobaltGlow, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = CoveArabic)
        }
    }
}

@Composable
private fun DockIcon(icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (active) CG.TabActiveBg else Color.Transparent)
            .pressClickEffect(onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = if (active) CG.CobaltGlow else CG.TextMuted, modifier = Modifier.size(16.dp))
    }
}
