package org.autojs.autojs.runtime.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataNetworkIpcApiTest {

    @Test
    fun namedHttpClientStoresConfigurationSummary() {
        val http = Http()
        val summary = http.putClient(
            Http.ClientConfig(
                name = "api",
                timeoutMillis = 12_000L,
                maxRetries = 2,
                defaultHeaders = mapOf("X-Agent" to "AutoJs6"),
                interceptors = listOf(Http.RequestInterceptorSpec(type = "bearer", value = "token")),
                certificatePins = mapOf("example.com" to listOf("sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")),
                allowedHosts = setOf("example.com"),
                followRedirects = false,
            )
        )

        assertEquals("api", summary.name)
        assertEquals(12_000L, summary.timeoutMillis)
        assertEquals(2, summary.maxRetries)
        assertEquals(listOf("X-Agent"), summary.defaultHeaders)
        assertEquals(listOf("example.com"), summary.pinnedHosts)
        assertEquals(listOf("example.com"), summary.allowedHosts)
        assertFalse(summary.followRedirects!!)
        assertNotNull(http.resolveClient("api"))
        assertEquals("api", http.clientSummaries().single().name)
        assertTrue(http.removeClient("api"))
        assertTrue(http.clientSummaries().isEmpty())
    }

    @Test
    fun downloadStatusMapKeepsProgressAndErrorFields() {
        val status = Http.DownloadStatus(
            id = "dl-1",
            url = "https://example.com/file.zip",
            path = "/tmp/file.zip",
            tempPath = "/tmp/file.zip.part",
            status = "paused",
            bytesDownloaded = 50,
            totalBytes = 100,
            progress = 0.5,
            clientName = "api",
            error = null,
            createdAt = 1,
            updatedAt = 2,
        ).toMap()

        assertEquals("dl-1", status["id"])
        assertEquals("paused", status["status"])
        assertEquals(0.5, status["progress"])
        assertEquals("api", status["clientName"])
        assertNull(status["error"])
    }

    @Test
    fun ipcMessageRoundTripsThroughNativeObject() {
        val message = Ipc.Message(
            id = "42",
            topic = "jobs.done",
            payload = "ok",
            sender = "runtime@test",
            replyTo = "jobs.reply",
            correlationId = "41",
            timestamp = 123L,
        )

        val parsed = Ipc.Message.from(message.toNativeObject())

        assertEquals(message.id, parsed.id)
        assertEquals(message.topic, parsed.topic)
        assertEquals(message.payload, parsed.payload)
        assertEquals(message.replyTo, parsed.replyTo)
        assertEquals(message.correlationId, parsed.correlationId)
        assertEquals(message.timestamp, parsed.timestamp)
    }
}
