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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A fixed-height, search-bar-width dropdown shared by home and full search. */
@Composable
internal fun SearchSuggestionsPanel(
    suggestions: List<String>,
    loading: Boolean,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit
) {
    val shape = RoundedCornerShape(
        topStart = 9.dp,
        topEnd = 9.dp,
        bottomStart = 22.dp,
        bottomEnd = 22.dp
    )
    val visibleSuggestions = suggestions.take(5)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(shape)
            .background(Color(0xF2141518))
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
    ) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color(0xFFF1F5F9),
                    strokeWidth = 2.dp
                )
            }
            visibleSuggestions.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "لا توجد اقتراحات الآن",
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Top
            ) {
                itemsIndexed(visibleSuggestions) { index, suggestion ->
                    Column {
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
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Text(
                                text = suggestion,
                                color = Color(0xFFF1F5F9),
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (index < visibleSuggestions.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = Color.White.copy(alpha = 0.07f),
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
            }
        }
    }
}
