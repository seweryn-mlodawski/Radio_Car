package com.seweryn.radiocar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.seweryn.radiocar.data.model.Station
import com.seweryn.radiocar.data.repository.SearchedStation
import com.seweryn.radiocar.ui.theme.AccentRed
import com.seweryn.radiocar.ui.theme.BgDarkEnd
import com.seweryn.radiocar.ui.theme.GlassBorder
import com.seweryn.radiocar.ui.theme.GlassSurface
import com.seweryn.radiocar.ui.theme.TextPrimary
import com.seweryn.radiocar.ui.theme.TextSecondary

@Composable
fun EditStationDialog(
    station: Station,
    allStations: List<Station>,
    searchResults: List<SearchedStation>,
    isSearching: Boolean,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (sourceId: Int, targetId: Int, name: String, streamUrl: String, logoUrl: String, icon: String) -> Unit,
    onClear: (Int) -> Unit
) {
    var targetSlotId by remember { mutableIntStateOf(station.id) }
    var name by remember { mutableStateOf(if (station.isEmpty) "" else station.name) }
    var streamUrl by remember { mutableStateOf(station.streamUrl) }
    var logoUrl by remember { mutableStateOf(station.logoUrl) }
    var icon by remember { mutableStateOf(station.icon) }
    var slotDropdownExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val targetStation = allStations.find { it.id == targetSlotId }
    val isSwapping = targetSlotId != station.id && targetStation != null && !targetStation.isEmpty

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = {
            onClearSearch()
            onDismiss()
        },
        containerColor = BgDarkEnd,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = "Edycja Gniazda #${station.id}",
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Section: Online Station Search
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(GlassSurface)
                        .border(1.dp, GlassBorder.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "WYSZUKAJ STACJĘ ONLINE",
                        color = AccentRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Nazwa lub gatunek (np. Rock, Jazz, RMF)") },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    onClearSearch()
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Wyczyść",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch(searchQuery) }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AccentRed,
                            unfocusedBorderColor = TextSecondary.copy(alpha = 0.5f),
                            focusedLabelColor = AccentRed,
                            unfocusedLabelColor = TextSecondary
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Quick genre tags
                    val quickTags = listOf("Rock", "Metal", "Pop", "Jazz", "Electronic", "Polska", "News")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        items(quickTags) { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x33FFFFFF))
                                    .clickable {
                                        searchQuery = tag
                                        onSearch(tag)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = tag,
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { onSearch(searchQuery) },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Szukaj", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Searching progress
                    if (isSearching) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = AccentRed,
                                strokeWidth = 2.dp
                            )
                            Text("Wyszukiwanie stacji online...", color = TextSecondary, fontSize = 12.sp)
                        }
                    }

                    // Search results
                    if (searchResults.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Wyniki (${searchResults.size}): dotknij aby wypełnić",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            searchResults.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0x33000000))
                                        .clickable {
                                            name = item.name.trim()
                                            streamUrl = item.streamUrl.trim()
                                            logoUrl = item.favicon.trim()
                                            icon = if (item.tags.contains("rock", ignoreCase = true) || item.tags.contains("metal", ignoreCase = true)) "🎸" else "📻"
                                            onClearSearch()
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (item.favicon.isNotBlank()) {
                                            AsyncImage(
                                                model = item.favicon,
                                                contentDescription = item.name,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        } else {
                                            Text(text = "📻", fontSize = 16.sp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }

                                        Column {
                                            Text(
                                                text = item.name,
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (item.tags.isNotBlank()) {
                                                Text(
                                                    text = item.tags.take(35),
                                                    color = TextSecondary,
                                                    fontSize = 10.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    Text(
                                        text = "Wybierz",
                                        color = AccentRed,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(start = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Target Slot Selector (Dropdown)
                Column {
                    Text(
                        text = "Docelowy numer gniazda:",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(GlassSurface)
                            .border(1.dp, GlassBorder.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .clickable { slotDropdownExpanded = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val slotLabel = if (targetStation != null && !targetStation.isEmpty) {
                                "Gniazdo $targetSlotId (${targetStation.name})"
                            } else {
                                "Gniazdo $targetSlotId [Puste]"
                            }
                            Text(
                                text = slotLabel,
                                color = if (targetSlotId != station.id) AccentRed else TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Wybierz gniazdo",
                                tint = TextSecondary
                            )
                        }

                        DropdownMenu(
                            expanded = slotDropdownExpanded,
                            onDismissRequest = { slotDropdownExpanded = false },
                            modifier = Modifier.background(BgDarkEnd)
                        ) {
                            for (slotNum in 1..10) {
                                val s = allStations.find { it.id == slotNum }
                                val isCur = slotNum == station.id
                                val isOccupied = s != null && !s.isEmpty
                                val text = buildString {
                                    append("Gniazdo $slotNum")
                                    if (isOccupied) append(" (${s.name})") else append(" [Puste]")
                                    if (isCur) append(" - (Edytowane)")
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = text,
                                            color = if (isCur) AccentRed else if (isOccupied) Color(0xFF58A6FF) else TextSecondary,
                                            fontWeight = if (isCur) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        targetSlotId = slotNum
                                        slotDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Swap warning notice
                    if (isSwapping) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x33F0883E))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = "Zamiana",
                                tint = Color(0xFFFFA657),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Gniazdo $targetSlotId jest zajęte. Stacje zamienią się miejscami!",
                                color = Color(0xFFFFA657),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Station Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nazwa stacji") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentRed,
                        unfocusedBorderColor = TextSecondary.copy(alpha = 0.5f),
                        focusedLabelColor = AccentRed,
                        unfocusedLabelColor = TextSecondary
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Stream URL
                OutlinedTextField(
                    value = streamUrl,
                    onValueChange = { streamUrl = it },
                    label = { Text("Adres URL strumienia") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentRed,
                        unfocusedBorderColor = TextSecondary.copy(alpha = 0.5f),
                        focusedLabelColor = AccentRed,
                        unfocusedLabelColor = TextSecondary
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Logo URL
                OutlinedTextField(
                    value = logoUrl,
                    onValueChange = { logoUrl = it },
                    label = { Text("URL do loga (opcjonalnie)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentRed,
                        unfocusedBorderColor = TextSecondary.copy(alpha = 0.5f),
                        focusedLabelColor = AccentRed,
                        unfocusedLabelColor = TextSecondary
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Icon / Emoji
                OutlinedTextField(
                    value = icon,
                    onValueChange = { icon = it },
                    label = { Text("Ikona / Emoji (np. 📻, 🎸)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentRed,
                        unfocusedBorderColor = TextSecondary.copy(alpha = 0.5f),
                        focusedLabelColor = AccentRed,
                        unfocusedLabelColor = TextSecondary
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onClearSearch()
                    onSave(
                        station.id,
                        targetSlotId,
                        name.trim(),
                        streamUrl.trim(),
                        logoUrl.trim(),
                        icon.trim()
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
            ) {
                Text(
                    text = if (isSwapping) "Zapisz i Zamień" else "Zapisz",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            Row {
                OutlinedButton(
                    onClick = {
                        onClearSearch()
                        onClear(station.id)
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF85149))
                ) {
                    Text("Wyczyść")
                }
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                OutlinedButton(onClick = {
                    onClearSearch()
                    onDismiss()
                }) {
                    Text("Anuluj", color = TextSecondary)
                }
            }
        }
    )
}
