package org.autojs.autojs.runtime.api

import org.autojs.autojs.core.http.MutableOkHttp
import okhttp3.Call
import okhttp3.CertificatePinner
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/**
 * Created by SuperMonster003 on Jul 7, 2024.
 */
class Http {

    val okhttp = MutableOkHttp()
    private val namedClients = ConcurrentHashMap<String, NamedClient>()
    private val downloadManager = HttpDownloadManager(this)

    fun client() = okhttp.client()

    fun client(name: String): OkHttpClient = namedClients[name]?.client
        ?: throw IllegalArgumentException("HTTP client \"$name\" does not exist")

    fun resolveClient(name: String?): OkHttpClient = when {
        name.isNullOrBlank() -> client()
        else -> client(name)
    }

    fun putClient(config: ClientConfig): ClientSummary {
        require(config.name.isNotBlank()) { "HTTP client name must not be blank" }
        val client = buildClient(config)
        val summary = config.toSummary()
        namedClients[config.name] = NamedClient(client, summary)
        return summary
    }

    fun clientSummary(name: String): ClientSummary = namedClients[name]?.summary
        ?: throw IllegalArgumentException("HTTP client \"$name\" does not exist")

    fun clientSummaries(): List<ClientSummary> = namedClients.values.map { it.summary }.sortedBy { it.name }

    fun removeClient(name: String) = namedClients.remove(name) != null

    fun enqueueDownload(url: String, path: String, options: DownloadOptions = DownloadOptions()): DownloadStatus {
        return downloadManager.enqueue(url, path, options)
    }

    fun resumeDownload(id: String) = downloadManager.resume(id)

    fun pauseDownload(id: String) = downloadManager.pause(id)

    fun cancelDownload(id: String) = downloadManager.cancel(id)

    fun downloadStatus(id: String) = downloadManager.status(id)

    fun downloadStatuses() = downloadManager.statuses()

    private fun buildClient(config: ClientConfig): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .readTimeout(config.timeoutMillis, TimeUnit.MILLISECONDS)
            .writeTimeout(config.timeoutMillis, TimeUnit.MILLISECONDS)
            .connectTimeout(config.timeoutMillis, TimeUnit.MILLISECONDS)
            .addInterceptor(retryInterceptor(config.maxRetries))

        if (config.defaultHeaders.isNotEmpty() || config.interceptors.isNotEmpty() || config.allowedHosts.isNotEmpty()) {
            builder.addInterceptor(requestPolicyInterceptor(config))
        }

        if (config.certificatePins.isNotEmpty()) {
            val certificatePinner = CertificatePinner.Builder().apply {
                config.certificatePins.forEach { (host, pins) ->
                    add(host, *pins.toTypedArray())
                }
            }.build()
            builder.certificatePinner(certificatePinner)
        }

        config.followRedirects?.let {
            builder.followRedirects(it)
            builder.followSslRedirects(it)
        }

