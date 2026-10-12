package com.wanderwildwood.solitaire.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SessionTest {

    /** Every tap the board offers right now that would move something. */
    private fun taps(s: Session): List<Pair<Pile, Int>> {
        val b = s.board
        val out = mutableListOf<Pair<Pile, Int>>()
        b.columns.forEachIndexed { c, col -> col.indices.forEach { out += Pile.Column(c) to it } }
        b.cells.forEachIndexed { i, card -> if (card != null) out += Pile.Cell(i) to 0 }
        b.homes.forEachIndexed { i, h -> if (h.isNotEmpty() && s.kind != Kind.SPIDER) out += Pile.Home(i) to h.lastIndex }
        if (b.waste.isNotEmpty()) out += Pile.Waste to b.waste.lastIndex
        return out.filter { (p, i) -> s.rules.choices(b, p, i).isNotEmpty() }
    }

    /** Plays a few hundred random taps and deals, keeping every board seen on the way. */
    private fun wander(s: Session, rnd: Random, steps: Int): List<Board> {
        val seen = mutableListOf(s.board)
        repeat(steps) {
            val t = taps(s)
            val moved = if (t.isNotEmpty() && rnd.nextInt(4) != 0) {
                val (p, i) = t[rnd.nextInt(t.size)]
                s.tap(p, i)
            } else {
                s.deal()
            }
            // A tap on the card just moved sends it on instead, or back: the count can stay or drop.
            when (s.moves) {
                seen.size -> seen += s.board
                seen.size - 1 -> seen[seen.lastIndex] = s.board
                seen.size - 2 -> seen.removeAt(seen.lastIndex)
                else -> throw AssertionError("moves jumped to ${s.moves}")
            }
            assertTrue(moved || seen.last() == s.board)
            assertEquals(seen.size - 1, s.moves)
            assertEquals(s.board.allCards().size, s.boards.first().allCards().size)
        }
        return seen
    }

    @Test fun undoRestoresEveryBoardExactly() {
        for ((kind, option) in listOf(Kind.KLONDIKE to 1, Kind.KLONDIKE to 3, Kind.FREECELL to 0, Kind.SPIDER to 1, Kind.SPIDER to 4)) {
            val s = Session.start(kind, option, if (kind == Kind.FREECELL) 617 else 99)
            val seen = wander(s, Random(5), 300)
            assertTrue("$kind made no moves", seen.size > 10)
            for (i in seen.indices.reversed()) {
                assertEquals("$kind at move $i", seen[i], s.board)
                if (i > 0) assertTrue(s.undo())
            }
            assertFalse(s.undo())
            assertEquals(s.rules.deal(s.seed), s.board)
        }
    }

    @Test fun saveAndLoadRoundTrip() {
        for ((kind, option) in listOf(Kind.KLONDIKE to 3, Kind.FREECELL to 0, Kind.SPIDER to 2)) {
            val s = Session.start(kind, option, if (kind == Kind.FREECELL) 1 else 1234567890123L)
            wander(s, Random(11), 200)
            s.counted = true
            val back = Session.decode(s.encode())
            assertNotNull(back)
            back!!
            assertEquals(s.kind, back.kind)
            assertEquals(s.option, back.option)
            assertEquals(s.seed, back.seed)
            assertEquals(s.turns, back.turns)
            assertEquals(s.boards, back.boards)
            assertTrue(back.counted)
            assertEquals(s.encode(), back.encode())
        }
    }

    @Test fun aDamagedSaveIsRefused() {
        val s = Session.start(Kind.FREECELL, 0, 1)
        s.tap(Pile.Column(0), 6)
        val good = s.encode()
        assertNotNull(Session.decode(good))
        assertNull(Session.decode(good.replace("c0.6", "c0.2")))
        assertNull(Session.decode("nonsense"))
    }

    @Test fun tappingAgainCyclesThroughThePlacesAndBack() {
        // A one-suit Spider deal where some card fits on two columns.
        val r = Rules(Kind.SPIDER, 1)
        val (seed, spot) = (1L..200L).firstNotNullOf { seed ->
            val b = r.deal(seed)
            b.columns.indices.map { Pile.Column(it) to b.columns[it].lastIndex }
                .firstOrNull { (p, i) -> r.choices(b, p, i).size >= 2 }?.let { seed to it }
        }
        val (pile, index) = spot
        val s = Session.start(Kind.SPIDER, 1, seed)
        val b = s.board
        val choices = r.choices(b, pile, index)
        val card = b.pile(pile)[index]

        assertTrue(s.tap(pile, index))
        assertEquals(1, s.moves)
        var where = choices[0]
        assertEquals(card, s.board.pile(where).last())
        for (k in 1 until choices.size) {
            val landed = if (where is Pile.Cell) 0 else s.board.pile(where).lastIndex
            assertTrue(s.tap(where, landed))
            where = choices[k]
            assertEquals(card, s.board.pile(where).last())
            assertEquals(1, s.moves)
        }
        // After the last place it goes back where it was, and the move is gone.
        val landed = if (where is Pile.Cell) 0 else s.board.pile(where).lastIndex
        assertTrue(s.tap(where, landed))
        assertEquals(b, s.board)
        assertEquals(0, s.moves)
        // And round again.
        assertTrue(s.tap(pile, index))
        assertEquals(card, s.board.pile(choices[0]).last())
    }

    @Test fun aTapWithNowhereToGoMovesNothing() {
        val s = Session.start(Kind.KLONDIKE, 1, 3)
        val b = s.board
        val stuck = b.columns.indices.firstOrNull { s.rules.choices(b, Pile.Column(it), b.columns[it].lastIndex).isEmpty() }
        assertNotNull(stuck)
        assertFalse(s.tap(Pile.Column(stuck!!), b.columns[stuck].lastIndex))
        assertEquals(b, s.board)
        // A face-down card never moves.
        assertFalse(s.tap(Pile.Column(6), 0))
        assertEquals(0, s.moves)
    }

    @Test fun finishingIsOneTurnAndUndoesWhole() {
        // Play deal 1 of FreeCell out by a simple search, then check the last stretch finishes.
        val s = Session.start(Kind.FREECELL, 0, 1)
        val solved = Solver.solve(s.rules, s.board)
        assertNotNull("deal 1 is solvable", solved)
        var finishedAt = -1
        for (m in solved!!) {
            if (s.finishing() != null) { finishedAt = s.moves; break }
            assertTrue(s.play(listOf(m)))
        }
        assertTrue(finishedAt >= 0)
        val before = s.board
        assertTrue(s.finish())
        assertTrue(s.won)
        assertEquals(finishedAt + 1, s.moves)
        assertTrue(s.undo())
        assertEquals(before, s.board)
    }
}
