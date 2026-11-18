package com.example.lolsito.core.error

import android.util.Log
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

/** [block]'s result as a [Result]; unlike runCatching, cancellation still propagates. */
suspend fun <T> runCatchingCancellable(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

/** [block]'s value, or null when it fails; cancellation still propagates. */
suspend fun <T> swallowFailure(block: suspend () -> T): T? = runCatchingCancellable(block).getOrNull()

/** A message fit to show the user for this failure; the original is logged. */
fun Throwable.toUserMessage(): String {
    logFailure(this)
    return when {
        this is HttpException && code() == 429 -> "Too many requests. Try again in a moment."
        this is HttpException && code() >= 500 -> "The server isn't responding. Try again in a moment."
        this is IOException -> "No connection. Check your internet and try again."
        else -> "Something went wrong. Try again."
    }
}

private fun logFailure(error: Throwable) {
    // Android's Log is not available in plain unit tests.
    try {
        Log.w("LolSito", "Request failed", error)
    } catch (_: Throwable) {
    }
}
