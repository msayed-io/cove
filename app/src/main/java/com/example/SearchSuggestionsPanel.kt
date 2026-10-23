package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CG
import com.example.ui.theme.CoveArabic

/** Opaque Cove suggestions card with rounded upper corners that meet the capsule search field. */
@Composable
internal fun SearchSuggestionsPanel(
    suggestions: List<String>,
    loading: Boolean,
    modifier: Modifier = Modifier,
    topCornerRadius: Dp = 24.dp,
    onSelect: (String) -> Unit
) {
    val shape = RoundedCornerShape(
        topStart = topCornerRadius,
        topEnd = topCornerRadius,
        bottomStart = 22.dp,
        bottomEnd = 22.dp
    )
    val visibleSuggestions = suggestions.take(5)
    // Match the reference's fixed dropdown height; only the row count varies within it.
    val panelHeight = 240.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(panelHeight)
            .clip(shape)
            .background(CG.Card)
            .border(1.dp, CG.Border10, shape)
    ) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = CG.CobaltGlow,
                    strokeWidth = 2.dp
                )
            }
            visibleSuggestions.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "لا توجد اقتراحات الآن",
                    color = CG.TextMuted,
                    fontSize = 13.sp,
                    fontFamily = CoveArabic
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Top
            ) {
                itemsIndexed(visibleSuggestions) { index, suggestion ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(47.dp)
                            .clickable { onSelect(suggestion) }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = CG.CobaltGlow,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(18.dp)
                        )
                        Text(
                            text = suggestion,
                            color = CG.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = CoveArabic,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (index < visibleSuggestions.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = CG.Divider05,
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}
