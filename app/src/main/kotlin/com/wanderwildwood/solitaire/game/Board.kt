package com.wanderwildwood.solitaire.game

enum class Kind { KLONDIKE, FREECELL, SPIDER }

/** Somewhere a card can lie. */
sealed interface Pile {
    data class Column(val i: Int) : Pile
    /** A foundation. In Klondike and FreeCell the index is the suit's; Spider has none to play to. */
    data class Home(val i: Int) : Pile
    /** A FreeCell free cell. */
    data class Cell(val i: Int) : Pile
    data object Waste : Pile
}

/**
 * One move. [index] is where a run starts in a column; elsewhere only the top card moves and
 * it is ignored. [Deal] is a tap on the stock: dealing from it, or turning the waste over.
 */
sealed interface Move {
    data class Shift(val from: Pile, val index: Int, val to: Pile) : Move
    data object Deal : Move

    companion object {
        fun encode(m: Move): String = when (m) {
            Deal -> "d"
            is Shift -> pileCode(m.from) + "." + m.index + ">" + pileCode(m.to)
        }

        fun decode(s: String): Move {
            if (s == "d") return Deal
            val (from, rest) = s.split(".", limit = 2)
            val (index, to) = rest.split(">", limit = 2)
            return Shift(pileOf(from), index.toInt(), pileOf(to))
        }

        private fun pileCode(p: Pile): String = when (p) {
            is Pile.Column -> "c${p.i}"
            is Pile.Home -> "h${p.i}"
            is Pile.Cell -> "f${p.i}"
            Pile.Waste -> "w"
        }

        private fun pileOf(s: String): Pile = when (s[0]) {
            'c' -> Pile.Column(s.substring(1).toInt())
            'h' -> Pile.Home(s.substring(1).toInt())
            'f' -> Pile.Cell(s.substring(1).toInt())
            'w' -> Pile.Waste
            else -> throw IllegalArgumentException(s)
        }
    }
}

/**
 * Everything on the table. Lists run bottom to top: the last card of a column is the one
 * showing in full, and the last of the stock is the next one dealt.
 *
 * In Spider [homes] holds the runs that have been completed and taken off, one per entry.
 */
data class Board(
    val columns: List<List<Card>>,
    val homes: List<List<Card>>,
    val cells: List<Card?> = emptyList(),
    val stock: List<Card> = emptyList(),
    val waste: List<Card> = emptyList(),
) {
    fun pile(p: Pile): List<Card> = when (p) {
        is Pile.Column -> columns[p.i]
        is Pile.Home -> homes.getOrElse(p.i) { emptyList() }
        is Pile.Cell -> listOfNotNull(cells[p.i])
        Pile.Waste -> waste
    }

    /** Every card, wherever it lies; used to check a deal and a replay have lost nothing. */
    fun allCards(): List<Card> =
        columns.flatten() + homes.flatten() + cells.filterNotNull() + stock + waste
}