        return builder.build()
    }

    private fun retryInterceptor(maxRetries: Int) = Interceptor { chain ->
        val request = chain.request()
        var response = chain.proceed(request)
        var tryCount = 0
        while (!response.isSuccessful && tryCount < maxRetries) {
            tryCount++
            response.close()
            response = chain.proceed(request)
        }
        response
    }

    private fun requestPolicyInterceptor(config: ClientConfig) = Interceptor { chain ->
        val original = chain.request()
        val host = original.url.host
        if (config.allowedHosts.isNotEmpty() && config.allowedHosts.none { it.matchesHost(host) }) {
            throw IOException("HTTP client \"${config.name}\" blocks host \"$host\"")
        }

        var requestBuilder = original.newBuilder()
        var urlBuilder = original.url.newBuilder()

        config.defaultHeaders.forEach { (name, value) ->
            requestBuilder.header(name, value)
        }

        config.interceptors.forEach { interceptor ->
            when (interceptor.type) {
                "header", "setHeader" -> requestBuilder.header(interceptor.requireName(), interceptor.requireValue())
                "addHeader" -> requestBuilder.addHeader(interceptor.requireName(), interceptor.requireValue())
                "query" -> urlBuilder.addQueryParameter(interceptor.requireName(), interceptor.requireValue())
                "userAgent" -> requestBuilder.header("User-Agent", interceptor.requireValue())
                "bearer" -> requestBuilder.header("Authorization", "Bearer ${interceptor.requireValue()}")
                else -> throw IOException("Unsupported HTTP interceptor type \"${interceptor.type}\"")
            }
        }

        chain.proceed(requestBuilder.url(urlBuilder.build()).build())
    }

    private fun String.matchesHost(host: String) = when {
        this == host -> true
        startsWith("*.") -> host == drop(2) || host.endsWith(this.drop(1))
        else -> false
    }

    private data class NamedClient(
        val client: OkHttpClient,
        val summary: ClientSummary,
    )

    data class RequestInterceptorSpec(
        val type: String,
        val name: String? = null,
        val value: String? = null,
    ) {
        fun requireName() = requireNotNull(name) { "HTTP interceptor \"$type\" requires name" }
        fun requireValue() = requireNotNull(value) { "HTTP interceptor \"$type\" requires value" }
        fun toSummary() = mapOf(
            "type" to type,
            "name" to name.orEmpty(),
            "hasValue" to (value != null),
        )
    }

    data class ClientConfig(
        val name: String,
        val timeoutMillis: Long = MutableOkHttp.DEFAULT_TIMEOUT,
        val maxRetries: Int = MutableOkHttp.DEFAULT_MAX_RETRIES,
        val defaultHeaders: Map<String, String> = emptyMap(),
        val interceptors: List<RequestInterceptorSpec> = emptyList(),
        val certificatePins: Map<String, List<String>> = emptyMap(),
        val allowedHosts: Set<String> = emptySet(),
        val followRedirects: Boolean? = null,
    ) {
        fun toSummary() = ClientSummary(
            name = name,
            timeoutMillis = timeoutMillis,
            maxRetries = maxRetries,
            defaultHeaders = defaultHeaders.keys.sorted(),
            interceptors = interceptors.map { it.toSummary() },
            pinnedHosts = certificatePins.keys.sorted(),
            allowedHosts = allowedHosts.sorted(),
            followRedirects = followRedirects,
        )
    }

    data class ClientSummary(
        val name: String,
        val timeoutMillis: Long,
        val maxRetries: Int,
        val defaultHeaders: List<String>,
        val interceptors: List<Map<String, Any>>,
        val pinnedHosts: List<String>,
        val allowedHosts: List<String>,
        val followRedirects: Boolean?,
    ) {
        fun toMap() = mapOf(
            "name" to name,
            "timeout" to timeoutMillis,
            "maxRetries" to maxRetries,
            "defaultHeaders" to defaultHeaders,
            "interceptors" to interceptors,
            "pinnedHosts" to pinnedHosts,
            "allowedHosts" to allowedHosts,
            "followRedirects" to followRedirects,
        )
    }

    data class DownloadOptions(
        val id: String = UUID.randomUUID().toString(),
        val clientName: String? = null,
        val headers: Map<String, String> = emptyMap(),
        val resume: Boolean = true,
        val overwrite: Boolean = true,
    )

    data class DownloadStatus(
        val id: String,
        val url: String,
        val path: String,
        val tempPath: String,
        val status: String,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val progress: Double,
        val clientName: String?,
        val error: String?,
        val createdAt: Long,
        val updatedAt: Long,
    ) {
        fun toMap() = mapOf(
            "id" to id,
            "url" to url,
            "path" to path,
            "tempPath" to tempPath,
            "status" to status,
            "bytesDownloaded" to bytesDownloaded,
            "totalBytes" to totalBytes,
            "progress" to progress,
            "clientName" to clientName,
            "error" to error,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt,
        )
    }

    private class HttpDownloadManager(private val http: Http) {

        private val tasks = ConcurrentHashMap<String, DownloadTask>()

        fun enqueue(url: String, path: String, options: DownloadOptions): DownloadStatus {
            require(url.isNotBlank()) { "Download url must not be blank" }
            require(path.isNotBlank()) { "Download path must not be blank" }
            val task = DownloadTask(http, url, path, options)
            val existing = tasks.putIfAbsent(options.id, task)
            require(existing == null) { "Download task \"${options.id}\" already exists" }
            task.start()
            return task.snapshot()
        }

        fun resume(id: String): DownloadStatus {
            val task = requireTask(id)
            task.resume()
            return task.snapshot()
        }

        fun pause(id: String): DownloadStatus {
            val task = requireTask(id)
            task.pause()
            return task.snapshot()
        }

        fun cancel(id: String): DownloadStatus {
            val task = requireTask(id)
            task.cancel()
            return task.snapshot()
        }

        fun status(id: String) = tasks[id]?.snapshot()

        fun statuses() = tasks.values.map { it.snapshot() }.sortedByDescending { it.updatedAt }

        private fun requireTask(id: String) = tasks[id] ?: throw IllegalArgumentException("Download task \"$id\" does not exist")
    }

    private class DownloadTask(
        private val http: Http,
        private val url: String,
        private val path: String,
        private val options: DownloadOptions,
    ) {
        private val createdAt = System.currentTimeMillis()
        private val tempPath = "$path.part"
        private val pauseRequested = AtomicBoolean(false)
        private val cancelRequested = AtomicBoolean(false)

        @Volatile
        private var status = "queued"

        @Volatile
        private var bytesDownloaded = 0L

        @Volatile
        private var totalBytes = -1L

        @Volatile
        private var error: String? = null

        @Volatile
        private var updatedAt = createdAt

        @Volatile
        private var call: Call? = null

        @Volatile
        private var worker: Thread? = null

        fun start() {
            require(status !in setOf("running", "completed")) { "Download task \"${options.id}\" is $status" }
            pauseRequested.set(false)
            cancelRequested.set(false)
            status = "running"
            touch()
            worker = Thread({ runDownload() }, "AutoJs6-http-download-${options.id.take(8)}").apply {
                isDaemon = true
                start()
            }
        }

        fun resume() {
            if (status == "completed" || status == "running") return
            start()
        }

        fun pause() {
            if (status !in setOf("running", "queued")) return
            pauseRequested.set(true)
            status = "pausing"
            touch()
            call?.cancel()
        }

        fun cancel() {
            cancelRequested.set(true)
            status = "canceled"
            touch()
            call?.cancel()
        }

        fun snapshot(): DownloadStatus {
            val total = totalBytes
            return DownloadStatus(
                id = options.id,
                url = url,
                path = path,
                tempPath = tempPath,
                status = status,
                bytesDownloaded = bytesDownloaded,
                totalBytes = total,
                progress = if (total > 0) bytesDownloaded.toDouble() / total else -1.0,
                clientName = options.clientName,
                error = error,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }

        private fun runDownload() {
            try {
                val targetFile = File(path)
                val tempFile = File(tempPath)
                targetFile.parentFile?.mkdirs()
                tempFile.parentFile?.mkdirs()

                if (targetFile.exists() && !options.overwrite && !options.resume) {
                    throw IOException("Target file already exists: $path")
                }

                var startAt = when {
                    options.resume && tempFile.exists() -> tempFile.length()
                    else -> 0L
                }
                if (!options.resume && tempFile.exists()) {
                    tempFile.delete()
                }

                val requestBuilder = Request.Builder().url(url)
                options.headers.forEach { (name, value) -> requestBuilder.header(name, value) }
                if (startAt > 0) {
                    requestBuilder.header("Range", "bytes=$startAt-")
                }

                val client = http.resolveClient(options.clientName)
                val request = requestBuilder.build()
                val localCall = client.newCall(request)
                call = localCall

                localCall.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code} ${response.message}")
                    }

                    if (startAt > 0 && response.code != 206) {
                        startAt = 0L
                        FileOutputStream(tempFile, false).use { }
                    }

                    val body = response.body ?: throw IOException("Empty response body")
                    bytesDownloaded = startAt
                    totalBytes = body.contentLength().let { length ->
                        if (length >= 0) length + startAt else -1L
                    }
                    touch()

                    body.byteStream().use { input ->
                        FileOutputStream(tempFile, startAt > 0).use { output ->
                            val buffer = ByteArray(DEFAULT_DOWNLOAD_BUFFER_SIZE)
                            while (true) {
                                if (cancelRequested.get()) {
                                    status = "canceled"
                                    touch()
                                    return
                                }
                                if (pauseRequested.get()) {
                                    status = "paused"
                                    touch()
                                    return
                                }
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                bytesDownloaded += read
                                touch()
                            }
                        }
                    }
                }

                if (cancelRequested.get()) {
                    status = "canceled"
                    touch()
                    return
                }
                if (pauseRequested.get()) {
                    status = "paused"
                    touch()
                    return
                }
                if (targetFile.exists() && options.overwrite) {
                    targetFile.delete()
                }
                if (!File(tempPath).renameTo(targetFile)) {
                    throw IOException("Failed to move temporary file to $path")
                }
                bytesDownloaded = max(bytesDownloaded, targetFile.length())
                totalBytes = bytesDownloaded
                status = "completed"
                error = null
                touch()
            } catch (e: Throwable) {
                if (pauseRequested.get()) {
                    status = "paused"
                    error = null
                } else if (cancelRequested.get()) {
                    status = "canceled"
                    error = null
                } else {
                    status = "error"
                    error = e.message ?: e.javaClass.name
                }
                touch()
            } finally {
                call = null
            }
        }

        private fun touch() {
            updatedAt = System.currentTimeMillis()
        }

        companion object {
            private const val DEFAULT_DOWNLOAD_BUFFER_SIZE = 8 * 1024
        }
    }

}
