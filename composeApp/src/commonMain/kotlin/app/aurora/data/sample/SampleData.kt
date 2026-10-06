package app.aurora.data.sample

import app.aurora.domain.CoverRecipe
import app.aurora.domain.CoverStyle
import app.aurora.domain.LyricLine
import app.aurora.domain.MediaType
import app.aurora.domain.Playlist
import app.aurora.domain.Track

/** Catálogo de ejemplo tomado del prototipo (files/aurora-app-musica.html). */
object SampleData {
    private const val DAY = 86_400_000L
    private const val BASE = 1_780_000_000_000L

    private val neonLyrics = listOf(
        0 to "♪", 8 to "Se apagan las ventanas de la avenida", 13 to "y tú sigues brillando sin pedir permiso",
        18 to "la noche tiene el pulso de tu risa", 23 to "y yo me pierdo un poco, como quiso",
        29 to "Neón en la piel, no me sueltes", 34 to "que el amanecer todavía no llega",
        39 to "neón en la piel, si te vas, vuelve", 44 to "que esta ciudad sin ti no se enciende",
        50 to "♪", 56 to "Los taxis dibujan líneas de colores", 61 to "tu sombra baila sobre la pared",
        66 to "no quiero nombres, no quiero razones", 71 to "solo este ruido y tu forma de ser",
        77 to "Neón en la piel, no me sueltes", 82 to "que el amanecer todavía no llega",
        87 to "neón en la piel, si te vas, vuelve", 92 to "que esta ciudad sin ti no se enciende", 100 to "♪",
    ).map { (t, s) -> LyricLine(t * 1000L, s) }

    private fun recipe(style: CoverStyle, bg: Long, vararg c: Long) = CoverRecipe(style, bg, c.toList())

