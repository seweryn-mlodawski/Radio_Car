package com.seweryn.radiocar.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.seweryn.radiocar.data.model.Station
import com.seweryn.radiocar.ui.theme.AccentGreen
import com.seweryn.radiocar.ui.theme.AccentRed
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun VinylCover(
    station: Station?,
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onReloadStream: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 250.dp
) {
    // Continuous rotation when music is playing
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation_angle"
    )

    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var showReloadCue by remember { mutableStateOf(false) }

    val currentRotation = if (isPlaying) rotation else 0f
    val glowColor by animateColorAsState(
        targetValue = if (isPlaying) AccentGreen else AccentRed,
        animationSpec = tween(durationMillis = 500),
        label = "vinyl_glow_color"
    )

    Box(
        modifier = modifier
            .size(size)
            .offset { IntOffset(0, dragOffsetY.roundToInt() / 2) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var totalDragY = 0f
                    var isDrag = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            // Finger lifted up
                            if (!isDrag && abs(totalDragY) < 18f) {
                                // Tap on vinyl disc -> toggle Play / Stop
                                onTogglePlayPause()
                            } else if (isDrag && abs(totalDragY) > 75f) {
                                // Vertical swipe gesture -> reload stream
                                onReloadStream()
                            }
                            dragOffsetY = 0f
                            showReloadCue = false
                            break
                        } else {
                            val dragDelta = change.position.y - change.previousPosition.y
                            totalDragY += dragDelta
                            if (abs(totalDragY) > 18f) {
                                isDrag = true
                                showReloadCue = true
                                change.consume()
                                dragOffsetY = totalDragY.coerceIn(-140f, 140f)
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Ambient Neon Glow behind the disc (Green when playing, Red when stopped)
        Box(
            modifier = Modifier
                .size(size + 28.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = if (isPlaying) 0.38f else 0.18f),
                            glowColor.copy(alpha = if (isPlaying) 0.12f else 0.04f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Outer Vinyl Disc
        Box(
            modifier = Modifier
                .size(size)
                .shadow(elevation = 20.dp, shape = CircleShape, ambientColor = Color.Black, spotColor = glowColor)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF23272F),
                            Color(0xFF14171D),
                            Color(0xFF0A0C0F)
                        )
                    )
                )
                .rotate(currentRotation),
            contentAlignment = Alignment.Center
        ) {
            // Vinyl grooved rings
            Canvas(modifier = Modifier.size(size)) {
                val center = Offset(size.toPx() / 2f, size.toPx() / 2f)
                val maxRadius = size.toPx() / 2f

                // Draw decorative vinyl grooves
                for (ratio in listOf(0.92f, 0.85f, 0.78f, 0.70f, 0.62f, 0.55f)) {
                    drawCircle(
                        color = Color(0x1AFFFFFF),
                        radius = maxRadius * ratio,
                        center = center,
                        style = Stroke(width = 1f)
                    )
                }
            }

            // Center Label / Logo
            val centerLabelSize = size * 0.44f
            Box(
                modifier = Modifier
                    .size(centerLabelSize)
                    .clip(CircleShape)
                    .background(Color(0xFF1E232B))
                    .border(2.dp, glowColor.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (station != null && station.logoUrl.isNotBlank()) {
                    coil.compose.SubcomposeAsyncImage(
                        model = station.logoUrl,
                        contentDescription = station.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(centerLabelSize)
                            .clip(CircleShape),
                        loading = {
                            DefaultStationLogo(station = station, glowColor = glowColor)
                        },
                        error = {
                            DefaultStationLogo(station = station, glowColor = glowColor)
                        }
                    )
                } else {
                    DefaultStationLogo(station = station, glowColor = glowColor)
                }

                // Spindle hole
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0D1117))
                        .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                )
            }
        }

        // Swipe reload cue
        if (showReloadCue && kotlin.math.abs(dragOffsetY) > 40f) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xCC000000))
                    .border(1.dp, glowColor, CircleShape)
                    .size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Odśwież strumień",
                    tint = glowColor,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
fun DefaultStationLogo(
    station: Station?,
    glowColor: Color = AccentRed,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        glowColor.copy(alpha = 0.22f),
                        Color(0xFF1E242C),
                        Color(0xFF12151B)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Radio,
                contentDescription = null,
                tint = glowColor,
                modifier = Modifier.size(34.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            val displayName = station?.name?.ifBlank { "RADIO" } ?: "RADIO"
            Text(
                text = displayName.take(8).uppercase(),
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                maxLines = 1
            )
        }
    }
}

