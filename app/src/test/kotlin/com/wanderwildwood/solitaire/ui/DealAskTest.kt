package com.wanderwildwood.solitaire.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DealAskTest {
    @Test fun aGameUnderWayIsAskedForTwice() = assertTrue(dealAsksFirst(won = false, moves = 3))

    @Test fun aFreshDealIsNotAsked() = assertFalse(dealAsksFirst(won = false, moves = 0))

    @Test fun aWonGameIsNotAsked() = assertFalse(dealAsksFirst(won = true, moves = 80))
}
