package app.aurora.domain

/** Audio y video viven en secciones separadas de la biblioteca. */
enum class MediaType {
    AUDIO, VIDEO;

    companion object {
        private val audio = setOf("mp3", "flac", "m4a", "aac", "ogg", "oga", "opus", "wav", "wma", "aiff", "alac")
        private val video = setOf("mp4", "m4v", "mkv", "webm", "mov", "avi", "3gp", "ts")

        /** Clasifica por extensión; `null` si no es un formato multimedia conocido. */
        fun fromFileName(name: String): MediaType? {
            val ext = name.substringAfterLast('.', "").lowercase()
            return when (ext) {
                in audio -> AUDIO
                in video -> VIDEO
                else -> null
            }
        }
    }
}

/** De dónde salieron los metadatos de la pista. */
enum class MetadataSource { FILE_TAGS, USER, MUSICBRAINZ }

/** Receta de portada procedimental para los datos de ejemplo (en producción: [Track.coverUri]). */
data class CoverRecipe(val style: CoverStyle, val background: Long, val colors: List<Long>)

enum class CoverStyle { SUN, CIRCLES, STRIPES, WAVES }

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationSec: Int,
    val mediaType: MediaType = MediaType.AUDIO,
    val bpm: Int? = null,
    val key: String? = null,
    val genre: String? = null,
    /** Año de lanzamiento, para ordenar. */
    val year: Int? = null,
    /** Fecha de lanzamiento legible. */
    val releaseDate: String? = null,
    /** Momento en que se añadió a la biblioteca (epoch ms), para ordenar. */
    val dateAddedMs: Long = 0,
    val filePath: String? = null,
    val coverUri: String? = null,
    val coverRecipe: CoverRecipe? = null,
    val composers: String? = null,
    val producers: String? = null,
    val mixing: String? = null,
    val monthlyListeners: String? = null,
    val lyrics: List<LyricLine> = emptyList(),
    /** Letra sin marcas de tiempo (etiqueta del archivo). */
    val plainLyrics: String? = null,
    /** Formato y calidad para las etiquetas técnicas de Carbono ("MP3", "320 kbps"). */
    val format: String? = null,
    val bitrateKbps: Int? = null,
    val sampleRateHz: Int? = null,
    val bitsPerSample: Int? = null,
    val trackNumber: Int? = null,
    val trackTotal: Int? = null,
    /** Tamaño del archivo en bytes. */
    val fileSizeBytes: Long? = null,
    val metadataSource: MetadataSource = MetadataSource.FILE_TAGS,
) {
    val hasCover: Boolean get() = coverUri != null || coverRecipe != null
}

data class Playlist(
    val id: String,
    val name: String,
    val trackIds: List<String>,
    /** Tres colores fijos para el mosaico (spec §9 "Mezcla"). */
    val colors: List<Long> = emptyList(),
    val description: String? = null,
    /** Lista creada por el usuario (se puede renombrar, borrar y editar). */
    val isUser: Boolean = false,
)

/** Datos editados por el usuario ("Editar datos"). Campos `null` = sin cambio. */
data class TrackEdit(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val year: Int? = null,
    val genre: String? = null,
    /** Hay una portada descargada para esta pista ("Corregir datos"). */
    val customCover: Boolean = false,
) {
    fun applyTo(t: Track) = t.copy(
        title = title ?: t.title, artist = artist ?: t.artist, album = album ?: t.album,
        year = year ?: t.year, genre = genre ?: t.genre,
        coverUri = if (customCover) CUSTOM_COVER else t.coverUri,
        metadataSource = if (title != null || artist != null) MetadataSource.USER else t.metadataSource,
    )

    /** Una corrección nueva sobre otra anterior: lo nuevo manda y lo anterior se conserva. */
    fun mergedOnto(old: TrackEdit?): TrackEdit = if (old == null) this else TrackEdit(
        title ?: old.title, artist ?: old.artist, album ?: old.album, year ?: old.year, genre ?: old.genre, customCover || old.customCover,
    )

    companion object {
        /** Marca de `Track.coverUri`: la portada es una descargada y guardada por la app. */
        const val CUSTOM_COVER = "aurora:portada"
    }
}
