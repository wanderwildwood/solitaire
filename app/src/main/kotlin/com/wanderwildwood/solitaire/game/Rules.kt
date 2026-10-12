package com.wanderwildwood.solitaire.game

/**
 * The rules of one game, played with one option: cards drawn at a time in Klondike (1 or 3),
 * suits in Spider (1, 2 or 4); FreeCell has none.
 *
 * Pure functions over [Board]. Nothing here knows about the screen.
 */
class Rules(val kind: Kind, val option: Int) {

    // ------------------------------------------------------------------ dealing

    /** The opening deal. In FreeCell [seed] is the deal number. */
    fun deal(seed: Long): Board = when (kind) {
        Kind.KLONDIKE -> klondikeDeal(seed)
        Kind.FREECELL -> Board(
            columns = freeCellDeal(seed.toInt()),
            homes = List(4) { emptyList() },
            cells = List(4) { null },
        )
        Kind.SPIDER -> spiderDeal(seed)
    }

    private fun klondikeDeal(seed: Long): Board {
        val deck = Shuffler(seed).shuffle(fullDeck())
        val columns = List(7) { mutableListOf<Card>() }
        var n = 0
        for (row in 0 until 7) for (c in row until 7) columns[c] += deck[n++].faceDown()
        return Board(
            columns = columns.map { col -> col.mapIndexed { i, card -> if (i == col.lastIndex) card.faceUp() else card } },
            homes = List(4) { emptyList() },
            stock = deck.drop(n).map { it.faceDown() },
        )
    }

    private fun spiderDeal(seed: Long): Board {
        val suits = when (option) {
            1 -> List(8) { Suit.SPADES }
            2 -> List(4) { Suit.SPADES } + List(4) { Suit.HEARTS }
            else -> List(2) { Suit.entries }.flatten()
        }
        val deck = Shuffler(seed).shuffle(suits.flatMap { s -> (1..13).map { Card(it, s) } })
        val columns = List(10) { mutableListOf<Card>() }
        for (n in 0 until 54) columns[n % 10] += deck[n].faceDown()
        return Board(
            columns = columns.map { col -> col.mapIndexed { i, card -> if (i == col.lastIndex) card.faceUp() else card } },
            homes = emptyList(),
            stock = deck.drop(54).map { it.faceDown() },
        )
    }

    // ------------------------------------------------------------------ what may move where

    /** The cards a tap at [index] of [from] would pick up, or null if they cannot move together. */
    fun pickUp(b: Board, from: Pile, index: Int): List<Card>? {
        val pile = b.pile(from)
        if (pile.isEmpty()) return null
        return when (from) {
            is Pile.Column -> {
                if (index !in pile.indices) return null
                val run = pile.subList(index, pile.size)
                if (!run.all { it.up }) return null
                val joined = if (kind == Kind.SPIDER) ::sameSuitDown else ::alternatingDown
                if (run.zipWithNext().all { (a, c) -> joined(a, c) }) run else null
            }
            // FreeCell's foundations are final; Klondike lets a card come back down.
            is Pile.Home -> if (kind == Kind.KLONDIKE) listOf(pile.last()) else null
            is Pile.Cell, Pile.Waste -> listOf(pile.last())
        }
    }

    private fun alternatingDown(a: Card, c: Card) = a.red != c.red && a.rank == c.rank + 1
    private fun sameSuitDown(a: Card, c: Card) = a.suit == c.suit && a.rank == c.rank + 1

    /** FreeCell's limit on a run moved at once: (free cells + 1) × 2^(empty columns), halved into an empty column. */
    fun supermoveLimit(b: Board, toEmptyColumn: Boolean): Int {
        val free = b.cells.count { it == null }
        val empty = b.columns.count { it.isEmpty() } - if (toEmptyColumn) 1 else 0
        return (free + 1) shl empty.coerceAtLeast(0)
    }

    fun canMove(b: Board, from: Pile, index: Int, to: Pile): Boolean {
        if (from == to) return false
        val run = pickUp(b, from, index) ?: return false
        val first = run.first()
        return when (to) {
            is Pile.Home -> kind != Kind.SPIDER && run.size == 1 &&
                first.suit.ordinal == to.i && first.rank == b.homes[to.i].size + 1
            is Pile.Cell -> kind == Kind.FREECELL && run.size == 1 && b.cells[to.i] == null
            Pile.Waste -> false
            is Pile.Column -> {
                val target = b.columns[to.i]
                if (from is Pile.Home && kind != Kind.KLONDIKE) return false
                val fits = when {
                    target.isEmpty() -> kind != Kind.KLONDIKE || first.rank == 13
                    !target.last().up -> false
                    kind == Kind.SPIDER -> target.last().rank == first.rank + 1
                    else -> alternatingDown(target.last(), first)
                }
                fits && (kind != Kind.FREECELL || run.size <= supermoveLimit(b, target.isEmpty()))
            }
        }
    }

