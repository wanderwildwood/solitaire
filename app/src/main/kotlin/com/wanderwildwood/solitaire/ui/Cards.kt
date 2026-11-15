package com.wanderwildwood.solitaire.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.solitaire.R
import com.wanderwildwood.solitaire.game.Card
import com.wanderwildwood.solitaire.game.Suit

/**
 * How far apart the lines of a card's back are, in px, for a wanted spacing of [wantPx]: whole
 * pixels, and never under 3, so there is always white between two one-pixel lines.
 */
fun backPitchPx(wantPx: Float): Int = Math.round(wantPx).coerceAtLeast(3)

/**
 * A card's back: white with a grid of black lines, every line on whole pixels. No grey anywhere,
 * so the panel has nothing to dither (a flat grey back came out as a restless speckle). The
 * first line across is one step under the top edge, so even the sliver of a covered card shows
 * the grid and reads apart from a white face.
 */
private fun DrawScope.cardBack(radius: CornerRadius) {
    drawRoundRect(Color.White, cornerRadius = radius)
    val pitch = backPitchPx(4.dp.toPx())
    val line = Math.round(1.dp.toPx()).coerceAtLeast(1).toFloat()
    val shape = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, radius)) }
    clipPath(shape) {
        var x = pitch.toFloat()
        while (x < size.width) { drawRect(Color.Black, Offset(x, 0f), Size(line, size.height)); x += pitch }
        var y = pitch.toFloat()
        while (y < size.height) { drawRect(Color.Black, Offset(0f, y), Size(size.width, line)); y += pitch }
    }
}

/** The arrow going round in an empty stock that the waste can be turned back into. */
private fun DrawScope.redealMark(center: Offset, radius: Float) {
    val w = (radius * 0.24f).coerceAtLeast(1.5f)
    drawArc(
        Color.Black, startAngle = -50f, sweepAngle = 290f, useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius), size = Size(radius * 2, radius * 2), style = Stroke(w),
    )
    // The head, at the open end of the arc (50 degrees above the x axis), pointing on round.
    val a = Math.toRadians(-50.0)
    val tip = Offset(center.x + radius * Math.cos(a).toFloat(), center.y + radius * Math.sin(a).toFloat())
    val h = radius * 0.62f
    val head = Path().apply {
        moveTo(tip.x + h * 0.55f, tip.y + h * 0.55f)
        lineTo(tip.x + h * 0.35f, tip.y - h * 0.75f)
        lineTo(tip.x - h * 0.8f, tip.y + h * 0.1f)
        close()
    }
    drawPath(head, Color.Black, style = Fill)
}

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
fun DrawScope.suit(suit: Suit, topLeft: Offset, size: Float, ink: Color = Color.Black, outlineOnly: Boolean = false) {
    var p = Path()
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
            p.moveTo(x(0.5f), y(0.5f))
            p.lineTo(x(0.64f), y(0.96f))
            p.lineTo(x(0.36f), y(0.96f))
            p.close()
            if (outlineOnly) {
                // One outline round the whole club: the three leaves, the middle and the stalk joined.
                for ((c, rr) in listOf(Offset(x(0.5f), y(0.26f)) to r, Offset(x(0.27f), y(0.58f)) to r, Offset(x(0.73f), y(0.58f)) to r, Offset(x(0.5f), y(0.52f)) to r * 0.6f)) {
                    val leaf = Path().apply { addOval(Rect(c.x - rr, c.y - rr, c.x + rr, c.y + rr)) }
                    p = Path.combine(PathOperation.Union, p, leaf)
                }
            } else {
                drawCircle(ink, r, Offset(x(0.5f), y(0.26f)))
                drawCircle(ink, r, Offset(x(0.27f), y(0.58f)))
                drawCircle(ink, r, Offset(x(0.73f), y(0.58f)))
                drawCircle(ink, r * 0.6f, Offset(x(0.5f), y(0.52f)))
            }
        }
    }
    if (outlineOnly) {
        drawPath(p, ink, style = Stroke(width = (size * 0.06f).coerceAtLeast(1.5f)))
    } else if (suit.red) {
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
 * Cards and places press exactly where they are drawn. Compose otherwise stretches the press of
 * anything narrower than 48 dp out to 48 dp, and Spider's ten columns are narrower than that, so a
 * card's press reached over the gap into the next column. On the table every place is drawn as big
 * as the width allows, and a press lands on the card under the finger.
 */
@Composable
fun ExactTouch(content: @Composable () -> Unit) {
    val base = LocalViewConfiguration.current
    val exact = remember(base) {
        object : ViewConfiguration by base {
            override val minimumTouchTargetSize: DpSize get() = DpSize.Zero
        }
    }
    CompositionLocalProvider(LocalViewConfiguration provides exact, content = content)
}

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
            // Dark mode leaves a card alone: white, with its own suits.
            .keepColors()
            // The press and the words on one node, so a screen reader says the card's name
            // where the press is, rather than the corner's "7" on its own.
            .tap(onTap)
            .clearAndSetSemantics { contentDescription = name },
    ) {
        Canvas(Modifier.size(s.cardW, s.cardH)) {
            val r = CornerRadius(4.dp.toPx())
            val line = 1.dp.toPx()
            if (card.up) drawRoundRect(Color.White, cornerRadius = r) else cardBack(r)
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

/**
 * An empty place: a dashed outline, and the suit that belongs there if it is a foundation, drawn
 * in outline whatever its colour so that it is not taken for a card. All of it black: a grey line
 * is dithered by the panel. [redeal] puts the turning arrow in an empty stock that can be dealt again.
 */
@Composable
fun SlotView(x: Dp, y: Dp, s: Sizes, tag: String, name: String, suitMark: Suit?, redeal: Boolean = false, onTap: (() -> Unit)?) {
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
                Color.Black, cornerRadius = CornerRadius(4.dp.toPx()), style = Stroke(line, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                topLeft = Offset(line / 2, line / 2), size = Size(size.width - line, size.height - line),
            )
            if (suitMark != null) {
                val big = size.width * 0.45f
                suit(suitMark, Offset((size.width - big) / 2, (size.height - big) / 2), big, outlineOnly = true)
            }
            if (redeal) redealMark(Offset(size.width / 2, size.height / 2), size.width * 0.2f)
        }
    }
}
