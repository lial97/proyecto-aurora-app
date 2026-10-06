package app.aurora.player

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class QueueRulesTest {
    @Test
    fun nextWrapsAround() {
        assertEquals(1, QueueRules.nextIndex(0, 3, shuffle = false))
        assertEquals(0, QueueRules.nextIndex(2, 3, shuffle = false))
    }

    @Test
    fun shuffleNeverRepeatsCurrent() {
        val r = Random(42)
        repeat(50) { assertNotEquals(1, QueueRules.nextIndex(1, 4, shuffle = true, random = r)) }
    }

    @Test
    fun previousRestartsAfterThreeSeconds() {
        assertEquals(2 to 0, QueueRules.previous(2, 5, positionSec = 10))
    }

    @Test
    fun previousGoesBackWithinThreeSeconds() {
        assertEquals(1 to 0, QueueRules.previous(2, 5, positionSec = 2))
        assertEquals(4 to 0, QueueRules.previous(0, 5, positionSec = 0))
    }
}
