package app.aurora.platform

import java.io.File
import java.io.RandomAccessFile

/** Lee la duración de un MP4/MOV desde la caja `moov/mvhd` sin decodificar el video. */
internal object Mp4 {
    fun durationSec(file: File): Int? {
        if (file.extension.lowercase() !in setOf("mp4", "m4v", "mov", "3gp")) return null
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val moov = findBox(raf, 0, raf.length(), "moov") ?: return null
                val mvhd = findBox(raf, moov.first, moov.second, "mvhd") ?: return null
                raf.seek(mvhd.first)
                val version = raf.readUnsignedByte()
                raf.skipBytes(3) // flags
                val (timescale, duration) = if (version == 1) {
                    raf.skipBytes(16) // creación y modificación (64 bits cada una)
                    raf.readInt().toLong() and 0xFFFFFFFFL to raf.readLong()
                } else {
                    raf.skipBytes(8)
                    (raf.readInt().toLong() and 0xFFFFFFFFL) to (raf.readInt().toLong() and 0xFFFFFFFFL)
                }
                if (timescale > 0) (duration / timescale).toInt() else null
            }
        }.getOrNull()
    }

    /** Busca una caja hija; devuelve (inicio del contenido, fin). */
    private fun findBox(raf: RandomAccessFile, start: Long, end: Long, type: String): Pair<Long, Long>? {
        var pos = start
        while (pos + 8 <= end) {
            raf.seek(pos)
            var size = raf.readInt().toLong() and 0xFFFFFFFFL
            val name = ByteArray(4).also { raf.readFully(it) }.toString(Charsets.US_ASCII)
            var header = 8L
            if (size == 1L) { size = raf.readLong(); header = 16 }
            if (size == 0L) size = end - pos
            if (size < header) return null
            if (name == type) return (pos + header) to (pos + size)
            pos += size
        }
        return null
    }
}
