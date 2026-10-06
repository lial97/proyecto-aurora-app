package app.aurora.components

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import app.aurora.player.Media3Engine

/**
 * Video de Media3 dibujado en una TextureView: a diferencia de SurfaceView, respeta la transparencia,
 * el recorte y el orden de Compose (no deja cuadros "fantasma" encima de la interfaz).
 */
@OptIn(UnstableApi::class)
@Composable
actual fun NativeVideoView(output: app.aurora.player.VideoOutput, modifier: Modifier) {
    val player by ((output as? Media3Engine.Media3Video)?.player ?: return).collectAsState()
    ContentFrame(player, modifier, surfaceType = SURFACE_TYPE_TEXTURE_VIEW, contentScale = ContentScale.Fit)
}
