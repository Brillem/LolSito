package com.example.lolsito.core.error

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ErrorsTest {
    private fun http(code: Int) =
        HttpException(Response.error<Any>(code, "".toResponseBody("application/json".toMediaType())))

    @Test
    fun `network failures read as no connection`() {
        val expected = "No connection. Check your internet and try again."
        assertEquals(expected, IOException("x").toUserMessage())
        assertEquals(expected, UnknownHostException("host").toUserMessage())
        assertEquals(expected, SocketTimeoutException("slow").toUserMessage())
    }

    @Test
    fun `server errors read as server not responding`() {
        assertEquals("The server isn't responding. Try again in a moment.", http(500).toUserMessage())
        assertEquals("The server isn't responding. Try again in a moment.", http(503).toUserMessage())
    }

    @Test
    fun `rate limiting has its own message`() {
        assertEquals("Too many requests. Try again in a moment.", http(429).toUserMessage())
    }

    @Test
    fun `other failures read as generic`() {
        val expected = "Something went wrong. Try again."
        assertEquals(expected, http(404).toUserMessage())
        assertEquals(expected, SerializationException("bad").toUserMessage())
        assertEquals(expected, IllegalStateException("oops").toUserMessage())
    }

    @Test
    fun `runCatchingCancellable wraps failures and values`() = runBlocking {
        assertEquals(3, runCatchingCancellable { 3 }.getOrNull())
        assertTrue(runCatchingCancellable { error("x") }.isFailure)
    }

    @Test
    fun `runCatchingCancellable rethrows cancellation`() = runBlocking {
        try {
            runCatchingCancellable { throw CancellationException("stop") }
            fail("expected cancellation")
        } catch (e: CancellationException) {
            assertEquals("stop", e.message)
        }
    }

    @Test
    fun `swallowFailure returns null on failure and value on success`() = runBlocking {
        assertEquals(5, swallowFailure { 5 })
        assertNull(swallowFailure<Int> { throw IOException("down") })
    }

    @Test
    fun `swallowFailure rethrows cancellation`() = runBlocking {
        try {
            swallowFailure<Int> { throw CancellationException("stop") }
            fail("expected cancellation")
        } catch (e: CancellationException) {
            assertEquals("stop", e.message)
        }
    }
}
