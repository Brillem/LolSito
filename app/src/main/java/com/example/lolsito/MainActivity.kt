package com.example.lolsito

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.lolsito.navigation.LolSitoApp
import com.example.lolsito.ui.theme.LolSitoTheme

/**
 * The only activity. It is `singleTop`, so tapping a match reminder while the app runs arrives in
 * [onNewIntent]; from a cold start it arrives in [onCreate]. Either way Match detail of that match opens.
 */
class MainActivity : ComponentActivity() {
    /** The match a reminder asked to open, until the navigation has opened it. */
    private var matchToOpen by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as LolSitoApplication).container
        // A recreated activity (rotation, restored process) already handled its launch.
        if (savedInstanceState == null) {
            matchToOpen = intent.matchIdToOpen()
        }
        // The app is always dark, so the bars always get light icons whatever the system setting.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            LolSitoTheme {
                LolSitoApp(
                    container = container,
                    matchToOpen = matchToOpen,
                    onMatchOpened = { matchToOpen = null },
                )
            }
        }
    }

    /** Every time the app comes to the foreground, a fresh launch included, the reminders are synced. */
    override fun onStart() {
        super.onStart()
        (application as LolSitoApplication).container.onAppForegrounded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.matchIdToOpen()?.let { matchToOpen = it }
    }

    companion object {
        private const val ACTION_OPEN_MATCH = "com.example.lolsito.action.OPEN_MATCH"
        private const val EXTRA_MATCH_ID = "com.example.lolsito.extra.MATCH_ID"

        /** Opens Match detail of [matchId]; used by the match reminders. */
        fun openMatchIntent(context: Context, matchId: String): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN_MATCH)
                .putExtra(EXTRA_MATCH_ID, matchId)

        private fun Intent?.matchIdToOpen(): String? =
            this?.takeIf { it.action == ACTION_OPEN_MATCH }?.getStringExtra(EXTRA_MATCH_ID)?.takeIf { it.isNotBlank() }
    }
}
