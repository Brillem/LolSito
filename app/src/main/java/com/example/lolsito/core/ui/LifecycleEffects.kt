package com.example.lolsito.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

/**
 * Runs [block] only while the screen is visible (lifecycle STARTED): it starts when the screen
 * becomes visible, is cancelled when it goes to the background, and starts again on return. [key]
 * restarts it when the thing it works on changes.
 */
@Composable
fun WhileStarted(key: Any?, block: suspend () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(key, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { block() }
    }
}
