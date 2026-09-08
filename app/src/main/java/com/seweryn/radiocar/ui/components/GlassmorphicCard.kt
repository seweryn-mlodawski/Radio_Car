package com.seweryn.radiocar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.seweryn.radiocar.ui.theme.GlassBorder
import com.seweryn.radiocar.ui.theme.GlassSurface

@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    borderWidth: Dp = 1.dp,
    backgroundColor: Color = GlassSurface,
    borderColor: Color = GlassBorder,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(
                width = borderWidth,
                brush = Brush.linearGradient(
                    listOf(
                        borderColor.copy(alpha = 0.35f),
                        borderColor.copy(alpha = 0.08f)
                    )
                ),
                shape = shape
            )
            .padding(16.dp)
    ) {
        content()
    }
}
