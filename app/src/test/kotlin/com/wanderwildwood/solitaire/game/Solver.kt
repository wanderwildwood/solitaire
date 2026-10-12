package com.wanderwildwood.solitaire.game

import java.util.PriorityQueue

/**
 * A plain best-first search, for the tests only: it proves deals can be played out with the
 * moves a tap can make, and gives the emulator a game to play to a win.
 *
 * It only ever tries what [Rules.choices] offers, so every move it finds is one a tap can reach.
 */
object Solver {

    private class Node(val board: Board, val moves: List<Move>, val score: Int)

    fun solve(rules: Rules, start: Board, maxNodes: Int = 200_000): List<Move>? {
        val seen = HashSet<String>()
        val queue = PriorityQueue<Node>(compareBy { it.score })
        queue += Node(start, emptyList(), score(rules, start, 0))
        var n = 0
        while (queue.isNotEmpty() && n++ < maxNodes) {
            val node = queue.poll()!!
            if (rules.won(node.board)) return node.moves
            if (!seen.add(key(node.board))) continue
            for (m in options(rules, node.board)) {
                val next = rules.play(node.board, m) ?: continue
                if (key(next) in seen) continue
                queue += Node(next, node.moves + m, score(rules, next, node.moves.size + 1))
            }
        }
        return null
    }

    fun options(rules: Rules, b: Board): List<Move> {
        val out = mutableListOf<Move>()
        val sources = mutableListOf<Pair<Pile, Int>>()
        b.columns.forEachIndexed { c, col -> col.indices.forEach { sources += Pile.Column(c) to it } }
        b.cells.forEachIndexed { i, card -> if (card != null) sources += Pile.Cell(i) to 0 }
        if (b.waste.isNotEmpty()) sources += Pile.Waste to b.waste.lastIndex
        for ((p, i) in sources) for (to in rules.choices(b, p, i)) out += Move.Shift(p, i, to)
        if (rules.kind != Kind.FREECELL && rules.play(b, Move.Deal) != null) out += Move.Deal
        return out
    }

    private fun key(b: Board): String = buildString {
        b.columns.forEach { c -> c.forEach { append(it.code); append(if (it.up) '+' else '-') }; append('/') }
        append('|'); b.cells.forEach { append(it?.code ?: "--") }
        append('|'); b.homes.forEach { append(it.size) }
        append('|'); b.stock.forEach { append(it.code) }
        append('|'); b.waste.forEach { append(it.code) }
    }

    private fun score(rules: Rules, b: Board, depth: Int): Int {
        val home = if (rules.kind == Kind.SPIDER) b.homes.size * 13 else b.homes.sumOf { it.size }
        val hidden = b.columns.sumOf { c -> c.count { !it.up } }
        val ordered = b.columns.sumOf { c ->
            c.zipWithNext().count { (a, x) ->
                a.up && x.up && a.rank == x.rank + 1 && (if (rules.kind == Kind.SPIDER) a.suit == x.suit else a.red != x.red)
            }
        }
        val empty = b.columns.count { it.isEmpty() }
        val cellsUsed = b.cells.count { it != null }
        return -(home * 10) + hidden * 6 - ordered * 2 - empty * 3 + cellsUsed * 2 + depth / 4
    }
}