    val tracks: List<Track> = listOf(
        Track("1", "Neón en la piel", "Cielo Prisma", "Luz de madrugada", 228, bpm = 118, key = "La menor",
            genre = "Synth pop", year = 2026, releaseDate = "14 feb 2026", dateAddedMs = BASE - 1 * DAY,
            composers = "Valeria Ortiz, Tomás Ríos", producers = "Estudio Marejada", mixing = "Iván Duarte",
            monthlyListeners = "1,2 M", lyrics = neonLyrics,
            coverRecipe = recipe(CoverStyle.SUN, 0xFF24103D, 0xFFFF3D8B, 0xFFFF8A3D, 0xFFFFD23D)),
        Track("2", "Satélite", "Mar Abierto", "Órbitas", 252, bpm = 92, key = "Re mayor", genre = "Dream pop",
            year = 2026, releaseDate = "3 mar 2026", dateAddedMs = BASE - 9 * DAY, composers = "Lucas Mena",
            producers = "Lucas Mena", mixing = "Sara Quintero", monthlyListeners = "640 mil",
            coverRecipe = recipe(CoverStyle.CIRCLES, 0xFF0B1D2E, 0xFF3DE0C0, 0xFF1A8A9C, 0xFFE9F5F2)),
        Track("3", "Calle sin nombre", "Los Imanes", "Ruido blanco", 201, bpm = 140, key = "Mi menor",
            genre = "Indie rock", year = 2025, releaseDate = "22 nov 2025", dateAddedMs = BASE - 30 * DAY,
            composers = "Los Imanes", producers = "Andrés Pardo", mixing = "Andrés Pardo", monthlyListeners = "410 mil",
            coverRecipe = recipe(CoverStyle.STRIPES, 0xFF151515, 0xFFFFD400, 0xFFF2F2F2, 0xFFFF4D2E)),
        Track("4", "Ventana abierta", "Ana Brisa", "Casa de aire", 179, bpm = 80, key = "Sol mayor", genre = "Folk",
            year = 2026, releaseDate = "9 ene 2026", dateAddedMs = BASE - 3 * DAY, composers = "Ana Brisa",
            producers = "Camila Soto", mixing = "Camila Soto", monthlyListeners = "280 mil"),
        Track("5", "Fiebre lunar", "Cielo Prisma", "Luz de madrugada", 215, bpm = 128, key = "Fa menor",
            genre = "Synth pop", year = 2026, releaseDate = "14 feb 2026", dateAddedMs = BASE - 12 * DAY,
            composers = "Valeria Ortiz", producers = "Estudio Marejada", mixing = "Iván Duarte", monthlyListeners = "1,2 M",
            coverRecipe = recipe(CoverStyle.SUN, 0xFF0E0A24, 0xFFB9A4FF, 0xFF7A5CFF, 0xFFF2EEFF)),
        Track("6", "Agua de coco", "Tropicanta", "Brisa salada", 185, bpm = 106, key = "Do mayor", genre = "Tropical",
            year = 2026, releaseDate = "1 jul 2026", dateAddedMs = BASE - 2 * DAY, composers = "Jhon Palacios",
            producers = "Tropicanta", mixing = "Mónica Gil", monthlyListeners = "2,3 M",
            coverRecipe = recipe(CoverStyle.WAVES, 0xFF0F3D2E, 0xFF7DDC5A, 0xFFFFCF3D, 0xFF2BB3A0)),
        Track("7", "Papel y humo", "Nadia Roca", "Ceniza", 241, bpm = 70, key = "Si menor", genre = "R&B",
            year = 2026, releaseDate = "18 may 2026", dateAddedMs = BASE - 20 * DAY, composers = "Nadia Roca",
            producers = "Felipe Arango", mixing = "Felipe Arango", monthlyListeners = "890 mil"),
        Track("8", "Kilómetro cero", "Mar Abierto", "Órbitas", 230, bpm = 112, key = "La mayor", genre = "Dream pop",
            year = 2026, releaseDate = "3 mar 2026", dateAddedMs = BASE - 5 * DAY, composers = "Lucas Mena",
            producers = "Lucas Mena", mixing = "Sara Quintero", monthlyListeners = "640 mil",
            coverRecipe = recipe(CoverStyle.STRIPES, 0xFF1D1B3A, 0xFF4D7CFF, 0xFFFF6B6B, 0xFFF5F5F5)),
        // Videos: sección aparte, nunca se mezclan con las canciones.
        Track("v1", "Neón en la piel (video oficial)", "Cielo Prisma", "Luz de madrugada", 236,
            mediaType = MediaType.VIDEO, bpm = 118, year = 2026, dateAddedMs = BASE - 4 * DAY,
            coverRecipe = recipe(CoverStyle.SUN, 0xFF24103D, 0xFFFF3D8B, 0xFFFF8A3D, 0xFFFFD23D)),
        Track("v2", "Agua de coco (en vivo)", "Tropicanta", "Brisa salada", 312,
            mediaType = MediaType.VIDEO, bpm = 106, year = 2026, dateAddedMs = BASE - 8 * DAY,
            coverRecipe = recipe(CoverStyle.WAVES, 0xFF0F3D2E, 0xFF7DDC5A, 0xFFFFCF3D, 0xFF2BB3A0)),
    )

    val playlists: List<Playlist> = listOf(
        Playlist("m1", "Mezcla nocturna", listOf("1", "2", "3", "4", "5", "6", "7", "8"),
            listOf(0xFF9B5CFF, 0xFFFF5FA2, 0xFF1B1640), "Luces de ciudad y canciones para la madrugada"),
        Playlist("m2", "Para concentrarte", listOf("7", "4", "2", "8"), listOf(0xFF38E1C6, 0xFF5B8CFF, 0xFFF2EEFF)),
        Playlist("m3", "Energía", listOf("3", "5", "1", "6"), listOf(0xFFFFB547, 0xFFFF5F5F, 0xFFFF5FA2)),
        Playlist("m4", "Descubrimientos", listOf("6", "8", "4"), listOf(0xFF5B8CFF, 0xFFFFB547, 0xFF9B5CFF)),
    )

    fun tracksOf(playlist: Playlist): List<Track> = playlist.trackIds.mapNotNull { id -> tracks.find { it.id == id } }
}
