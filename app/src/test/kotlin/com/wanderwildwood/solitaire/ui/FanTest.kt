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

    @Test fun freeCellStripsAreAsTallAsTheTableAllows() {
        // The Kompakt's table: about 390 dp under the top row, a card 63 dp tall.
        val strip = freeCellStrip(avail = 390f, cardH = 63f, least = 22f, most = 36f)
        assertEquals(327f / 11f, strip, 0.001f)
        // A column of twelve then fits exactly, unsqueezed.
        val ys = fan(column(0, 12), 390f, 63f, 7f, strip)
        assertEquals(390f, ys.last() + 63f, 0.01f)
        assertEquals(strip, ys[1] - ys[0], 0.001f)
    }

    @Test fun freeCellStripsStayBetweenTheUsualStripAndTheMost() {
        // A short table: never under the strip the other games use.
        assertEquals(22f, freeCellStrip(avail = 200f, cardH = 63f, least = 22f, most = 36f))
        // A tall one: never over the most.
        assertEquals(36f, freeCellStrip(avail = 900f, cardH = 63f, least = 22f, most = 36f))
        // Limits the wrong way round do not throw.
        assertEquals(40f, freeCellStrip(avail = 900f, cardH = 63f, least = 40f, most = 36f))
    }

    @Test fun aCardBacksLinesAreWholePixelsWithWhiteBetween() {
        assertEquals(5, backPitchPx(5.25f))
        assertEquals(4, backPitchPx(4f))
        assertEquals(3, backPitchPx(1f))
    }
}
