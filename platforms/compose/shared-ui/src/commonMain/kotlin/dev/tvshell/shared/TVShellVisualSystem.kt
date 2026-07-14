package dev.tvshell.shared

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object TVShellVisual {
    const val CornerRadius = 28f
    const val FocusAnimationMilliseconds = TVShellDesign.FocusAnimationMilliseconds
    val BackdropTop = Color(0xFF23252C)
    val BackdropBottom = Color(0xFF090A0D)
    val Surface = Color(0xCC24262D)
    val ContentSurface = Color(0xB82D3038)
    val FocusSurface = Color(0xFFF0F1F3)
}

enum class TVSurfaceRole { Dock, Panel, Content, Alert }

@Composable
fun TVShellBackdrop(content: @Composable BoxScope.() -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(TVShellVisual.BackdropTop, TVShellVisual.BackdropBottom))),
        content = content,
    )
}

@Composable
fun Modifier.tvShellFocus(isFocused: Boolean): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (isFocused) TVShellDesign.FocusScale else 1f,
        animationSpec = tween(TVShellVisual.FocusAnimationMilliseconds),
        label = "TVShell focus scale",
    )
    return scale(scale)
}

fun Modifier.tvShellSurface(
    role: TVSurfaceRole,
    isFocused: Boolean = false,
    cornerRadius: Float = TVShellVisual.CornerRadius,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius.dp)
    val color = when {
        isFocused -> TVShellVisual.FocusSurface
        role == TVSurfaceRole.Content -> TVShellVisual.ContentSurface
        else -> TVShellVisual.Surface
    }
    return clip(shape)
        .background(color)
        .border(1.dp, Color.White.copy(alpha = if (isFocused) .46f else .12f), shape)
}
