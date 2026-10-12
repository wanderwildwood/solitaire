package com.wanderwildwood.solitaire

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.mudita.mmd.ThemeMMD
import com.wanderwildwood.solitaire.game.Kind
import com.wanderwildwood.solitaire.game.Session
import com.wanderwildwood.solitaire.ui.SolitaireApp
import com.wanderwildwood.solitaire.ui.monochrome

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(this)
        setContent {
            ThemeMMD(colorScheme = monochrome) {
                // A debug build names each card's place for the emulator tests; a release names none.
                Box(Modifier.semantics { testTagsAsResourceId = BuildConfig.DEBUG }) {
                    SolitaireApp(store, if (savedInstanceState == null) debugStart() else null)
                }
            }
        }
    }

    /**
     * Debug builds only: a deal named in the launching intent, so a test can play a known game
     * to the end. `BuildConfig.DEBUG` is false in a release, so this is compiled out of it.
     */
    private fun debugStart(): Session? {
        if (!BuildConfig.DEBUG) return null
        val kind = intent?.getStringExtra("kind")?.let { k -> Kind.entries.firstOrNull { it.name == k } } ?: return null
        val seed = intent.getLongExtra("seed", 1)
        val option = intent.getIntExtra("option", if (kind == Kind.FREECELL) 0 else 1)
        return Session.start(kind, option, seed)
    }
}
