package com.wanderwildwood.solitaire.game

/** In the order Microsoft's FreeCell numbers them, which its deal generator depends on. */
enum class Suit(val red: Boolean) { CLUBS(false), DIAMONDS(true), HEARTS(true), SPADES(false) }

/** A card: rank 1 (ace) to 13 (king), and whether it is face up. */
data class Card(val rank: Int, val suit: Suit, val up: Boolean = true) {

    val red: Boolean get() = suit.red

    fun faceUp() = if (up) this else copy(up = true)
    fun faceDown() = if (up) copy(up = false) else this

    /** One letter for the rank and one for the suit, as deal listings write them: "TD", "AS". */
    val code: String get() = "A23456789TJQK"[rank - 1].toString() + "CDHS"[suit.ordinal]

    companion object {
        /** The reverse of [code]; face up. */
        fun of(code: String): Card =
            Card("A23456789TJQK".indexOf(code[0]) + 1, Suit.entries["CDHS".indexOf(code[1])])
    }
}

/**
 * SplitMix64, written out so a deal is the same deal on every phone and every version of
 * Android: a seed names a deal for good, which is what "Same deal" relies on.
 */
class Shuffler(seed: Long) {
    private var state = seed

    fun next(): Long {
        state += -0x61c8864680b583ebL
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    /** Fisher-Yates, last place first. */
    fun <T> shuffle(items: List<T>): List<T> {
        val a = items.toMutableList()
        for (i in a.size - 1 downTo 1) {
            val j = ((next() ushr 1) % (i + 1)).toInt()
            val t = a[i]; a[i] = a[j]; a[j] = t
        }
        return a
    }
}

fun fullDeck(): List<Card> = Suit.entries.flatMap { s -> (1..13).map { Card(it, s) } }
