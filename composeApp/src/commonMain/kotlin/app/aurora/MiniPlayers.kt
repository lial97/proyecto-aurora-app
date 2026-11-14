package app.aurora

/** Qué mini reproductores se muestran en el móvil (además de que no haya una pantalla a pantalla completa). */
data class MiniPlayers(val audio: Boolean, val video: Boolean)

/**
 * Regla de los mini reproductores:
 * - Con un video que se ve, solo el mini video flotante (no los dos a la vez).
 * - Si el mini video se cerró con × o el video está en "Solo audio", vuelve el mini reproductor de audio para no
 *   quedarse sin controles.
 * - Deslizar uno en pausa quita los dos ([miniHiddenFor] y [pipHiddenFor]); vuelven al reanudar o cambiar de pista.
 */
fun miniPlayers(
    currentId: String?,
    hasVideo: Boolean,
    videoEnabled: Boolean,
    miniHiddenFor: String?,
    pipClosedFor: String?,
    pipHiddenFor: String?,
): MiniPlayers {
    if (currentId == null) return MiniPlayers(audio = false, video = false)
    val videoShown = hasVideo && videoEnabled && pipClosedFor != currentId
    return MiniPlayers(
        audio = !videoShown && miniHiddenFor != currentId,
        video = videoShown && pipHiddenFor != currentId,
    )
}
