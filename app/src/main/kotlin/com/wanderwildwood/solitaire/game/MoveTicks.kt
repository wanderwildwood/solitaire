package com.wanderwildwood.solitaire.game

/**
 * Counts the turns that were taken, for the optional tick under the finger: [after] is given
 * what a tap, a deal, an undo or a finish answered, and only an answer of true (it moved,
 * flipped, drew or undid) adds one. A tap that went nowhere adds nothing.
 */
class MoveTicks {
    var count = 0
        private set

    fun after(moved: Boolean): Boolean {
        if (moved) count++
        return moved
    }
}
