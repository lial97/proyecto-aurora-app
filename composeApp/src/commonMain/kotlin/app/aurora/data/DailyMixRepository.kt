package app.aurora.data

import app.aurora.platform.KeyValueStore

/** Respuesta a "¿Te gustó la mezcla de hoy?". */
enum class MixAnswer { PENDING, YES, NO }

/** La mezcla guardada: de qué día es, sus canciones, la respuesta y cuántas de ellas sonaron. */
data class SavedDailyMix(val dateKey: String, val trackIds: List<String>, val answer: MixAnswer, val played: Int)

/**
 * Guarda "Tu mezcla de hoy". Cada día nuevo se crea otra y la anterior se descarta: si gustó ya se guardó como lista
 * propia (con "Sí"), y si no gustó o no hubo respuesta simplemente se reemplaza.
 */
class DailyMixRepository(private val store: KeyValueStore) {
    fun current(): SavedDailyMix? {
        val date = store.get(KEY_DATE) ?: return null
        val ids = store.get(KEY_IDS).orEmpty().split('\n').filter { it.isNotEmpty() }
        val answer = runCatching { MixAnswer.valueOf(store.get(KEY_ANSWER).orEmpty()) }.getOrDefault(MixAnswer.PENDING)
        return SavedDailyMix(date, ids, answer, store.get(KEY_PLAYED)?.toIntOrNull() ?: 0)
    }

    /**
     * La mezcla de [dateKey]: la guardada si es de ese día; si no, una nueva hecha con [build] (que recibe las canciones
     * de la anterior, para no repetirlas).
     */
    fun ensure(dateKey: String, build: (previous: Set<String>) -> List<String>): SavedDailyMix {
        current()?.takeIf { it.dateKey == dateKey }?.let { return it }
        val previous = current()?.trackIds?.toSet().orEmpty()
        val mix = SavedDailyMix(dateKey, build(previous), MixAnswer.PENDING, 0)
        save(mix)
        return mix
    }

    /** Sonó una canción de la mezcla (solo cuenta mientras no hay respuesta). Devuelve cuántas van. */
    fun recordPlay(trackId: String): Int {
        val m = current() ?: return 0
        if (trackId !in m.trackIds || m.answer != MixAnswer.PENDING) return m.played
        val n = m.played + 1
        store.put(KEY_PLAYED, n.toString())
        return n
    }

    fun answer(yes: Boolean) = store.put(KEY_ANSWER, (if (yes) MixAnswer.YES else MixAnswer.NO).name)

    private fun save(m: SavedDailyMix) {
        store.put(KEY_DATE, m.dateKey)
        store.put(KEY_IDS, m.trackIds.joinToString("\n"))
        store.put(KEY_ANSWER, m.answer.name)
        store.put(KEY_PLAYED, m.played.toString())
    }

    private companion object {
        const val KEY_DATE = "mezcla_dia.fecha"
        const val KEY_IDS = "mezcla_dia.canciones"
        const val KEY_ANSWER = "mezcla_dia.respuesta"
        const val KEY_PLAYED = "mezcla_dia.escuchadas"
    }
}
