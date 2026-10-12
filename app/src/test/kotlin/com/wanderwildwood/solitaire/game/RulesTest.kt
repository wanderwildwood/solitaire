package com.wanderwildwood.solitaire.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {

    private fun cards(s: String): List<Card> = s.split(" ").filter { it.isNotBlank() }.map(Card::of)
    private fun down(s: String): List<Card> = cards(s).map { it.faceDown() }

    /** Rows of a published deal listing, read down into eight columns. */
    private fun layout(rows: String): List<List<String>> {
        val r = rows.trim().lines().map { it.trim().split(Regex("\\s+")) }
        return List(8) { c -> r.mapNotNull { it.getOrNull(c) } }
    }

    // ------------------------------------------------------------------ FreeCell

    @Test fun freeCellDeal1MatchesThePublishedLayout() {
        val expected = layout(
            """
            JD 2D 9H JC 5D 7H 7C 5H
            KD KC 9S 5S AD QC KH 3H
            2S KS 9D QD JS AS AH 3C
            4C 5C TS QH 4H AC 4D 7S
            3S TD 4S TH 8H 2C JH 7D
            6D 8S 8D QS 6C 3D 8C TC
            6S 9C 2H 6H
            """,
        )
        assertEquals(expected, Rules.freeCellDeal(1).map { col -> col.map { it.code } })
    }

    @Test fun freeCellDeal617MatchesThePublishedLayout() {
        val expected = layout(
            """
            7D AD 5C 3S 5S 8C 2D AH
            TD 7S QD AC 6D 8H AS KH
            TH QC 3H 9D 6S 8D 3D TC
            KD 5H 9S 3C 8S 7H 4D JS
            4C QS 9C 9H 7C 6H 2C 2S
            4S TS 2H 5D JC 6C JH QH
            JD KS KC 4H
            """,
        )
        assertEquals(expected, Rules.freeCellDeal(617).map { col -> col.map { it.code } })
    }

    @Test fun theDealGeneratorCanFail() {
        // A control: a different deal number must not pass the same comparison.
        assertFalse(Rules.freeCellDeal(2) == Rules.freeCellDeal(1))
    }

    private fun freeCell(columns: List<List<Card>>, cells: List<Card?> = List(4) { null }, homes: List<List<Card>> = List(4) { emptyList() }) =
        Board(columns = columns, homes = homes, cells = cells)

    @Test fun supermoveLimit() {
        val r = Rules(Kind.FREECELL, 0)
        val full = List(8) { cards("KS") }
        assertEquals(5, r.supermoveLimit(freeCell(full), false))
        val twoEmpty = full.mapIndexed { i, c -> if (i < 2) emptyList() else c }
        assertEquals(20, r.supermoveLimit(freeCell(twoEmpty), false))
        assertEquals(10, r.supermoveLimit(freeCell(twoEmpty), true))
        val cellsUsed = freeCell(twoEmpty, cells = listOf(Card.of("2C"), Card.of("3C"), Card.of("4C"), null))
        assertEquals(8, r.supermoveLimit(cellsUsed, false))
        assertEquals(4, r.supermoveLimit(cellsUsed, true))
    }

    @Test fun freeCellRefusesARunLongerThanTheLimit() {
        val r = Rules(Kind.FREECELL, 0)
        val run = cards("9S 8H 7C 6D 5S 4H")
        val cols = listOf(run, cards("TD")) + List(6) { cards("KC") }
        val allCellsFull = listOf(Card.of("2C"), Card.of("2D"), Card.of("2H"), Card.of("2S"))
        val b = freeCell(cols, cells = allCellsFull)
        // No free cells and no empty columns: one card at a time.
        assertFalse(r.canMove(b, Pile.Column(0), 0, Pile.Column(1)))
        val open = freeCell(cols, cells = List(4) { null })
        assertFalse(r.canMove(open, Pile.Column(0), 0, Pile.Column(1))) // 6 cards, limit 5
        val five = freeCell(listOf(cards("9S 8H 7C 6D 5S"), cards("TD")) + List(6) { cards("KC") })
        assertTrue(r.canMove(five, Pile.Column(0), 0, Pile.Column(1)))
    }

    @Test fun freeCellMovesOnlyAlternatingRuns() {
        val r = Rules(Kind.FREECELL, 0)
        val b = freeCell(listOf(cards("9S 8S"), cards("TD")) + List(6) { cards("KC") })
        assertNull(r.pickUp(b, Pile.Column(0), 0))
        assertNotNull(r.pickUp(b, Pile.Column(0), 1))
    }

    @Test fun freeCellTapPrefersSafeFoundationThenColumnThenCell() {
        val r = Rules(Kind.FREECELL, 0)
        val b = freeCell(listOf(cards("AH"), cards("5C 4H"), cards("6D")) + List(5) { cards("KC") })
        assertEquals(Pile.Home(Suit.HEARTS.ordinal), r.choices(b, Pile.Column(0), 0).first())
        assertEquals(listOf(Pile.Column(2)), r.choices(b, Pile.Column(1), 0))
        // A single card that fits nowhere goes to a free cell.
        assertEquals(listOf(Pile.Cell(0)), r.choices(b, Pile.Column(1), 1))
    }

    // ------------------------------------------------------------------ Klondike

    @Test fun klondikeDealIsWhole() {
        val b = Rules(Kind.KLONDIKE, 1).deal(42)
        assertEquals((1..7).toList(), b.columns.map { it.size })
        assertTrue(b.columns.all { c -> c.last().up && c.dropLast(1).none { it.up } })
        assertEquals(24, b.stock.size)
        assertEquals(fullDeck().toSet(), b.allCards().map { it.faceUp() }.toSet())
        assertEquals(b, Rules(Kind.KLONDIKE, 1).deal(42))
        assertFalse(b == Rules(Kind.KLONDIKE, 1).deal(43))
    }

    @Test fun shufflerIsSplitMix64() {
        val s = Shuffler(42)
        assertEquals(-4767286540954276203L, s.next())
        assertEquals(2949826092126892291L, s.next())
    }

    private fun klondike(columns: List<List<Card>>, homes: List<List<Card>> = List(4) { emptyList() }, stock: List<Card> = emptyList(), waste: List<Card> = emptyList()) =
        Board(columns = columns, homes = homes, stock = stock, waste = waste)

    @Test fun klondikeOnlyAKingGoesToAnEmptyColumn() {
        val r = Rules(Kind.KLONDIKE, 1)
        val b = klondike(listOf(emptyList(), cards("KH"), cards("QH"), down("2C") + cards("KS")) + List(3) { cards("AC") })
        assertTrue(r.canMove(b, Pile.Column(1), 0, Pile.Column(0))) // legal, but...
        assertFalse(r.canMove(b, Pile.Column(2), 0, Pile.Column(0)))
        assertTrue(r.canMove(b, Pile.Column(3), 1, Pile.Column(0)))
        // ...a king already at the bottom of its column is never offered an empty one.
        assertFalse(Pile.Column(0) in r.choices(b, Pile.Column(1), 0))
    }

    @Test fun klondikeRedOnBlackOneLower() {
        val r = Rules(Kind.KLONDIKE, 1)
        val b = klondike(listOf(cards("9S"), cards("8H"), cards("8C"), cards("7H")) + List(3) { cards("KC") })
        assertTrue(r.canMove(b, Pile.Column(1), 0, Pile.Column(0)))
        assertFalse(r.canMove(b, Pile.Column(2), 0, Pile.Column(0)))
        assertFalse(r.canMove(b, Pile.Column(3), 0, Pile.Column(0)))
    }

    @Test fun klondikeMoveTurnsTheUncoveredCardUp() {
        val r = Rules(Kind.KLONDIKE, 1)
        val b = klondike(listOf(cards("9S"), down("3D") + cards("8H")) + List(5) { cards("KC") })
        val after = r.play(b, Move.Shift(Pile.Column(1), 1, Pile.Column(0)))!!
        assertEquals(cards("3D"), after.columns[1])
        assertTrue(after.columns[1].single().up)
    }

    @Test fun klondikeDrawThreeAndTurningTheWasteOver() {
        val r = Rules(Kind.KLONDIKE, 3)
        val b = klondike(List(7) { cards("KC") }, stock = down("2C 3C 4C 5C"))
        val one = r.play(b, Move.Deal)!!
        assertEquals(cards("5C 4C 3C"), one.waste)
        val two = r.play(one, Move.Deal)!!
        assertEquals(cards("5C 4C 3C 2C"), two.waste)
        val over = r.play(two, Move.Deal)!!
        assertEquals(down("2C 3C 4C 5C"), over.stock)
        assertTrue(over.waste.isEmpty())
        assertEquals(b.stock, over.stock)
    }

    @Test fun klondikeSafeFoundation() {
        val r = Rules(Kind.KLONDIKE, 1)
        val homes = listOf(cards("AC"), cards("AD 2D"), cards("AH 2H 3H"), cards("AS"))
        val b = klondike(listOf(cards("4H"), cards("5S")) + List(5) { cards("KC") }, homes = homes)
        // 4H is legal home, but the black twos are not up yet: the column comes first.
        assertFalse(r.safeHome(b, Card.of("4H")))
        assertEquals(listOf(Pile.Column(1), Pile.Home(Suit.HEARTS.ordinal)), r.choices(b, Pile.Column(0), 0))
        assertTrue(r.safeHome(b, Card.of("2C")))
    }

    // ------------------------------------------------------------------ Spider

    @Test fun spiderDealsBySuits() {
        for ((suits, kinds) in listOf(1 to 1, 2 to 2, 4 to 4)) {
            val b = Rules(Kind.SPIDER, suits).deal(7)
            assertEquals(listOf(6, 6, 6, 6, 5, 5, 5, 5, 5, 5), b.columns.map { it.size })
            assertEquals(50, b.stock.size)
            val all = b.allCards()
            assertEquals(104, all.size)
            assertEquals(kinds, all.map { it.suit }.toSet().size)
            assertTrue(all.groupBy { it.rank }.values.all { it.size == 8 })
        }
    }

    private fun spider(columns: List<List<Card>>, stock: List<Card> = emptyList(), homes: List<List<Card>> = emptyList()) =
        Board(columns = columns, homes = homes, stock = stock)

    @Test fun spiderRemovesACompletedRun() {
        val r = Rules(Kind.SPIDER, 1)
        val col0 = down("5H") + cards("KS QS JS TS 9S 8S 7S 6S 5S 4S 3S 2S")
        val b = spider(listOf(col0, cards("AS")) + List(8) { cards("KH") })
        val after = r.play(b, Move.Shift(Pile.Column(1), 0, Pile.Column(0)))!!
        assertEquals(1, after.homes.size)
        assertEquals(13, after.homes[0].size)
        assertEquals(listOf(Card.of("5H")), after.columns[0])
        assertTrue(after.columns[0].single().up)
        assertTrue(after.columns[1].isEmpty())
    }

    @Test fun spiderMovesOnlySameSuitRuns() {
        val r = Rules(Kind.SPIDER, 2)
        val b = spider(listOf(cards("9S 8H"), cards("9H 8H"), cards("TD")) + List(7) { cards("KC") })
        assertNull(r.pickUp(b, Pile.Column(0), 0))
        assertNotNull(r.pickUp(b, Pile.Column(1), 0))
        assertTrue(r.canMove(b, Pile.Column(1), 0, Pile.Column(2)))
    }

    @Test fun spiderPrefersTheSameSuit() {
        val r = Rules(Kind.SPIDER, 2)
        val b = spider(listOf(cards("8H"), cards("9S"), cards("9H")) + List(7) { cards("KC") })
        assertEquals(listOf(Pile.Column(2), Pile.Column(1)), r.choices(b, Pile.Column(0), 0))
    }

    @Test fun spiderWillNotDealOverAnEmptyColumn() {
        val r = Rules(Kind.SPIDER, 1)
        val stock = List(10) { Card.of("5S").faceDown() }
        val b = spider(listOf(emptyList<Card>()) + List(9) { cards("KS") }, stock = stock)
        assertTrue(r.stockBlocked(b))
        assertNull(r.play(b, Move.Deal))
        val filled = spider(List(10) { cards("KS") }, stock = stock)
        val dealt = r.play(filled, Move.Deal)!!
        assertTrue(dealt.columns.all { it.size == 2 && it.last().up })
        assertTrue(dealt.stock.isEmpty())
    }

    // ------------------------------------------------------------------ finishing

    @Test fun finishIsOfferedOnlyWhenEverythingIsUpAndItReallyFinishes() {
        val r = Rules(Kind.KLONDIKE, 1)
        // Everything up and in order: finishes.
        val homes = listOf(cards("AC 2C 3C 4C 5C 6C 7C 8C 9C TC JC"), cards("AD 2D 3D 4D 5D 6D 7D 8D 9D TD JD QD"),
            cards("AH 2H 3H 4H 5H 6H 7H 8H 9H TH JH QH"), cards("AS 2S 3S 4S 5S 6S 7S 8S 9S TS JS QS"))
        val nearly = klondike(listOf(cards("KD QC"), cards("KH"), cards("KS"), cards("KC")) + List(3) { emptyList() }, homes = homes)
        val moves = r.finish(nearly)
        assertNotNull(moves)
        var b = nearly
        for (m in moves!!) b = r.play(b, m)!!
        assertTrue(r.won(b))
        // A card face down: not offered.
        val hidden = nearly.copy(columns = nearly.columns.replace(0) { listOf(it[0].faceDown(), it[1]) })
        assertNull(r.finish(hidden))
        // Stock not empty: not offered.
        assertNull(r.finish(nearly.copy(stock = down("KC"))))
    }

    @Test fun freeCellFinishRefusesABlockedBoard() {
        val r = Rules(Kind.FREECELL, 0)
        // Everything face up, but the two of clubs is under the three: no finish.
        val homes = listOf(cards("AC"), emptyList(), emptyList(), emptyList())
        val rest = fullDeck().filter { it.code !in setOf("AC", "2C", "3C") }
        val cols = listOf(cards("2C 3C")) + rest.chunked(7)
        val b = freeCell(cols, homes = homes, cells = List(4) { null })
        assertNull(r.finish(b))
    }

    @Test fun spiderFinishJoinsRuns() {
        val r = Rules(Kind.SPIDER, 1)
        val cols = listOf(cards("KS QS JS TS 9S 8S 7S"), cards("6S 5S 4S 3S 2S AS")) + List(8) { emptyList() }
        val b = spider(cols, homes = List(7) { cards("KS QS JS TS 9S 8S 7S 6S 5S 4S 3S 2S AS") })
        val moves = r.finish(b)!!
        var x = b
        for (m in moves) x = r.play(x, m)!!
        assertTrue(r.won(x))
    }
}
