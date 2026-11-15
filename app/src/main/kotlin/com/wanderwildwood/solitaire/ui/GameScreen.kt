package com.wanderwildwood.solitaire.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.solitaire.R
import com.wanderwildwood.solitaire.game.Board
import com.wanderwildwood.solitaire.game.Card
import com.wanderwildwood.solitaire.game.Kind
import com.wanderwildwood.solitaire.game.Pile
import com.wanderwildwood.solitaire.game.Session
import com.wanderwildwood.solitaire.game.Suit
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction

/** What the game screen asks of whoever holds the game. */
interface Table {
    val session: Session
    /** Bumped on every change, so the screen redraws. */
    val version: Int
    /** A tap on a card; false when nothing could move. */
    fun tap(pile: Pile, index: Int): Boolean
    fun deal(): Boolean
    fun forget()
    fun undo()
    fun finish()
    fun newGame()
    fun sameDeal()
    fun freeCellDeal(n: Int)
}

@Composable
fun gameName(kind: Kind) = stringResource(
    when (kind) {
        Kind.KLONDIKE -> R.string.klondike
        Kind.FREECELL -> R.string.freecell
        Kind.SPIDER -> R.string.spider
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(table: Table, onBack: () -> Unit) {
    BackHandler { onBack() }
    @Suppress("UNUSED_VARIABLE") val v = table.version
    val s = table.session
    var message by remember { mutableStateOf<String?>(null) }
    // The last press was on a card or pile with nowhere to go. Said where the move count is, so the
    // table does not shift, and it stays until the next press: on this panel a tap that changes
    // nothing otherwise looks like a tap that was missed.
    var noMove by remember { mutableStateOf(false) }
    var askDeal by remember { mutableStateOf(false) }
    val title = if (s.kind == Kind.FREECELL) stringResource(R.string.freecell_deal, s.seed.toString()) else gameName(s.kind)
    val fill = stringResource(R.string.fill_columns_first)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = title, maxLines = 1) },
                navigationIcon = { BarButton(Icons.Back, stringResource(R.string.cd_back), onBack) },
                actions = {
                    TextMMD(
                        text = if (noMove) stringResource(R.string.no_move) else pluralStringResource(R.plurals.moves, s.moves, s.moves.toString()),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (noMove) FontWeight.Bold else null,
                        modifier = Modifier.padding(end = 16.dp).testTag("moves"),
                    )
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .tap { table.forget(); message = null; noMove = false },
            ) {
                ExactTouch {
                    TableView(
                        board = s.board,
                        kind = s.kind,
                        option = s.option,
                        width = maxWidth,
                        height = maxHeight,
                        onTap = { p, i -> message = null; noMove = !table.tap(p, i) && !s.won },
                        onStock = {
                            message = null
                            val dealt = table.deal()
                            val blocked = !dealt && s.rules.stockBlocked(s.board)
                            if (blocked) message = fill
                            noMove = !dealt && !blocked && !s.won
                        },
                    )
                }
                if (s.won) {
                    TextMMD(
                        text = stringResource(R.string.won_in, pluralStringResource(R.plurals.moves, s.moves, s.moves.toString())),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).testTag("won"),
                    )
                }
            }
            message?.let {
                TextMMD(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).testTag("message"),
                )
            }
            Controls(table, table.version, onDeal = { askDeal = true }, clear = { message = null; noMove = false })
        }
    }

    if (askDeal) DealDialog(onDeal = { table.freeCellDeal(it); askDeal = false }, onDismiss = { askDeal = false })
}

