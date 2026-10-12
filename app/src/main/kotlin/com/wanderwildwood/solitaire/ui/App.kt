package com.wanderwildwood.solitaire.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.solitaire.R
import com.wanderwildwood.solitaire.Store
import com.wanderwildwood.solitaire.game.Kind
import com.wanderwildwood.solitaire.game.Pile
import com.wanderwildwood.solitaire.game.Session
import kotlin.random.Random

/**
 * Holds the game on the table and writes it down after every change, and counts a game as
 * played at its first move and as won when it is won.
 */
class Holder(private val store: Store, start: Session) : Table {
    override var session: Session = start
        private set
    private var _version by mutableIntStateOf(0)
    override val version: Int get() = _version

    private fun changed(wasWon: Boolean) {
        val s = session
        if (!s.counted && s.moves > 0) {
            s.counted = true
            store.countPlayed(s.kind)
        }
        if (!wasWon && s.won) store.countWon(s.kind)
        store.save(s)
        _version++
    }

    private fun act(f: (Session) -> Boolean) {
        val wasWon = session.won
        if (f(session)) changed(wasWon)
    }

    override fun tap(pile: Pile, index: Int) = act { it.tap(pile, index) }
    override fun deal(): Boolean {
        val wasWon = session.won
        val moved = session.deal()
        if (moved) changed(wasWon)
        return moved
    }
    override fun forget() = session.forget()
    override fun undo() = act { it.undo() }
    override fun finish() = act { it.finish() }

    private fun replace(s: Session) {
        session = s
        store.save(s)
        _version++
    }

    override fun newGame() = replace(fresh(store, session.kind))
    override fun sameDeal() = replace(Session.start(session.kind, session.option, session.seed))
    override fun freeCellDeal(n: Int) = replace(Session.start(Kind.FREECELL, 0, n.toLong()))

    companion object {
        /** A new deal of [kind] with today's settings. */
        fun fresh(store: Store, kind: Kind): Session {
            val seed = if (kind == Kind.FREECELL) Random.nextLong(1, 32001) else Random.nextLong()
            return Session.start(kind, store.option(kind), seed)
        }

        /** The game of [kind] under way, or a new one if there is none or it is over. */
        fun resume(store: Store, kind: Kind): Session =
            store.game(kind)?.takeIf { !it.won } ?: fresh(store, kind).also(store::save)
    }
}

private sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data class Game(val holder: Holder) : Screen
}

/**
 * [debugStart] is a deal set from outside in a debug build, so a test can play a known one.
 */
@Composable
fun SolitaireApp(store: Store, debugStart: Session?) {
    var screen by remember {
        mutableStateOf<Screen>(
            when {
                debugStart != null -> Screen.Game(Holder(store, debugStart.also(store::save)))
                else -> store.open?.let { Screen.Game(Holder(store, Holder.resume(store, it))) } ?: Screen.Home
            },
        )
    }
    var about by remember { mutableStateOf(false) }

    fun go(to: Screen) {
        store.open = (to as? Screen.Game)?.holder?.session?.kind
        screen = to
    }
    if (debugStart != null) remember { store.open = debugStart.kind }

    when (val s = screen) {
        Screen.Home -> HomeScreen(
            store = store,
            onPick = { go(Screen.Game(Holder(store, Holder.resume(store, it)))) },
            onSettings = { go(Screen.Settings) },
        )
        Screen.Settings -> SettingsScreen(store, onBack = { go(Screen.Home) }, onAbout = { about = true })
        is Screen.Game -> GameScreen(s.holder, onBack = { go(Screen.Home) })
    }
    if (about) AboutDialog(onDismiss = { about = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(store: Store, onPick: (Kind) -> Unit, onSettings: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
                actions = { BarButton(Icons.Settings, stringResource(R.string.cd_settings), onSettings) },
            )
        },
    ) { padding ->
        LazyColumnMMD(modifier = Modifier.padding(padding).fillMaxSize(), isScrollbarVisible = false) {
            for (kind in Kind.entries) {
                item(key = kind.name) {
                    val saved = remember { store.game(kind) }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(kind) }
                            .testTag("home_${kind.name}")
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                    ) {
                        TextMMD(text = gameName(kind), style = MaterialTheme.typography.titleMedium)
                        if (saved != null && !saved.won && saved.moves > 0) {
                            TextMMD(
                                text = stringResource(R.string.in_progress, pluralStringResource(R.plurals.moves, saved.moves, saved.moves.toString())),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    HorizontalDividerMMD(thickness = 1.dp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(store: Store, onBack: () -> Unit, onAbout: () -> Unit) {
    BackHandler { onBack() }
    var v by remember { mutableIntStateOf(0) }
    var pickDraw by remember { mutableStateOf(false) }
    var pickSuits by remember { mutableStateOf(false) }
    val draw = remember(v) { store.draw }
    val suits = remember(v) { store.suits }
    val drawNames = mapOf(1 to stringResource(R.string.settings_draw_1), 3 to stringResource(R.string.settings_draw_3))
    val suitNames = mapOf(
        1 to stringResource(R.string.settings_suits_1),
        2 to stringResource(R.string.settings_suits_2),
        4 to stringResource(R.string.settings_suits_4),
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = { BarButton(Icons.Back, stringResource(R.string.cd_back), onBack) },
                actions = { BarButton(Icons.Info, stringResource(R.string.cd_about), onAbout) },
            )
        },
    ) { padding ->
        LazyColumnMMD(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 20.dp), isScrollbarVisible = false) {
            item { Spacer(Modifier.height(8.dp)) }
            item(key = "draw") { SettingRow(stringResource(R.string.settings_draw), drawNames[draw]) { pickDraw = true } }
            item(key = "suits") { SettingRow(stringResource(R.string.settings_suits), suitNames[suits]) { pickSuits = true } }
            item(key = "next") {
                TextMMD(text = stringResource(R.string.settings_next_game), style = MaterialTheme.typography.labelSmall)
            }
            item(key = "counts") { Heading(stringResource(R.string.settings_counts), Modifier.padding(top = 12.dp)) }
            for (kind in Kind.entries) {
                item(key = "count_${kind.name}") {
                    TextMMD(
                        text = stringResource(R.string.settings_count_row, gameName(kind), store.played(kind).toString(), store.won(kind).toString()),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 6.dp).testTag("count_${kind.name}"),
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (pickDraw) {
        OptionsDialog(stringResource(R.string.settings_draw), drawNames.toList(), draw, onPick = { store.draw = it; v++ }, onDismiss = { pickDraw = false })
    }
    if (pickSuits) {
        OptionsDialog(stringResource(R.string.settings_suits), suitNames.toList(), suits, onPick = { store.suits = it; v++ }, onDismiss = { pickSuits = false })
    }
}
