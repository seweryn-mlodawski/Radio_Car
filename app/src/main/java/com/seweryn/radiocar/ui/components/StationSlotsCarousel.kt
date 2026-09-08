package com.seweryn.radiocar.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.seweryn.radiocar.data.model.Station
import com.seweryn.radiocar.ui.theme.AccentRed
import com.seweryn.radiocar.ui.theme.ActiveSlotGlow
import com.seweryn.radiocar.ui.theme.GlassBorder
import com.seweryn.radiocar.ui.theme.GlassSurface
import com.seweryn.radiocar.ui.theme.TextPrimary
import com.seweryn.radiocar.ui.theme.TextSecondary

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StationSlotsCarousel(
    stations: List<Station>,
    activeStationId: Int?,
    isPlaying: Boolean,
    onSelectStation: (Station) -> Unit,
    onEditStation: (Station) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    LaunchedEffect(activeStationId) {
        if (activeStationId != null && stations.isNotEmpty()) {
            val index = stations.indexOfFirst { it.id == activeStationId }
            if (index != -1) {
                when (index) {
                    0 -> {
                        // Pierwsza stacja -> przylega do lewej krawędzi ekranu
                        listState.animateScrollToItem(index = 0, scrollOffset = 0)
                    }
                    stations.lastIndex -> {
                        // Ostatnia stacja -> przylega do prawej krawędzi ekranu
                        listState.animateScrollToItem(index = stations.lastIndex, scrollOffset = 0)
                    }
                    else -> {
                        // Stacje pośrednie -> wyśrodkowanie aktywnego gniazda na ekranie
                        val viewportWidth = if (listState.layoutInfo.viewportSize.width > 0) {
                            listState.layoutInfo.viewportSize.width
                        } else {
                            with(density) { configuration.screenWidthDp.dp.roundToPx() }
                        }
                        val itemWidth = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size
                            ?: with(density) { 130.dp.roundToPx() }

                        val centerOffset = -((viewportWidth - itemWidth) / 2)
                        listState.animateScrollToItem(index = index, scrollOffset = centerOffset)
                    }
                }
            }
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(stations, key = { it.id }) { station ->
            val isActive = station.id == activeStationId
            val cardBorderColor = if (isActive) AccentRed else GlassBorder.copy(alpha = 0.2f)
            val cardBg = if (isActive) GlassSurface.copy(alpha = 0.7f) else GlassSurface.copy(alpha = 0.35f)

            Box(
                modifier = Modifier
                    .width(130.dp)
                    .height(115.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(cardBg)
                    .border(
                        width = if (isActive) 2.dp else 1.dp,
                        color = cardBorderColor,
                        shape = RoundedCornerShape(18.dp)
                    )
                    .combinedClickable(
                        onClick = {
                            if (station.isEmpty) {
                                onEditStation(station)
                            } else {
                                onSelectStation(station)
                            }
                        },
                        onLongClick = {
                            onEditStation(station)
                        }
                    )
                    .padding(8.dp)
            ) {
                // Slot Number Badge (Top Left)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .clip(CircleShape)
                        .background(if (isActive) AccentRed else Color(0x33FFFFFF))
                        .size(22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${station.id}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Edit Icon (Top Right)
                IconButton(
                    onClick = { onEditStation(station) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edytuj stację",
                        tint = TextSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Center Icon / Logo & Name
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (station.logoUrl.isNotBlank()) {
                        AsyncImage(
                            model = station.logoUrl,
                            contentDescription = station.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Text(
                            text = station.icon,
                            fontSize = 26.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = station.name,
                        color = if (isActive) Color.White else TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }

                // "GRA" Indicator Badge (Bottom Center)
                if (isActive && isPlaying) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .clip(RoundedCornerShape(6.dp))
                            .background(AccentRed)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "ON AIR",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}