/** The row of buttons under the table. */
@Composable
private fun Controls(table: Table, version: Int, onDeal: () -> Unit, clear: () -> Unit) {
    // [version] changes with every move; the session itself stays the same object. It has to be
    // read here, not just passed: Compose leaves an unread parameter out when it decides whether
    // to redraw, and Finish then never appeared mid-game.
    val s = table.session
    // Armed until something else happens (a card played, Back, the screen off): no timer.
    var armed by rememberArmedFlag()
    val canFinish = remember(version) { !s.won && s.finishing() != null }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        @Composable
        fun Btn(label: String, tag: String, bold: Boolean = false, weight: Float = 1f, onClick: () -> Unit) {
            OutlinedButtonMMD(
                onClick = { clear(); onClick() },
                modifier = Modifier.weight(weight).height(48.dp).testTag(tag),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                TextMMD(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (bold) FontWeight.Bold else null,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }

        when {
            s.won -> {
                Btn(stringResource(R.string.new_game), "new") { table.newGame() }
                Btn(stringResource(R.string.same_deal), "same") { table.sameDeal() }
                if (s.kind == Kind.FREECELL) Btn(stringResource(R.string.deal_number), "dealno", onClick = onDeal)
            }
            armed -> Btn(stringResource(R.string.new_game_armed), "new", bold = true) {
                armed = false
                table.newGame()
            }
            else -> {
                Btn(stringResource(R.string.undo), "undo") { table.undo() }
                if (canFinish) Btn(stringResource(R.string.finish), "finish", bold = true) { table.finish() }
                Btn(stringResource(R.string.new_game), "new") {
                    // Starting over throws the game away, so a game under way asks first.
                    if (s.moves > 0) armed = true else table.newGame()
                }
                if (s.kind == Kind.FREECELL) Btn(stringResource(R.string.deal_number), "dealno", onClick = onDeal)
            }
        }
    }
}

/** FreeCell: a deal chosen by its number. */
@Composable
private fun DealDialog(onDeal: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val deal = {
        val n = text.toIntOrNull()
        if (n == null || n !in 1..32000) wrong = true else onDeal(n)
    }
    val field = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    EInkDialog(onDismiss = onDismiss) {
        // The number is what the dialog is for: the field takes the focus as it opens and the
        // keyboard comes up. Asked from inside the dialog, a frame after it is drawn: the field
        // lives in the dialog's own window, which a request from the screen beneath does not reach.
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { field.requestFocus() }
            keyboard?.show()
        }
        TextMMD(text = stringResource(R.string.deal_title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        TextFieldMMD(
            value = text,
            onValueChange = { v -> text = v.filter { it.isDigit() }.take(5); wrong = false },
            singleLine = true,
            placeholder = { TextMMD(text = stringResource(R.string.deal_hint), style = MaterialTheme.typography.labelSmall) },
            // Done on the keyboard is Deal.
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { deal() }),
            modifier = Modifier.fillMaxWidth().focusRequester(field).testTag("dealfield"),
        )
        if (wrong) {
            TextMMD(text = stringResource(R.string.deal_out_of_range), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(12.dp))
        DialogButton(stringResource(R.string.deal_go), filled = true) { deal() }
        DialogButton(stringResource(R.string.cancel), onClick = onDismiss)
    }
}

// ---------------------------------------------------------------------- the table

private const val PAD = 4f

/** Card sizes for [columns] across [width]. */
private fun sizes(columns: Int, width: Dp, gap: Dp, rankSmall: TextStyle, rankLarge: TextStyle): Sizes {
    val cardW = (width - PAD.dp * 2 - gap * (columns - 1)) / columns
    val small = cardW < 44.dp
    return Sizes(
        cardW = cardW,
        cardH = cardW * 1.4f,
        strip = if (small) 22.dp else 26.dp,
        back = 7.dp,
        rank = if (small) rankSmall else rankLarge,
        pip = if (small) 10.dp else 13.dp,
    )
}

/**
 * How much of a covered face-up card shows in a FreeCell column of [cards], in px. Every card there
 * is face up and a card is pressed by the strip of it that shows, so each column spreads down the
 * room it has: a short column shows tall strips, up to [most], and a long one closes up until its
 * last card ends at the foot of [avail]. Never under [least], the strip the other games use; past
 * that, [fan] squeezes the column as in the other games.
 */
fun freeCellStrip(avail: Float, cardH: Float, least: Float, most: Float, cards: Int): Float =
    ((avail - cardH) / (cards - 1).coerceAtLeast(1)).coerceIn(least, maxOf(least, most))

/** The most of a covered FreeCell card that shows: its corner and most of its face, still a stack. */
const val FREECELL_MOST_STRIP = 0.9f

/**
 * How much of a covered face-up card shows in a Spider column, in px. Spider's ten columns make
 * narrow cards, so the strip that shows is what a finger presses; like FreeCell, each column spreads
 * its face-up run down the room left once its face-down cards have their slivers ([back] each), up
 * to [most], and never under [least], the usual strip, past which [fan] squeezes as before.
 */
fun spiderStrip(cards: List<Card>, avail: Float, cardH: Float, back: Float, least: Float, most: Float): Float {
    val downs = cards.dropLast(1).count { !it.up }
    val ups = cards.size - 1 - downs
    return ((avail - cardH - downs * back) / ups.coerceAtLeast(1)).coerceIn(least, maxOf(least, most))
}

/**
 * Where each card of a column sits, top down. A face-down card shows a sliver and a face-up
 * one its strip. When the column is too long for the space, the face-down part closes up
 * first and then the face-up part, so the last card is always in full and nothing runs off
 * the bottom.
 */
fun fan(cards: List<Card>, avail: Float, cardH: Float, back: Float, strip: Float): List<Float> {
    if (cards.isEmpty()) return emptyList()
    val downs = cards.dropLast(1).count { !it.up }
    val ups = cards.size - 1 - downs
    var d = back
    var u = strip
    val room = avail - cardH
    if (downs * d + ups * u > room) {
        d = ((room - ups * u) / downs.coerceAtLeast(1)).coerceIn(minOf(3f, back), back)
        if (downs * d + ups * u > room && ups > 0) u = ((room - downs * d) / ups).coerceAtLeast(0f)
    }
    val out = ArrayList<Float>(cards.size)
    var y = 0f
    for (i in cards.indices) {
        out += y
        y += if (cards[i].up) u else d
    }
    return out
}

@Composable
private fun TableView(
    board: Board,
    kind: Kind,
    option: Int,
    width: Dp,
    height: Dp,
    onTap: (Pile, Int) -> Unit,
    onStock: () -> Unit,
) {
    val columns = board.columns.size
    val gap = if (kind == Kind.SPIDER) 2.dp else 4.dp
    val s = sizes(columns, width, gap, MaterialTheme.typography.bodySmall, MaterialTheme.typography.bodyMedium)
    val topY = PAD.dp
    val tableauY = topY + s.cardH + 10.dp
    fun colX(i: Int) = PAD.dp + (s.cardW + gap) * i
    val stockName = stringResource(R.string.pile_stock)
    val emptyName = stringResource(R.string.pile_empty)

    // Dark mode leaves the cards alone (CardView keeps their colors); the empty places and the
    // words on the table turn with the page, so they show on the dark table too.
    Box(Modifier.fillMaxSize()) {
        // ---------------- the top row
        when (kind) {
            Kind.KLONDIKE -> {
                StockView(board.stock, board.waste.isNotEmpty(), colX(0), topY, s, stockName, emptyName, stringResource(R.string.pile_redeal), onStock)
                val shown = board.waste.takeLast(if (option == 3) 3 else 1)
                shown.forEachIndexed { k, card ->
                    val top = k == shown.lastIndex
                    CardView(
                        card, colX(1) + s.cardW * 0.42f * k, topY, s,
                        tag = if (top) "w_${board.waste.lastIndex}" else "wl_$k",
                        whole = top,
                        pipUnder = !top,
                        onTap = if (top) ({ onTap(Pile.Waste, board.waste.lastIndex) }) else null,
                    )
                }
                Homes(board, 3, ::colX, topY, s, emptyName, onTap)
            }
            Kind.FREECELL -> {
                for (i in 0 until 4) {
                    val c = board.cells[i]
                    if (c == null) SlotView(colX(i), topY, s, "f$i", emptyName, null, onTap = null)
                    else CardView(c, colX(i), topY, s, "f${i}_0", whole = true) { onTap(Pile.Cell(i), 0) }
                }
                Homes(board, 4, ::colX, topY, s, emptyName, onTap)
            }
            Kind.SPIDER -> {
                // Completed runs on the left, one king and how many; the deals left on the right.
                if (board.homes.isNotEmpty()) {
                    val k = board.homes.lastIndex
                    CardView(board.homes[k].first(), colX(0), topY, s, "h${k}_0", whole = true, onTap = null)
                    if (k > 0) {
                        TextMMD(
                            text = stringResource(R.string.runs_count, k + 1),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.offset(colX(1), topY + s.cardH / 2 - 12.dp).testTag("runs"),
                        )
                    }
                }
                val deals = board.stock.size / 10
                for (k in 0 until deals) {
                    CardView(
                        board.stock[k * 10], colX(columns - 1) - 9.dp * (deals - 1 - k), topY, s,
                        tag = "stock_$k", whole = false, onTap = onStock,
                    )
                }
            }
        }

        // ---------------- the columns
        val density = LocalDensity.current
        with(density) {
            val avail = (height - tableauY - PAD.dp).toPx()
            board.columns.forEachIndexed { c, cards ->
                if (cards.isEmpty()) {
                    SlotView(colX(c), tableauY, s, "c$c", emptyName, null, onTap = null)
                } else {
                    // FreeCell and Spider spread each column down the room it has, so short columns
                    // show tall strips; Spider keeps its face-down slivers as they are.
                    val most = s.cardH.toPx() * FREECELL_MOST_STRIP
                    val strip = when (kind) {
                        Kind.FREECELL -> freeCellStrip(avail, s.cardH.toPx(), s.strip.toPx(), most, cards.size)
                        Kind.SPIDER -> spiderStrip(cards, avail, s.cardH.toPx(), s.back.toPx(), s.strip.toPx(), most)
                        else -> s.strip.toPx()
                    }
                    val ys = fan(cards, avail, s.cardH.toPx(), s.back.toPx(), strip)
                    cards.forEachIndexed { i, card ->
                        CardView(
                            card, colX(c), tableauY + ys[i].toDp(), s, tag = "c${c}_$i",
                            whole = i == cards.lastIndex,
                            // A face-down card takes the press too: it cannot move, and says so.
                            onTap = { onTap(Pile.Column(c), i) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StockView(stock: List<Card>, canRedeal: Boolean, x: Dp, y: Dp, s: Sizes, name: String, emptyName: String, redealName: String, onStock: () -> Unit) {
    // An empty stock with cards in the waste is pressed to turn them back over: the arrow says so.
    if (stock.isEmpty()) SlotView(x, y, s, "stock", if (canRedeal) redealName else emptyName, null, redeal = canRedeal, onTap = onStock)
    else CardView(stock.last(), x, y, s, "stock", whole = false, onTap = onStock)
}

@Composable
private fun Homes(board: Board, first: Int, colX: (Int) -> Dp, y: Dp, s: Sizes, emptyName: String, onTap: (Pile, Int) -> Unit) {
    for (i in 0 until 4) {
        val h = board.homes[i]
        if (h.isEmpty()) {
            SlotView(colX(first + i), y, s, "h$i", emptyName, Suit.entries[i], onTap = null)
        } else {
            CardView(h.last(), colX(first + i), y, s, "h${i}_${h.lastIndex}", whole = true) { onTap(Pile.Home(i), h.lastIndex) }
        }
    }
}
