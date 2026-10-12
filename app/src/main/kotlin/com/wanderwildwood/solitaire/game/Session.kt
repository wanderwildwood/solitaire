package com.wanderwildwood.solitaire.game

/**
 * One game: the deal it came from and every turn taken since. A turn is usually one move;
 * finishing is one turn of many moves, so a single Undo takes it back whole.
 *
 * The board is never stored, only rebuilt from the deal and the turns, which is what makes
 * Undo exact and keeps what is saved small. [boards] holds one board per turn, the deal first.
 */
class Session private constructor(
    val kind: Kind,
    val option: Int,
    val seed: Long,
    turns: List<List<Move>>,
    /** Counted as played in the statistics: true once the first move is made. */
    var counted: Boolean,
) {
    val rules = Rules(kind, option)
    private val _turns = turns.toMutableList()
    private val _boards = mutableListOf(rules.deal(seed))

    init {
        for (t in _turns) _boards += replay(_boards.last(), t)
            ?: throw IllegalStateException("a saved turn does not replay")
    }

    val turns: List<List<Move>> get() = _turns
    val boards: List<Board> get() = _boards
    val board: Board get() = _boards.last()
    val moves: Int get() = _turns.size
    val won: Boolean get() = rules.won(board)

    private fun replay(b: Board, turn: List<Move>): Board? {
        var board = b
        for (m in turn) board = rules.play(board, m) ?: return null
        return board
    }

    /** Plays a turn; false, and nothing changes, if any part of it is not legal. */
    fun play(turn: List<Move>): Boolean {
        if (won || turn.isEmpty()) return false
        val next = replay(board, turn) ?: return false
        _turns += turn
        _boards += next
        cycle = null
        return true
    }

    fun undo(): Boolean {
        if (_turns.isEmpty()) return false
        _turns.removeAt(_turns.lastIndex)
        _boards.removeAt(_boards.lastIndex)
        cycle = null
        return true
    }

    // ------------------------------------------------------------------ tapping

    /**
     * The tap just made, so a second tap on the same card can send it on to the next place
     * instead: [choices] were worked out from the board before it moved, [at] is the one it
     * went to, and [landed] where the card now lies. [at] of -1 means it is back where it
     * started.
     */
    private data class Cycle(
        val from: Pile, val index: Int, val choices: List<Pile>, val at: Int,
        val landed: Pair<Pile, Int>, val turnCount: Int,
    )
    private var cycle: Cycle? = null

    /**
     * A tap on the card at [index] of [pile]. It goes to the best place it can; tapped again
     * where it landed, it goes to the next, and after the last it goes back where it was.
     * Returns whether anything moved.
     */
    fun tap(pile: Pile, index: Int): Boolean {
        if (won) return false
        val c = cycle
        if (c != null && c.landed == (pile to index) && c.turnCount == _turns.size) {
            if (c.choices.size < 2) {
                // Nowhere else to send it: the tap is a fresh one from where it lies now.
                cycle = null
                return tap(pile, index)
            }
            if (c.at >= 0) undo()
            val nextAt = if (c.at + 1 < c.choices.size) c.at + 1 else -1
            if (nextAt >= 0) play(listOf(Move.Shift(c.from, c.index, c.choices[nextAt])))
            cycle = c.copy(
                at = nextAt,
                landed = if (nextAt >= 0) landingOf(c.from, c.index, c.choices[nextAt]) else (c.from to c.index),
                turnCount = _turns.size,
            )
            return true
        }
        val choices = rules.choices(board, pile, index)
        if (choices.isEmpty()) {
            cycle = null
            return false
        }
        play(listOf(Move.Shift(pile, index, choices[0])))
        cycle = Cycle(pile, index, choices, 0, landingOf(pile, index, choices[0]), _turns.size)
        return true
    }

    /** A tap on the bare table: the next tap on any card is a fresh one. */
    fun forget() {
        cycle = null
    }

    /** Where the first card of a run lies after it moved to [to]: on top of what was there. */
    private fun landingOf(from: Pile, index: Int, to: Pile): Pair<Pile, Int> {
        val before = _boards[_boards.size - 2]
        return to to if (to is Pile.Cell) 0 else before.pile(to).size
    }

    /** A tap on the stock. */
    fun deal(): Boolean = play(listOf(Move.Deal))

    /** The finishing moves, if the game can be finished in one go from here. */
    fun finishing(): List<Move>? = rules.finish(board)

    fun finish(): Boolean = finishing()?.let { play(it) } ?: false

    // ------------------------------------------------------------------ saving

    fun encode(): String = listOf(
        "1", kind.name, option.toString(), seed.toString(), if (counted) "1" else "0",
        _turns.joinToString(";") { t -> t.joinToString(",") { Move.encode(it) } },
    ).joinToString("|")

    companion object {
        fun start(kind: Kind, option: Int, seed: Long) = Session(kind, option, seed, emptyList(), false)

        /** A saved game, or null if it cannot be read back exactly. */
        fun decode(s: String): Session? = runCatching {
            val f = s.split("|")
            require(f[0] == "1" && f.size == 6)
            val turns = if (f[5].isEmpty()) emptyList() else f[5].split(";").map { t -> t.split(",").map(Move::decode) }
            Session(Kind.valueOf(f[1]), f[2].toInt(), f[3].toLong(), turns, f[4] == "1")
        }.getOrNull()
    }
}