    /**
     * Whether a card can go to its foundation without being wanted lower down: an ace or a
     * two always can, anything else once both foundations of the other colour are no more
     * than one below it.
     */
    fun safeHome(b: Board, card: Card): Boolean {
        if (card.rank <= 2) return true
        return Suit.entries.filter { it.red != card.red }.all { b.homes[it.ordinal].size >= card.rank - 1 }
    }

    /**
     * Where a tap sends what it picks up, best first. The foundation leads when going there
     * is safe; then the columns from the left, a card of the same suit first in Spider; then
     * an empty column; then a free cell; and last the foundation when going there is legal
     * but not safe. Tapping the same card again walks down this list.
     */
    fun choices(b: Board, from: Pile, index: Int): List<Pile> {
        val run = pickUp(b, from, index) ?: return emptyList()
        val first = run.first()
        val out = mutableListOf<Pile>()
        val home = if (kind != Kind.SPIDER) Pile.Home(first.suit.ordinal) else null
        val homeOk = home != null && canMove(b, from, index, home)
        if (homeOk && safeHome(b, first)) out += home!!

        val columns = b.columns.indices.map { Pile.Column(it) }.filter { canMove(b, from, index, it) }
        val (empty, filled) = columns.partition { b.columns[it.i].isEmpty() }
        if (kind == Kind.SPIDER) {
            out += filled.filter { b.columns[it.i].last().suit == first.suit }
            out += filled.filter { b.columns[it.i].last().suit != first.suit }
        } else {
            out += filled
        }
        // A whole column moved to an empty one changes nothing.
        val wholeColumn = from is Pile.Column && index == 0
        if (!wholeColumn) empty.firstOrNull()?.let { out += it }

        if (kind == Kind.FREECELL && from !is Pile.Cell) {
            b.cells.indices.firstOrNull { canMove(b, from, index, Pile.Cell(it)) }?.let { out += Pile.Cell(it) }
        }
        if (homeOk && home !in out) out += home!!
        return out
    }

    // ------------------------------------------------------------------ making moves

    /** The board after [m]; null when it is not a legal move. */
    fun play(b: Board, m: Move): Board? = when (m) {
        Move.Deal -> dealStock(b)
        is Move.Shift -> if (canMove(b, m.from, m.index, m.to)) shift(b, m) else null
    }

    private fun shift(b: Board, m: Move.Shift): Board {
        val run = pickUp(b, m.from, m.index)!!
        var next = take(b, m.from, run.size)
        next = put(next, m.to, run)
        if (m.from is Pile.Column) next = turnOver(next, m.from.i)
        if (kind == Kind.SPIDER && m.to is Pile.Column) next = completeRun(next, m.to.i)
        return next
    }

    private fun take(b: Board, p: Pile, n: Int): Board = when (p) {
        is Pile.Column -> b.copy(columns = b.columns.replace(p.i) { it.dropLast(n) })
        is Pile.Home -> b.copy(homes = b.homes.replace(p.i) { it.dropLast(n) })
        is Pile.Cell -> b.copy(cells = b.cells.toMutableList().also { it[p.i] = null })
        Pile.Waste -> b.copy(waste = b.waste.dropLast(n))
    }

    private fun put(b: Board, p: Pile, run: List<Card>): Board = when (p) {
        is Pile.Column -> b.copy(columns = b.columns.replace(p.i) { it + run })
        is Pile.Home -> b.copy(homes = b.homes.replace(p.i) { it + run })
        is Pile.Cell -> b.copy(cells = b.cells.toMutableList().also { it[p.i] = run.single() })
        Pile.Waste -> b.copy(waste = b.waste + run)
    }

    /** The card a move uncovers turns face up. */
    private fun turnOver(b: Board, col: Int): Board {
        val c = b.columns[col]
        if (c.isEmpty() || c.last().up) return b
        return b.copy(columns = b.columns.replace(col) { it.dropLast(1) + it.last().faceUp() })
    }

    /** Spider: a king-to-ace run of one suit at the foot of a column comes off the table. */
    private fun completeRun(b: Board, col: Int): Board {
        val c = b.columns[col]
        if (c.size < 13) return b
        val tail = c.takeLast(13)
        val done = tail.all { it.up && it.suit == tail[0].suit } &&
            tail.mapIndexed { i, card -> card.rank == 13 - i }.all { it }
        if (!done) return b
        val next = b.copy(columns = b.columns.replace(col) { it.dropLast(13) }, homes = b.homes + listOf(tail))
        return turnOver(next, col)
    }

    /** Why the stock cannot be dealt from, if it cannot. */
    fun stockBlocked(b: Board): Boolean =
        kind == Kind.SPIDER && b.stock.isNotEmpty() && b.columns.any { it.isEmpty() }

