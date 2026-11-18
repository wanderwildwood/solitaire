package com.wanderwildwood.solitaire

import android.content.Context
import com.wanderwildwood.solitaire.game.Kind
import com.wanderwildwood.solitaire.game.Session

/**
 * What the app keeps: a game of each kind in progress, which one was open, the two settings,
 * and how many of each have been played and won. All of it in one private preferences file,
 * written as each move is made, so a game survives the app being closed, killed or the phone
 * restarting.
 */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("solitaire", Context.MODE_PRIVATE)

    /**
     * The saved game of [kind]. A save this version cannot replay exactly (a rule changed since,
     * or the value was cut short) is first copied aside, once, under its own key so nothing can
     * write over it, and the game goes on from the turns that still replay.
     */
    fun game(kind: Kind): Session? {
        val key = "game_${kind.name}"
        val saved = prefs.getString(key, null) ?: return null
        Session.decode(saved)?.let { return it }
        val aside = "${key}_unreadable"
        if (!prefs.contains(aside)) prefs.edit().putString(aside, saved).commit()
        return Session.salvage(saved)
    }

    fun save(s: Session) {
        // commit, not apply: the next thing to happen may be the process being killed.
        prefs.edit().putString("game_${s.kind.name}", s.encode()).commit()
    }

    /** The game on screen when the app was last left, or null for the list. */
    var open: Kind?
        get() = prefs.getString("open", null)?.let { n -> Kind.entries.firstOrNull { it.name == n } }
        set(v) { prefs.edit().putString("open", v?.name).commit() }

    /** Klondike: cards turned from the stock at a time, 1 or 3. */
    var draw: Int
        get() = prefs.getInt("draw", 1)
        set(v) { prefs.edit().putInt("draw", v).apply() }

    /** Spider: suits in the two decks, 1, 2 or 4. */
    var suits: Int
        get() = prefs.getInt("suits", 1)
        set(v) { prefs.edit().putInt("suits", v).apply() }

    fun played(kind: Kind) = prefs.getInt("played_${kind.name}", 0)
    fun won(kind: Kind) = prefs.getInt("won_${kind.name}", 0)

    fun countPlayed(kind: Kind) {
        prefs.edit().putInt("played_${kind.name}", played(kind) + 1).commit()
    }

    fun countWon(kind: Kind) {
        prefs.edit().putInt("won_${kind.name}", won(kind) + 1).commit()
    }

    fun option(kind: Kind) = when (kind) {
        Kind.KLONDIKE -> draw
        Kind.SPIDER -> suits
        Kind.FREECELL -> 0
    }
}
