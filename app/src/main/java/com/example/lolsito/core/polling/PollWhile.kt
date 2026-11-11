package com.example.lolsito.core.polling

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Duration

/**
 * Calls [tick] every [interval] while [active] is true, until the caller is cancelled. Run it only
 * while the screen is visible (for example inside `repeatOnLifecycle(STARTED)`): leaving the screen
 * cancels the caller, which is what stops the polling.
 *
 * Each activation waits one full [interval] before its first tick, since whoever turns polling on has
 * just loaded the data. A tick runs to completion before the next wait starts, so ticks never
 * overlap, and a tick that fails is ignored: the next one tries again. The wait and a running tick
 * are cancelled as soon as [active] turns false, so a state change (for example a manual reload) never
 * races with a tick already in flight.
 */
suspend fun pollWhile(
    interval: Duration,
    active: Flow<Boolean> = flowOf(true),
    tick: suspend () -> Unit,
) {
    active.distinctUntilChanged().collectLatest { isActive ->
        if (!isActive) return@collectLatest
        while (true) {
            delay(interval)
            try {
                tick()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keep polling: whatever the tick failed to refresh stays as it was.
            }
        }
    }
}
