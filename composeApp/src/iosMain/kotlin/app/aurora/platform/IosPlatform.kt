package app.aurora.platform

import androidx.compose.ui.graphics.ImageBitmap
import app.aurora.domain.Track

// Pendiente: biblioteca de música (MPMediaLibrary), archivos importados, AVPlayer y NSUserDefaults.
actual fun createPlatformServices(): PlatformServices = PlatformServices(
    name = "iOS",
    isDesktop = false,
    store = MemoryStore(),
    media = object : MediaSource {
        override fun defaultFolders(): List<String> = emptyList()
        override val canPickFolder: Boolean = false
        override suspend fun pickFolder(): String? = null
        override suspend fun candidateFolders(): List<String> = emptyList()
        override suspend fun scan(folders: List<String>, onProgress: (ScanProgress) -> Unit): List<Track> = emptyList()
        override suspend fun loadCover(track: Track): ImageBitmap? = null
    },
    mediaEngine = null,
)
