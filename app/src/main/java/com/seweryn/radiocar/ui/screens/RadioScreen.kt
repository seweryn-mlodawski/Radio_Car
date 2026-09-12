package com.seweryn.radiocar.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seweryn.radiocar.ui.RadioViewModel
import com.seweryn.radiocar.ui.components.EditStationDialog
import com.seweryn.radiocar.ui.components.GlassmorphicCard
import com.seweryn.radiocar.ui.components.ResetDefaultsConfirmationDialog
import com.seweryn.radiocar.ui.components.StationSlotsCarousel
import com.seweryn.radiocar.ui.components.VinylCover
import com.seweryn.radiocar.ui.theme.AccentRed
import com.seweryn.radiocar.ui.theme.AccentRedGlow
import com.seweryn.radiocar.ui.theme.BgDarkEnd
import com.seweryn.radiocar.ui.theme.BgDarkStart
import com.seweryn.radiocar.ui.theme.GlassBorder
import com.seweryn.radiocar.ui.theme.GlassSurface
import com.seweryn.radiocar.ui.theme.TextPrimary
import com.seweryn.radiocar.ui.theme.TextSecondary

@Composable
fun RadioScreen(
    viewModel: RadioViewModel,
    modifier: Modifier = Modifier
) {
    val currentStation by viewModel.currentStation.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val songTitle by viewModel.songTitle.collectAsState()
    val artistName by viewModel.artistName.collectAsState()
    val stations by viewModel.stations.collectAsState()
    val editingStation by viewModel.editingStation.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val connectedDevice by viewModel.connectedDeviceName.collectAsState()

    var showResetDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(BgDarkStart, BgDarkEnd, Color(0xFF080A0E))
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: App Title or Connected Bluetooth Audio Device
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    val headerTitle = connectedDevice?.ifBlank { null } ?: "SEWER MOBILE RADIO"
                    val headerSubtitle = if (connectedDevice != null) "Połączono z audio Bluetooth" else "Auto Connect & MediaSession"

                    Text(
                        text = headerTitle,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = headerSubtitle,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.size(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Reset to defaults button (same as Brave extension)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(GlassSurface)
                            .border(1.dp, GlassBorder.copy(alpha = 0.3f), CircleShape)
                            .clickable { showResetDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Przywróć sprawdzone stacje fabryczne",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Live status chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(GlassSurface)
                            .border(1.dp, GlassBorder.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isBuffering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    color = AccentRed,
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "ŁĄCZENIE...",
                                    color = AccentRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else if (isPlaying) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF2EA043))
                                )
                                Text(
                                    text = "NA ŻYWO",
                                    color = Color(0xFF2EA043),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(TextSecondary)
                                )
                                Text(
                                    text = "ZATRZYMANO",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Center: Spinning Vinyl & Gesture Reconnect
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                VinylCover(
                    station = currentStation,
                    isPlaying = isPlaying,
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    onReloadStream = { viewModel.reloadStream() },
                    size = 240.dp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Dotknij płyty aby włączyć/zatrzymać • Przeciągnij aby odświeżyć",
                    color = TextSecondary.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Now Playing Card (Song Title & Artist from ICY Metadata)
            GlassmorphicCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = currentStation?.name ?: "Wybierz stację",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val displaySong = when {
                        songTitle.isNotBlank() -> songTitle
                        isBuffering -> "Łączenie..."
                        isPlaying -> "Live"
                        else -> "Gotowy"
                    }

                    Text(
                        text = displaySong,
                        color = AccentRed,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (artistName.isNotBlank() && !artistName.equals(currentStation?.name, ignoreCase = true)) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = artistName,
                            color = TextSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // In-Car Controls (Large Touch Targets for Safety)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous Station
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(GlassSurface)
                        .border(1.dp, GlassBorder.copy(alpha = 0.3f), CircleShape)
                        .clickable { viewModel.prevStation() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Poprzednia stacja",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }

                // Play / Stop Toggle Button
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .shadow(20.dp, CircleShape, spotColor = AccentRedGlow)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(AccentRed, Color(0xFFCC1836))
                            )
                        )
                        .clickable { viewModel.togglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Zatrzymaj" else "Odtwórz",
                        tint = Color.White,
                        modifier = Modifier.size(46.dp)
                    )
                }

                // Next Station
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(GlassSurface)
                        .border(1.dp, GlassBorder.copy(alpha = 0.3f), CircleShape)
                        .clickable { viewModel.nextStation() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Następna stacja",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Station Presets Carousel (Slots 1..10)
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "GNIAZDA STACJI (1-10)",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Przytrzymaj aby edytować",
                            color = TextSecondary.copy(alpha = 0.5f),
                            fontSize = 10.sp
                        )
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(GlassSurface)
                                .border(0.5.dp, GlassBorder.copy(alpha = 0.3f), CircleShape)
                                .clickable { showResetDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Przywróć stacje fabryczne",
                                tint = TextSecondary.copy(alpha = 0.8f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                StationSlotsCarousel(
                    stations = stations,
                    activeStationId = currentStation?.id,
                    isPlaying = isPlaying,
                    onSelectStation = { viewModel.playStation(it) },
                    onEditStation = { viewModel.openEditDialog(it) }
                )

                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Edit Slot Dialog
        editingStation?.let { station ->
            EditStationDialog(
                station = station,
                allStations = stations,
                searchResults = searchResults,
                isSearching = isSearching,
                onSearch = { viewModel.searchStations(it) },
                onClearSearch = { viewModel.clearSearchResults() },
                onDismiss = { viewModel.closeEditDialog() },
                onSave = { sourceId, targetId, name, streamUrl, logoUrl, icon ->
                    viewModel.saveOrSwapStation(sourceId, targetId, name, streamUrl, logoUrl, icon)
                },
                onClear = { stationId -> viewModel.clearStation(stationId) }
            )
        }

        // Reset Defaults Confirmation Dialog
        if (showResetDialog) {
            ResetDefaultsConfirmationDialog(
                onDismiss = { showResetDialog = false },
                onConfirm = { viewModel.resetToDefaults() }
            )
        }
    }
}
