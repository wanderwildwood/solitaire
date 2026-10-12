package com.wanderwildwood.solitaire.ui

import com.wanderwildwood.solitaire.game.Card
import com.wanderwildwood.solitaire.game.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FanTest {
    private fun column(down: Int, up: Int) =
        List(down) { Card(13, Suit.SPADES, up = false) } + List(up) { Card(13 - it % 13, Suit.SPADES) }

    @Test fun aShortColumnIsNotSqueezed() {
        val ys = fan(column(3, 4), avail = 600f, cardH = 60f, back = 9f, strip = 29f)
        assertEquals(listOf(0f, 9f, 18f, 27f, 56f, 85f, 114f), ys)
    }

    @Test fun aLongColumnClosesItsBacksFirstAndNeverRunsOff() {
        val avail = 400f
        for ((down, up) in listOf(5 to 10, 5 to 13, 6 to 20, 0 to 40, 30 to 2)) {
            val ys = fan(column(down, up), avail, cardH = 60f, back = 9f, strip = 29f)
            assertTrue("$down/$up runs off: ${ys.last() + 60f}", ys.last() + 60f <= avail + 0.01f)
            assertTrue(ys.zipWithNext().all { (a, b) -> b >= a })
        }
        // 5 backs and 10 up need 45 + 9×29 + 60 = 366: fits untouched.
        assertEquals(45f + 9 * 29f, fan(column(5, 10), avail, 60f, 9f, 29f).last())
        // 5 backs and 13 up need 45 + 12×29 + 60 = 453: the backs close to 3 first.
        val ys = fan(column(5, 13), avail, 60f, 9f, 29f)
        assertEquals(3f, ys[1] - ys[0])
    }
}
