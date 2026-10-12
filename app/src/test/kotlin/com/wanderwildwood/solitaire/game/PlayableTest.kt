package com.wanderwildwood.solitaire.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Games played to the end through [Session.tap] alone, the way a thumb plays them: each
 * solver move becomes a tap on the card, and further taps on the same card until it lies
 * where the solver wanted it. Printed as a script the emulator can play.
 */
class PlayableTest {

    private fun pileCode(p: Pile) = when (p) {
        is Pile.Column -> "c${p.i}"
        is Pile.Home -> "h${p.i}"
        is Pile.Cell -> "f${p.i}"
        Pile.Waste -> "w"
    }

    /** Plays [moves] by taps, finishing with the Finish button once it is offered. */
    private fun playByTaps(s: Session, moves: List<Move>): List<String> {
        val script = mutableListOf<String>()
        var landed: Pair<Pile, Int>? = null
        for (m in moves) {
            if (s.finishing() != null) {
                script += "FINISH"
                assertTrue(s.finish())
                break
            }
            val expected = s.rules.play(s.board, m)!!
            when (m) {
                Move.Deal -> { script += "DEAL"; assertTrue(s.deal()); landed = null }
                is Move.Shift -> {
                    val choices = s.rules.choices(s.board, m.from, m.index)
                    val k = choices.indexOf(m.to)
                    assertTrue("solver move not offered by a tap", k >= 0)
                    val orig = s.board
                    var at = m.from to m.index
                    if (at == landed) {
                        // The same card again, from where it was just put: a tap there would
                        // send it on round the last move's places, so tap the bare table first.
                        script += "RESET"
                        s.forget()
                    }
                    for (j in 0..k) {
                        script += "TAP ${pileCode(at.first)} ${at.second}"
                        assertTrue(s.tap(at.first, at.second))
                        // Each try starts again from the board before the move.
                        at = choices[j] to (if (choices[j] is Pile.Cell) 0 else orig.pile(choices[j]).size)
                    }
                    landed = at
                }
            }
            assertEquals(expected, s.board)
        }
        assertTrue(s.won)
        return script
    }

    private fun print(name: String, s: Session, script: List<String>) {
        println("=== $name ${s.kind} option=${s.option} seed=${s.seed} taps=${script.size}")
        script.forEach(::println)
        println("=== end")
        println("TURNS " + s.encode())
    }

    @Test fun freeCellDeal1PlaysOutByTaps() {
        val s = Session.start(Kind.FREECELL, 0, 1)
        val moves = Solver.solve(s.rules, s.board)
        assertNotNull(moves)
        print("freecell", s, playByTaps(s, moves!!))
    }

    @Test fun aKlondikeDealPlaysOutByTaps() {
        val (seed, moves) = (1L..60L).firstNotNullOf { seed ->
            val r = Rules(Kind.KLONDIKE, 1)
            Solver.solve(r, r.deal(seed), 60_000)?.let { seed to it }
        }
        val s = Session.start(Kind.KLONDIKE, 1, seed)
        print("klondike", s, playByTaps(s, moves))
    }

    @Test fun aOneSuitSpiderDealPlaysOutByTaps() {
        val (seed, moves) = (1L..30L).firstNotNullOf { seed ->
            val r = Rules(Kind.SPIDER, 1)
            Solver.solve(r, r.deal(seed), 60_000)?.let { seed to it }
        }
        val s = Session.start(Kind.SPIDER, 1, seed)
        print("spider", s, playByTaps(s, moves))
    }
}
