package app.aurora.screens

import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val days = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
private val months = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")

private fun now() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

fun todayLabel(): String = now().let { "${days[it.dayOfWeek.ordinal]}, ${it.day} de ${months[it.month.ordinal]}" }

/** "Buenos días" (5 a 12), "Buenas tardes" (12 a 19) o "Buenas noches" (19 a 5), con el nombre si lo hay. */
fun greetingFor(hour: Int, name: String = ""): String {
    val base = when (hour) {
        in 5..11 -> "Buenos días"
        in 12..18 -> "Buenas tardes"
        else -> "Buenas noches"
    }
    return if (name.isBlank()) base else "$base, ${name.trim()}"
}

fun greeting(name: String = ""): String = greetingFor(now().hour, name)
