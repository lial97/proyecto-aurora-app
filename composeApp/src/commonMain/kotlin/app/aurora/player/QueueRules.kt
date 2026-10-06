package app.aurora.player

import kotlin.random.Random

/** Reglas de cola de la spec §8, separadas para poder probarlas. */
object QueueRules {
    /** Si van más de 3 s se reinicia la canción; si no, se va a la anterior. */
    const val RESTART_THRESHOLD_SEC = 3

    fun nextIndex(index: Int, size: Int, shuffle: Boolean, random: Random = Random.Default): Int {
        if (size <= 1) return 0
        if (!shuffle) return (index + 1) % size
        var n: Int
        do n = random.nextInt(size) while (n == index)
        return n
    }

    /** @return el nuevo índice y la nueva posición. */
    fun previous(index: Int, size: Int, positionSec: Int): Pair<Int, Int> =
        if (positionSec > RESTART_THRESHOLD_SEC || size <= 1) index to 0
        else ((index - 1 + size) % size) to 0
}