    private fun dealStock(b: Board): Board? = when (kind) {
        Kind.FREECELL -> null
        Kind.KLONDIKE -> when {
            b.stock.isNotEmpty() -> {
                val n = minOf(option, b.stock.size)
                // Taken from the top one at a time, so the last one taken is the one on top.
                val drawn = b.stock.takeLast(n).reversed().map { it.faceUp() }
                b.copy(stock = b.stock.dropLast(n), waste = b.waste + drawn)
            }
            b.waste.isNotEmpty() -> b.copy(stock = b.waste.reversed().map { it.faceDown() }, waste = emptyList())
            else -> null
        }
        Kind.SPIDER -> if (b.stock.isEmpty() || stockBlocked(b)) null else {
            val drawn = b.stock.takeLast(10).reversed()
            var next = b.copy(
                stock = b.stock.dropLast(10),
                columns = b.columns.mapIndexed { i, c -> c + drawn[i].faceUp() },
            )
            for (i in next.columns.indices) next = completeRun(next, i)
            next
        }
    }

    // ------------------------------------------------------------------ the end

    fun won(b: Board): Boolean = when (kind) {
        Kind.SPIDER -> b.homes.size == 8
        else -> b.homes.sumOf { it.size } == 52
    }

    /**
     * The moves that finish the game from here with nothing left to decide, or null when it
     * is not that far along. Offered only when every card is face up and the stock is spent,
     * and only when playing the moves out actually reaches the end, so it can never strand a
     * game halfway.
     */
    fun finish(b: Board): List<Move>? {
        if (won(b)) return null
        if (b.stock.isNotEmpty() || b.columns.any { c -> c.any { !it.up } }) return null
        val moves = mutableListOf<Move>()
        var board = b
        var idle = 0
        while (!won(board)) {
            if (moves.size > 2000) return null
            val m = nextFinishingMove(board)
            if (m != null) {
                board = play(board, m) ?: return null
                moves += m
                idle = 0
            } else if (kind == Kind.KLONDIKE && (board.stock.isNotEmpty() || board.waste.isNotEmpty())) {
                // Turning the waste over is fine while it leads somewhere; a full pass with
                // nothing going home means it never will.
                if (++idle > board.stock.size + board.waste.size + 2) return null
                board = play(board, Move.Deal) ?: return null
                moves += Move.Deal
            } else {
                return null
            }
        }
        return moves
    }

    private fun nextFinishingMove(b: Board): Move? {
        if (kind == Kind.SPIDER) {
            // Join a column's same-suit run onto the card of that suit one above it.
            for (from in b.columns.indices) {
                val c = b.columns[from]
                if (c.isEmpty()) continue
                var start = c.lastIndex
                while (start > 0 && sameSuitDown(c[start - 1], c[start])) start--
                for (to in b.columns.indices) {
                    val t = b.columns[to]
                    if (to == from || t.isEmpty()) continue
                    if (t.last().suit == c[start].suit && t.last().rank == c[start].rank + 1) {
                        return Move.Shift(Pile.Column(from), start, Pile.Column(to))
                    }
                }
            }
            return null
        }
        val sources = b.columns.indices.filter { b.columns[it].isNotEmpty() }
            .map { Pile.Column(it) to b.columns[it].lastIndex } +
            b.cells.indices.filter { b.cells[it] != null }.map { Pile.Cell(it) to 0 } +
            (if (b.waste.isNotEmpty()) listOf(Pile.Waste to 0) else emptyList())
        // Lowest card first, so nothing is sent home before what it needs.
        return sources.map { (p, i) -> Triple(p, i, b.pile(p).last()) }
            .sortedBy { it.third.rank }
            .firstOrNull { (p, i, card) -> canMove(b, p, i, Pile.Home(card.suit.ordinal)) }
            ?.let { (p, i, card) -> Move.Shift(p, i, Pile.Home(card.suit.ordinal)) }
    }

    companion object {
        /**
         * Microsoft's FreeCell deal [n]: its C runtime's rand() over a reversed deck, the cards
         * dealt row by row across eight columns. Deals 1 to 32000 are the ones it offered.
         */
        fun freeCellDeal(n: Int): List<List<Card>> {
            var seed = n.toLong() and 0xffffffffL
            val cards = (51 downTo 0).toMutableList()
            for (i in 0 until 52) {
                seed = (seed * 214013 + 2531011) and 0x7fffffff
                val r = (seed shr 16).toInt()
                val j = 51 - r % (52 - i)
                val t = cards[i]; cards[i] = cards[j]; cards[j] = t
            }
            val columns = List(8) { mutableListOf<Card>() }
            cards.forEachIndexed { i, c -> columns[i % 8] += Card(c / 4 + 1, Suit.entries[c % 4]) }
            return columns
        }
    }
}

internal inline fun <T> List<T>.replace(i: Int, f: (T) -> T): List<T> =
    toMutableList().also { it[i] = f(it[i]) }
