package com.wanderwildwood.solitaire.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.solitaire.R
import com.wanderwildwood.solitaire.game.Card
import com.wanderwildwood.solitaire.game.Suit

/** The grey of a card's back: one flat tone, which the panel draws as an even dither. */
val CardBack = Color(0xFF9A9A9A)

/** The faint line of an empty place on the table. */
val SlotLine = Color(0xFFAAAAAA)

/** The sizes one game's table is drawn at, worked out from the width of the screen. */
data class Sizes(
    val cardW: Dp,
    val cardH: Dp,
    /** How much of a face-up card shows under the next: its rank and suit. */
    val strip: Dp,
    /** How much of a face-down card shows. */
    val back: Dp,
    val rank: TextStyle,
    val pip: Dp,
)

/**
 * A suit drawn as a vector rather than a glyph from the font, so it is the same shape at
 * every size and on every phone. Hearts and diamonds are drawn in outline and spades and
 * clubs filled, so the two colours read apart on a screen with no red.
 */
fun DrawScope.suit(suit: Suit, topLeft: Offset, size: Float, ink: Color = Color.Black) {
    val p = Path()
    fun x(f: Float) = topLeft.x + f * size
    fun y(f: Float) = topLeft.y + f * size
    when (suit) {
        Suit.HEARTS -> {
            p.moveTo(x(0.5f), y(0.92f))
            p.cubicTo(x(0.2f), y(0.68f), x(0.04f), y(0.5f), x(0.06f), y(0.3f))
            p.cubicTo(x(0.08f), y(0.12f), x(0.36f), y(0.04f), x(0.5f), y(0.26f))
            p.cubicTo(x(0.64f), y(0.04f), x(0.92f), y(0.12f), x(0.94f), y(0.3f))
            p.cubicTo(x(0.96f), y(0.5f), x(0.8f), y(0.68f), x(0.5f), y(0.92f))
            p.close()
        }
        Suit.DIAMONDS -> {
            p.moveTo(x(0.5f), y(0.04f))
            p.lineTo(x(0.88f), y(0.5f))
            p.lineTo(x(0.5f), y(0.96f))
            p.lineTo(x(0.12f), y(0.5f))
            p.close()
        }
        Suit.SPADES -> {
            p.moveTo(x(0.5f), y(0.04f))
            p.cubicTo(x(0.78f), y(0.3f), x(0.96f), y(0.44f), x(0.94f), y(0.62f))
            p.cubicTo(x(0.92f), y(0.8f), x(0.64f), y(0.84f), x(0.53f), y(0.68f))
            p.lineTo(x(0.64f), y(0.96f))
            p.lineTo(x(0.36f), y(0.96f))
            p.lineTo(x(0.47f), y(0.68f))
            p.cubicTo(x(0.36f), y(0.84f), x(0.08f), y(0.8f), x(0.06f), y(0.62f))
            p.cubicTo(x(0.04f), y(0.44f), x(0.22f), y(0.3f), x(0.5f), y(0.04f))
            p.close()
        }
        Suit.CLUBS -> {
            val r = 0.2f * size
            drawCircle(ink, r, Offset(x(0.5f), y(0.26f)))
            drawCircle(ink, r, Offset(x(0.27f), y(0.58f)))
            drawCircle(ink, r, Offset(x(0.73f), y(0.58f)))
            drawCircle(ink, r * 0.6f, Offset(x(0.5f), y(0.52f)))
            p.moveTo(x(0.5f), y(0.5f))
            p.lineTo(x(0.64f), y(0.96f))
            p.lineTo(x(0.36f), y(0.96f))
            p.close()
        }
    }
    if (suit.red) {
        drawPath(p, ink, style = Stroke(width = (size * 0.11f).coerceAtLeast(1.5f)))
    } else {
        drawPath(p, ink, style = Fill)
    }
}

@Composable
fun cardName(card: Card): String {
    val rank = stringResource(
        when (card.rank) {
            1 -> R.string.rank_1; 2 -> R.string.rank_2; 3 -> R.string.rank_3; 4 -> R.string.rank_4
            5 -> R.string.rank_5; 6 -> R.string.rank_6; 7 -> R.string.rank_7; 8 -> R.string.rank_8
            9 -> R.string.rank_9; 10 -> R.string.rank_10; 11 -> R.string.rank_11; 12 -> R.string.rank_12
            else -> R.string.rank_13
        },
    )
    val suit = stringResource(
        when (card.suit) {
            Suit.CLUBS -> R.string.suit_clubs
            Suit.DIAMONDS -> R.string.suit_diamonds
            Suit.HEARTS -> R.string.suit_hearts
            Suit.SPADES -> R.string.suit_spades
        },
    )
    return stringResource(R.string.card, rank, suit)
}

