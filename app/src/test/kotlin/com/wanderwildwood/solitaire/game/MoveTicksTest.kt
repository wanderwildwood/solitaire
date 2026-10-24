package com.wanderwildwood.solitaire.game

import com.wanderwildwood.solitaire.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveTicksTest {
    @Test fun buzzIsOffToBeginWith() {
        assertFalse(Store.BUZZ_DEFAULT)
        assertEquals("buzz", Store.BUZZ)
    }

    @Test fun onlyAnAcceptedTurnIsCounted() {
        val s = Session.start(Kind.KLONDIKE, 1, 7L)
        val ticks = MoveTicks()
        // Nothing to undo on a fresh deal: refused, so no tick.
        assertFalse(ticks.after(s.undo()))
        assertEquals(0, ticks.count)
        // The stock can always be drawn from at the start: one tick.
        assertTrue(ticks.after(s.deal()))
        assertEquals(1, ticks.count)
        // A tap on a face-down card goes nowhere.
        assertFalse(ticks.after(s.tap(Pile.Column(6), 0)))
        assertEquals(1, ticks.count)
        // Undoing the draw is a move too.
        assertTrue(ticks.after(s.undo()))
        assertEquals(2, ticks.count)
    }
}