@Composable
private fun corner(rank: Int): String = when (rank) {
    1 -> stringResource(R.string.corner_1)
    11 -> stringResource(R.string.corner_11)
    12 -> stringResource(R.string.corner_12)
    13 -> stringResource(R.string.corner_13)
    else -> rank.toString()
}

/** Taps with nothing to show for them: no ripple, which on this panel is only a smear. */
@Composable
fun Modifier.tap(onTap: (() -> Unit)?): Modifier =
    if (onTap == null) this else clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onTap,
    )

/**
 * One card at [x], [y]. A face-up card shows its rank and suit across the top, which is all
 * that is seen of it in a fanned column, and [whole] adds a larger suit in the middle for the
 * card that lies uncovered.
 */
@Composable
fun CardView(card: Card, x: Dp, y: Dp, s: Sizes, tag: String, whole: Boolean, pipUnder: Boolean = false, onTap: (() -> Unit)?) {
    val name = if (card.up) cardName(card) else stringResource(R.string.card_face_down)
    Box(
        Modifier
            .offset(x, y)
            .size(s.cardW, s.cardH)
            .testTag(tag)
            // The press and the words on one node, so a screen reader says the card's name
            // where the press is, rather than the corner's "7" on its own.
            .tap(onTap)
            .clearAndSetSemantics { contentDescription = name },
    ) {
        Canvas(Modifier.size(s.cardW, s.cardH)) {
            val r = CornerRadius(4.dp.toPx())
            val line = 1.dp.toPx()
            drawRoundRect(if (card.up) Color.White else CardBack, cornerRadius = r)
            drawRoundRect(Color.Black, cornerRadius = r, style = Stroke(line), topLeft = Offset(line / 2, line / 2),
                size = Size(size.width - line, size.height - line))
            if (!card.up) return@Canvas
            val pip = s.pip.toPx()
            // A card fanned sideways, as the waste is in draw three, shows its left edge only,
            // so its suit goes under the rank there.
            val at = if (pipUnder) Offset(3.dp.toPx(), s.strip.toPx()) else Offset(size.width - pip - 2.dp.toPx(), 4.dp.toPx())
            suit(card.suit, at, pip)
            if (whole) {
                val big = size.width * 0.5f
                suit(card.suit, Offset((size.width - big) / 2, s.strip.toPx() + (size.height - s.strip.toPx() - big) / 2), big)
            }
        }
        if (card.up) {
            TextMMD(
                text = corner(card.rank),
                style = s.rank,
                fontWeight = FontWeight.Bold,
                // "10" is the one two-figure rank; drawn a little tighter it clears the suit.
                letterSpacing = if (card.rank == 10) (-1.5).sp else TextUnit.Unspecified,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 3.dp),
            )
        }
    }
}

/** An empty place: a faint outline, and the suit that belongs there if it is a foundation. */
@Composable
fun SlotView(x: Dp, y: Dp, s: Sizes, tag: String, name: String, suitMark: Suit?, onTap: (() -> Unit)?) {
    Box(
        Modifier
            .offset(x, y)
            .size(s.cardW, s.cardH)
            .testTag(tag)
            // The press and the words on one node, so a screen reader says the card's name
            // where the press is, rather than the corner's "7" on its own.
            .tap(onTap)
            .clearAndSetSemantics { contentDescription = name },
    ) {
        Canvas(Modifier.size(s.cardW, s.cardH)) {
            val line = 1.dp.toPx()
            drawRoundRect(
                SlotLine, cornerRadius = CornerRadius(4.dp.toPx()), style = Stroke(line, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                topLeft = Offset(line / 2, line / 2), size = Size(size.width - line, size.height - line),
            )
            if (suitMark != null) {
                val big = size.width * 0.45f
                suit(suitMark, Offset((size.width - big) / 2, (size.height - big) / 2), big, SlotLine)
            }
        }
    }
}
