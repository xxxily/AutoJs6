@file:Suppress("MayBeConstant")

package org.autojs.autojs.runtime.api.augment.http

import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.http.MutableOkHttp
import org.autojs.autojs.rhino.extension.IterableExtensions.toNativeArray
import org.autojs.autojs.rhino.extension.MapExtensions.toNativeObject
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsFunction
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component1
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component2
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component3
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component4
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.rhino.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.Http.ClientConfig
import org.autojs.autojs.runtime.api.Http.ClientSummary
import org.autojs.autojs.runtime.api.Http.DownloadOptions
import org.autojs.autojs.runtime.api.Http.DownloadStatus
import org.autojs.autojs.runtime.api.Http.RequestInterceptorSpec
import org.autojs.autojs.runtime.api.Mime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.continuation.Continuation
import org.autojs.autojs.runtime.api.augment.continuation.Creator
import org.autojs.autojs.runtime.api.augment.http.RequestBuilder.Companion.applyOkHttpClientBuilder
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.coerceLongNumber
import org.autojs.autojs.util.RhinoUtils.coerceObject
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.handleAsyncOperation
import org.autojs.autojs.util.RhinoUtils.isUiThread
import org.autojs.autojs.util.RhinoUtils.js_json_stringify
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.autojs.autojs.util.RhinoUtils.withRhinoContext
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject.DONTENUM
import org.mozilla.javascript.ScriptableObject.PERMANENT
import org.mozilla.javascript.ScriptableObject.READONLY
import java.io.IOException

@Suppress("unused", "UNUSED_PARAMETER")
class Http(scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime) {

    override val selfAssignmentProperties = listOf(
        "__okhttp__" to scriptRuntime.http.okhttp to (READONLY or DONTENUM or PERMANENT)
    )

    override val selfAssignmentFunctions = listOf(
        ::client.name,
        ::clients.name,
        ::removeClient.name,
        ::download.name,
        ::downloads.name,
        ::downloadStatus.name,
        ::pauseDownload.name,
        ::resumeDownload.name,
        ::cancelDownload.name,
        ::buildRequest.name,
        ::request.name,
        ::requestAsync.name,
        ::get.name,
        ::getAsync.name,
        ::head.name,
        ::headAsync.name,
        ::post.name,
        ::postAsync.name,
        ::postJson.name,
        ::postJsonAsync.name,
        ::postMultipart.name,
        ::postMultipartAsync.name,
        "put",
        ::putAsync.name,
        ::delete.name,
        ::del.name,
        ::deleteAsync.name,
        ::delAsync.name,
    )

    companion object : Augmentable() {

        internal const val KEY_METHOD = "method"
        internal const val KEY_CONTENT_TYPE = "contentType"
        internal const val KEY_HEADERS = "headers"
        internal const val KEY_FILES = "files"
        internal const val KEY_BODY = "body"
        internal const val KEY_CLIENT = "client"
        internal const val KEY_CLIENT_NAME = "clientName"
        internal const val KEY_TIMEOUT = "timeout"

        private const val KEY_MAX_RETRIES = "maxRetries"
        private const val KEY_CACHE_BODY = "cacheBody"
        private const val KEY_BODY_CACHE_THRESHOLD_BYTES = "bodyCacheThresholdBytes"

        private const val METHOD_GET = "GET"
        private const val METHOD_HEAD = "HEAD"
        private const val METHOD_POST = "POST"
        private const val METHOD_PUT = "PUT"
        private const val METHOD_DELETE = "DELETE"

        private val DEFAULT_CONTENT_TYPE = Mime.APPLICATION_X_WWW_FORM_URLENCODED

        @JvmField
        val DEFAULT_TIMEOUT = MutableOkHttp.DEFAULT_TIMEOUT

        @JvmField
        val DEFAULT_MAX_RETRIES = MutableOkHttp.DEFAULT_MAX_RETRIES

        @JvmField
        val DEFAULT_CACHE_BODY = false

        @JvmField
        val DEFAULT_BODY_CACHE_THRESHOLD_BYTES = 8L * 1024 * 1024

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun client(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeObject = ensureArgumentsLengthInRange(args, 1..2) { argList ->
            val (name, options) = argList
            val clientName = coerceString(name).trim()
            require(clientName.isNotEmpty()) { "Argument name for http.client() must not be empty" }
            val summary = when {
                options.isJsNullish() -> scriptRuntime.http.clientSummary(clientName)
                options is NativeObject -> scriptRuntime.http.putClient(parseClientConfig(clientName, options))
                else -> throw WrappedIllegalArgumentException("Argument \"options\" ${options.jsBrief()} for http.client() must be a JavaScript Object")
            }
            summaryToNative(summary)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun clients(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsIsEmpty(args) {
            scriptRuntime.http.clientSummaries().map { summaryToNative(it) }.toNativeArray()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun removeClient(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) {
            scriptRuntime.http.removeClient(coerceString(it).trim())
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun download(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeObject = ensureArgumentsLengthInRange(args, 2..3) { argList ->
            val (url, path, options) = argList
            val status = scriptRuntime.http.enqueueDownload(
                url = coerceString(url),
                path = scriptRuntime.files.nonNullPath(coerceString(path)),
                options = parseDownloadOptions(options),
            )
            downloadStatusToNative(status)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun downloads(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsIsEmpty(args) {
            scriptRuntime.http.downloadStatuses().map { downloadStatusToNative(it) }.toNativeArray()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun downloadStatus(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = ensureArgumentsOnlyOne(args) {
            scriptRuntime.http.downloadStatus(coerceString(it))?.let(::downloadStatusToNative) ?: UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun pauseDownload(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeObject = ensureArgumentsOnlyOne(args) {
            downloadStatusToNative(scriptRuntime.http.pauseDownload(coerceString(it)))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun resumeDownload(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeObject = ensureArgumentsOnlyOne(args) {
            downloadStatusToNative(scriptRuntime.http.resumeDownload(coerceString(it)))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun cancelDownload(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeObject = ensureArgumentsOnlyOne(args) {
            downloadStatusToNative(scriptRuntime.http.cancelDownload(coerceString(it)))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun buildRequest(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Request = ensureArgumentsLengthInRange(args, 1..2) {
            val (url, options) = it
            buildRequestRhinoWithRuntime(scriptRuntime, url, options)
        }

        @JvmStatic
        @JvmOverloads
        @RhinoFunctionBody
        fun buildRequestRhinoWithRuntime(scriptRuntime: ScriptRuntime, url: Any?, options: Any? = null): Request = when {
            options.isJsNullish() -> RequestBuilder(scriptRuntime, url).build()
            options is NativeObject -> RequestBuilder(scriptRuntime, url, options).build()
            else -> throw WrappedIllegalArgumentException("Argument options ${options.jsBrief()} must be a JavaScript Object")
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun request(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = ensureArgumentsLengthInRange(args, 1..3) {
            val (url, options, callback) = it
            requestRhinoWithRuntime(scriptRuntime, url, options, callback)
        }

        @JvmStatic
        @JvmOverloads
        @RhinoFunctionBody
        fun requestRhinoWithRuntime(scriptRuntime: ScriptRuntime, url: Any?, options: Any? = null, callback: Any? = null): Any {

            val cont: Creator? = when {
                callback.isJsFunction() && isUiThread() && Continuation.isEnabled(scriptRuntime) -> Continuation.createRhinoWithRuntime(scriptRuntime)
                else -> null
            }

            val prepared = prepareRequest(scriptRuntime, url, options)

            if (!callback.isJsFunction() && cont == null) {
                return wrapResponse(scriptRuntime, prepared.call.execute(), prepared.cacheBody, prepared.cacheThreshold)
            }

            scriptRuntime.loopers.waitWhenIdle(true)
            prepared.call.enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    val wrappedResponse = wrapResponse(scriptRuntime, response, prepared.cacheBody, prepared.cacheThreshold)
                    cont?.resume(wrappedResponse)
                    if (callback is BaseFunction) {
                        scriptRuntime.bridges.call(callback, callback, arrayOf(wrappedResponse, null))
                    }
                    scriptRuntime.loopers.waitWhenIdle(false)
                }

                override fun onFailure(call: Call, e: IOException) {
                    cont?.resumeError(e)
                    if (callback is BaseFunction) {
                        scriptRuntime.bridges.call(callback, callback, arrayOf(null, e))
                    }
                    scriptRuntime.loopers.waitWhenIdle(false)
                }
            })

            cont?.await()
            return UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun requestAsync(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeObject = ensureArgumentsLengthInRange(args, 1..3) {
            val (url, options, callback) = it
            requestAsyncRhinoWithRuntime(scriptRuntime, url, options, callback)
        }

        @JvmStatic
        @JvmOverloads
        @RhinoFunctionBody
        fun requestAsyncRhinoWithRuntime(scriptRuntime: ScriptRuntime, url: Any?, options: Any? = null, callback: Any? = null): NativeObject {

            val prepared = prepareRequest(scriptRuntime, url, options)

            return handleAsyncOperation(
                scriptRuntime,
                operation = {
                    // Execute network call in background thread.
                    // zh-CN: 在后台线程执行网络请求.
                    prepared.call.execute()
                },
                uiMapper = { response ->
                    // Wrap response into Rhino object on UI thread with Rhino Context.
                    // zh-CN: 在 UI 线程进入 Rhino Context, 将 Response 包装为 Rhino 对象.
                    withRhinoContext(scriptRuntime) {
                        val wrapped = wrapResponse(scriptRuntime, response, prepared.cacheBody, prepared.cacheThreshold)

                        // Optional: if user provided callback, call it on UI thread.
                        // zh-CN: 可选: 如果用户提供了 callback, 则在 UI 线程调用.
                        if (callback is BaseFunction) {
                            scriptRuntime.bridges.call(callback, callback, arrayOf(wrapped, null))
                        }
                        wrapped
                    }
                },
            )
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun get(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = ensureArgumentsLengthInRange(args, 1..3) {
            val (url, options, callback) = it
            getRhinoWithRuntime(scriptRuntime, url, options, callback)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun head(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = ensureArgumentsLengthInRange(args, 1..3) {
            val (url, options, callback) = it
            headRhinoWithRuntime(scriptRuntime, url, options, callback)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun headAsync(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeObject = ensureArgumentsLengthInRange(args, 1..3) {
            val (url, options, callback) = it
            headAsyncRhinoWithRuntime(scriptRuntime, url, options, callback)
        }

        @JvmStatic
        @JvmOverloads
        @RhinoFunctionBody
        fun headRhinoWithRuntime(scriptRuntime: ScriptRuntime, url: Any?, options: Any? = null, callback: Any? = null): Any {
            val niceOptions = if (options.isJsNullish()) newNativeObject() else options
            require(niceOptions is NativeObject) { "Argument \"options\" ${options.jsBrief()} for http.head must be a JavaScript Object" }
            put(niceOptions, KEY_METHOD to METHOD_HEAD)
            return requestRhinoWithRuntime(scriptRuntime, url, niceOptions, callback)
        }

        @JvmStatic
        @JvmOverloads
        @RhinoFunctionBody
        fun headAsyncRhinoWithRuntime(scriptRuntime: ScriptRuntime, url: Any?, options: Any? = null, callback: Any? = null): NativeObject {
            val niceOptions = if (options.isJsNullish()) newNativeObject() else options
            require(niceOptions is NativeObject) { "Argument \"options\" ${options.jsBrief()} for http.headAsync must be a JavaScript Object" }
            put(niceOptions, KEY_METHOD to METHOD_HEAD)
            return requestAsyncRhinoWithRuntime(scriptRuntime, url, niceOptions, callback)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getAsync(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeObject = ensureArgumentsLengthInRange(args, 1..3) {
            val (url, options, callback) = it
            getAsyncRhinoWithRuntime(scriptRuntime, url, options, callback)
        }

        @JvmStatic
        @JvmOverloads
        @RhinoFunctionBody
        fun getAsyncRhinoWithRuntime(scriptRuntime: ScriptRuntime, url: Any?, options: Any? = null, callback: Any? = null): NativeObject {
            val niceOptions = if (options.isJsNullish()) newNativeObject() else options
            require(niceOptions is NativeObject) { "Argument \"options\" ${options.jsBrief()} for http.getAsync must be a JavaScript Object" }
            put(niceOptions, KEY_METHOD to METHOD_GET)
            return requestAsyncRhinoWithRuntime(scriptRuntime, url, niceOptions, callback)
        }

        @JvmStatic
        @JvmOverloads
        @RhinoFunctionBody
        fun getRhinoWithRuntime(scriptRuntime: ScriptRuntime, url: Any?, options: Any? = null, callback: Any? = null): Any {
            val niceOptions = if (options.isJsNullish()) newNativeObject() else options
            require(niceOptions is NativeObject) { "Argument \"options\" ${options.jsBrief()} for http.get must be a JavaScript Object" }
            put(niceOptions, KEY_METHOD to METHOD_GET)
            return requestRhinoWithRuntime(scriptRuntime, url, niceOptions, callback)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun put(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = ensureArgumentsLengthInRange(args, 1..4) {
            val (url, data, options, callback) = it
            putRhinoWithRuntime(scriptRuntime, url, data, options, callback)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun putAsync(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeObject = ensureArgumentsLengthInRange(args, 1..4) {
            val (url, data, options, callback) = it
            putAsyncRhinoWithRuntime(scriptRuntime, url, data, options, callback)
        }

        @JvmStatic
        @JvmOverloads
        @RhinoFunctionBody
        fun putRhinoWithRuntime(scriptRuntime: ScriptRuntime, url: Any?, data: Any? = null, options: Any? = null, callback: Any? = null): Any {
            val niceOptions = if (options.isJsNullish()) newNativeObject() else options
            require(niceOptions is NativeObject) { "Argument \"options\" ${options.jsBrief()} for http.put must be a JavaScript Object" }
            put(niceOptions, KEY_METHOD to METHOD_PUT)
            putIfAbsent(niceOptions, KEY_CONTENT_TYPE to DEFAULT_CONTENT_TYPE)
            fillPostData(niceOptions, data)
            return requestRhinoWithRuntime(scriptRuntime, url, niceOptions, callback)
        }

        @JvmStatic
        @JvmOverloads
        @RhinoFunctionBody
        fun putAsyncRhinoWithRuntime(scriptRuntime: ScriptRuntime, url: Any?, data: Any? = null, options: Any? = null, callback: Any? = null): NativeObject {
            val niceOptions = if (options.isJsNullish()) newNativeObject() else options
            require(niceOptions is NativeObject) { "Argument \"options\" ${options.jsBrief()} for http.putAsync must be a JavaScript Object" }
            put(niceOptions, KEY_METHOD to METHOD_PUT)
            putIfAbsent(niceOptions, KEY_CONTENT_TYPE to DEFAULT_CONTENT_TYPE)
            fillPostData(niceOptions, data)
            return requestAsyncRhinoWithRuntime(scriptRuntime, url, niceOptions, callback)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun delete(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = ensureArgumentsLengthInRange(args, 1..4) {
            val (url, data, options, callback) = it
            deleteRhinoWithRuntime(scriptRuntime, url, data, options, callback)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun del(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = ensureArgumentsLengthInRange(args, 1..4) {
            val (url, data, options, callback) = it
            deleteRhinoWithRuntime(scriptRuntime, url, data, options, callback)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun deleteAsync(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeObject = ensureArgumentsLengthInRange(args, 1..4) {
            val (url, data, options, callback) = it
            deleteAsyncRhinoWithRuntime(scriptRuntime, url, data, options, callback)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun delAsync(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeObject = ensureArgumentsLengthInRange(args, 1..4) {
            val (url, data, options, callback) = it
            deleteAsyncRhinoWithRuntime(scriptRuntime, url, data, options, callback)
        }

        @JvmStatic
        @JvmOverloads
        @RhinoFunctionBody
        fun deleteRhinoWithRuntime(scriptRuntime: ScriptRuntime, url: Any?, data: Any? = null, options: Any? = null, callback: Any? = null): Any {
            val niceOptions = if (options.isJsNullish()) newNativeObject() else options
            require(niceOptions is NativeObject) { "Argument \"options\" ${options.jsBrief()} for http.delete must be a JavaScript Object" }
            put(niceOptions, KEY_METHOD to METHOD_DELETE)
            putIfAbsent(niceOptions, KEY_CONTENT_TYPE to DEFAULT_CONTENT_TYPE)
            fillPostData(niceOptions, data)
            return requestRhinoWithRuntime(scriptRuntime, url, niceOptions, callback)
        }

        @JvmStatic
        @JvmOverloads
        @RhinoFunctionBody
        fun deleteAsyncRhinoWithRuntime(scriptRuntime: ScriptRuntime, url: Any?, data: Any? = null, options: Any? = null, callback: Any? = null): NativeObject {
            val niceOptions = if (options.isJsNullish()) newNativeObject() else options
            require(niceOptions is NativeObject) { "Argument \"options\" ${options.jsBrief()} for http.deleteAsync must be a JavaScript Object" }
            put(niceOptions, KEY_METHOD to METHOD_DELETE)
            putIfAbsent(niceOptions, KEY_CONTENT_TYPE to DEFAULT_CONTENT_TYPE)
            fillPostData(niceOptions, data)
            return requestAsyncRhinoWithRuntime(scriptRuntime, url, niceOptions, callback)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun post(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = ensureArgumentsLengthInRange(args, 1..4) {
            val (url, data, options, callback) = it
            postRhinoWithRuntime(scriptRuntime, url, data, options, callback)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun postAsync(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeObject = ensureArgumentsLengthInRange(args, 1..4) {
            val (url, data, options, callback) = it
            postAsyncRhinoWithRuntime(scriptRuntime, url, data, options, callback)
        }

        @JvmStatic
        @JvmOverloads
        @RhinoFunctionBody
        fun postAsyncRhinoWithRuntime(scriptRuntime: ScriptRuntime, url: Any?, data: Any? = null, options: Any? = null, callback: Any? = null): NativeObject {
            val niceOptions = if (options.isJsNullish()) newNativeObject() else options
            require(niceOptions is NativeObject) { "Argument \"options\" ${options.jsBrief()} for http.postAsync must be a JavaScript Object" }
            put(niceOptions, KEY_METHOD to METHOD_POST)
            putIfAbsent(niceOptions, KEY_CONTENT_TYPE to DEFAULT_CONTENT_TYPE)
            fillPostData(niceOptions, data)
            return requestAsyncRhinoWithRuntime(scriptRuntime, url, niceOptions, callback)
        }

        @JvmStatic
        @JvmOverloads
        @RhinoFunctionBody
        fun postRhinoWithRuntime(scriptRuntime: ScriptRuntime, url: Any?, data: Any? = null, options: Any? = null, callback: Any? = null): Any {
            val niceOptions = if (options.isJsNullish()) newNativeObject() else options
            require(niceOptions is NativeObject) { "Argument \"options\" ${options.jsBrief()} for http.post must be a JavaScript Object" }
            put(niceOptions, KEY_METHOD to METHOD_POST)
            putIfAbsent(niceOptions, KEY_CONTENT_TYPE to DEFAULT_CONTENT_TYPE)
            fillPostData(niceOptions, data)
            return requestRhinoWithRuntime(scriptRuntime, url, niceOptions, callback)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun postJson(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = ensureArgumentsLengthInRange(args, 1..4) {
            val (url, data, options, callback) = it
            postJsonRhinoWithRuntime(scriptRuntime, url, data, options, callback)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun postJsonAsync(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeObject = ensureArgumentsLengthInRange(args, 1..4) {
            val (url, data, options, callback) = it
            postJsonAsyncRhinoWithRuntime(scriptRuntime, url, data, options, callback)
        }

        @JvmStatic
        @JvmOverloads
        @RhinoFunctionBody
        fun postJsonAsyncRhinoWithRuntime(scriptRuntime: ScriptRuntime, url: Any?, data: Any?, options: Any? = null, callback: Any? = null): NativeObject {
            val niceOptions = if (options.isJsNullish()) newNativeObject() else options
            require(niceOptions is NativeObject) { "Argument \"options\" ${options.jsBrief()} for http.postJsonAsync must be a JavaScript Object" }
            put(niceOptions, KEY_CONTENT_TYPE to Mime.APPLICATION_JSON)
            return postAsyncRhinoWithRuntime(scriptRuntime, url, data, niceOptions, callback)
        }

        @JvmStatic
        @JvmOverloads
        @RhinoFunctionBody
        fun postJsonRhinoWithRuntime(scriptRuntime: ScriptRuntime, url: Any?, data: Any?, options: Any? = null, callback: Any? = null): Any {
            val niceOptions = if (options.isJsNullish()) newNativeObject() else options
            require(niceOptions is NativeObject) { "Argument \"options\" ${options.jsBrief()} for http.postJson] must be a JavaScript Object" }
            put(niceOptions, KEY_CONTENT_TYPE to Mime.APPLICATION_JSON)
            return postRhinoWithRuntime(scriptRuntime, url, data, niceOptions, callback)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun postMultipart(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = ensureArgumentsLengthInRange(args, 1..4) {
            val (url, files, options, callback) = it
            postMultipartRhinoWithRuntime(scriptRuntime, url, files, options, callback)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun postMultipartAsync(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeObject = ensureArgumentsLengthInRange(args, 1..4) {
            val (url, files, options, callback) = it
            postMultipartAsyncRhinoWithRuntime(scriptRuntime, url, files, options, callback)
        }

        @JvmStatic
        @JvmOverloads
        @RhinoFunctionBody
        fun postMultipartAsyncRhinoWithRuntime(scriptRuntime: ScriptRuntime, url: Any?, files: Any?, options: Any? = null, callback: Any? = null): NativeObject {
            val niceOptions = if (options.isJsNullish()) newNativeObject() else options
            require(niceOptions is NativeObject) { "Argument \"options\" ${options.jsBrief()} for http.postMultipartAsync must be a JavaScript Object" }
            listOf(
                KEY_METHOD to METHOD_POST,
                KEY_CONTENT_TYPE to Mime.MULTIPART_FORM_DATA,
                KEY_FILES to files,
            ).let { list -> put(niceOptions, list) }
            return requestAsyncRhinoWithRuntime(scriptRuntime, url, niceOptions, callback)
        }

        @JvmStatic
        @JvmOverloads
        @RhinoFunctionBody
        fun postMultipartRhinoWithRuntime(scriptRuntime: ScriptRuntime, url: Any?, files: Any?, options: Any? = null, callback: Any? = null): Any {
            val niceOptions = if (options.isJsNullish()) newNativeObject() else options
            require(niceOptions is NativeObject) { "Argument \"options\" ${options.jsBrief()} for http.postMultipart] must be a JavaScript Object" }
            listOf(
                KEY_METHOD to METHOD_POST,
                KEY_CONTENT_TYPE to Mime.MULTIPART_FORM_DATA,
                KEY_FILES to files,
            ).let { list -> put(niceOptions, list) }
            return requestRhinoWithRuntime(scriptRuntime, url, niceOptions, callback)
        }

        private fun fillPostData(options: NativeObject, data: Any?) {
            when (options.prop(KEY_CONTENT_TYPE)) {
                DEFAULT_CONTENT_TYPE -> {
                    val builder = FormBody.Builder()
                    coerceObject(data, newNativeObject()).forEach { (key, value) ->
                        builder.add(coerceString(key), Context.toString(value))
                    }
                    options.put(KEY_BODY, options, builder.build())
                }
                Mime.APPLICATION_JSON -> {
                    options.put(KEY_BODY, options, js_json_stringify(data))
                }
                else -> {
                    options.put(KEY_BODY, options, data)
                }
            }
        }

        private data class PreparedRequest(
            val call: Call,
            val cacheBody: Boolean,
            val cacheThreshold: Long,
        )

        private fun prepareRequest(scriptRuntime: ScriptRuntime, url: Any?, options: Any?): PreparedRequest {

            // Normalize options as a NativeObject.
            // zh-CN: 将 options 规范化为 NativeObject.
            val opt = coerceObject(options, newNativeObject())

            // Apply OkHttp runtime configs (retries/client builder).
            // zh-CN: 应用 OkHttp 运行时配置 (重试/Client Builder).
            val namedClient = clientNameFromOptions(opt)
            val requestClient = when {
                namedClient != null -> scriptRuntime.http.resolveClient(namedClient)
                else -> scriptRuntime.http.okhttp.apply {
                    setMaxRetries(coerceIntNumber(opt.prop(KEY_MAX_RETRIES), getMaxRetries()))
                    applyOkHttpClientBuilder(opt)
                }.client()
            }

            // Cache policy for response body wrapper.
            // zh-CN: Response body wrapper 的缓存策略.
            val cacheBody = opt.inquire(KEY_CACHE_BODY, ::coerceBoolean, DEFAULT_CACHE_BODY)
            val cacheThreshold = coerceLongNumber(opt.prop(KEY_BODY_CACHE_THRESHOLD_BYTES), DEFAULT_BODY_CACHE_THRESHOLD_BYTES)

            // Build request with normalized options and create call.
            // zh-CN: 使用规范化后的 options 构建 Request 并创建 Call.
            val request = buildRequestRhinoWithRuntime(scriptRuntime, url, opt)
            val call = requestClient.newCall(request)

            return PreparedRequest(
                call = call,
                cacheBody = cacheBody,
                cacheThreshold = cacheThreshold,
            )
        }

        private fun wrapResponse(scriptRuntime: ScriptRuntime, response: Response, cacheBody: Boolean, cacheThreshold: Long): Any {

            // Wrap okhttp3.Response into a Rhino-friendly object.
            // zh-CN: 将 okhttp3.Response 包装为 Rhino 可用对象.
            return ResponseWrapper(scriptRuntime, response, cacheBody, cacheThreshold).wrap()
        }

        private fun parseClientConfig(name: String, options: NativeObject): ClientConfig {
            return ClientConfig(
                name = name,
                timeoutMillis = coerceLongNumber(options.prop(KEY_TIMEOUT), DEFAULT_TIMEOUT),
                maxRetries = coerceIntNumber(options.prop(KEY_MAX_RETRIES), DEFAULT_MAX_RETRIES),
                defaultHeaders = parseStringMap(options.inquire(listOf("defaultHeaders", "headers"))),
                interceptors = parseInterceptors(options.prop("interceptors")),
                certificatePins = parsePins(options.inquire(listOf("certificatePins", "pins"))),
                allowedHosts = parseStringList(options.inquire(listOf("allowedHosts", "hosts"))).toSet(),
                followRedirects = options.prop("followRedirects").takeUnless { it.isJsNullish() }?.let { coerceBoolean(it) },
            )
        }

        private fun parseDownloadOptions(options: Any?): DownloadOptions {
            val opt = coerceObject(options, newNativeObject())
            val clientName = clientNameFromOptions(opt)
            return DownloadOptions(
                id = coerceString(opt.prop("id"), java.util.UUID.randomUUID().toString()),
                clientName = clientName,
                headers = parseStringMap(opt.prop(KEY_HEADERS)),
                resume = opt.inquire("resume", ::coerceBoolean, true),
                overwrite = opt.inquire("overwrite", ::coerceBoolean, true),
            )
        }

        private fun clientNameFromOptions(options: NativeObject): String? {
            val clientName = options.prop(KEY_CLIENT_NAME).takeUnless { it.isJsNullish() }
            if (clientName != null) return coerceString(clientName).trim().takeIf { it.isNotEmpty() }
            val client = options.prop(KEY_CLIENT).takeUnless { it.isJsNullish() }
            return when (client) {
                is CharSequence -> coerceString(client).trim().takeIf { it.isNotEmpty() }
                else -> null
            }
        }

        private fun parseInterceptors(raw: Any?): List<RequestInterceptorSpec> {
            return parseAnyList(raw).map { item ->
                require(item is NativeObject) { "HTTP interceptor ${item.jsBrief()} must be a JavaScript Object" }
                RequestInterceptorSpec(
                    type = coerceString(item.prop("type"), "header").trim(),
                    name = item.prop("name").takeUnless { it.isJsNullish() }?.let { coerceString(it) },
                    value = item.prop("value").takeUnless { it.isJsNullish() }?.let { coerceString(it) },
                )
            }
        }

        private fun parsePins(raw: Any?): Map<String, List<String>> {
            if (raw.isJsNullish()) return emptyMap()
            require(raw is NativeObject) { "HTTP certificate pins ${raw.jsBrief()} must be a JavaScript Object" }
            return raw.entries.associate { (host, pins) ->
                coerceString(host) to parseStringList(pins)
            }.filterValues { it.isNotEmpty() }
        }

        private fun parseStringMap(raw: Any?): Map<String, String> {
            if (raw.isJsNullish()) return emptyMap()
            require(raw is NativeObject) { "Expected a JavaScript Object, got ${raw.jsBrief()}" }
            return raw.entries.associate { (key, value) -> coerceString(key) to coerceString(value) }
        }

        private fun parseStringList(raw: Any?): List<String> = parseAnyList(raw).map { coerceString(it) }.filter { it.isNotBlank() }

        private fun parseAnyList(raw: Any?): List<Any?> = when {
            raw.isJsNullish() -> emptyList()
            raw is NativeArray -> (0 until raw.length.toInt()).map { raw.get(it, raw) }
            raw is Iterable<*> -> raw.toList()
            else -> listOf(raw)
        }

        private fun summaryToNative(summary: ClientSummary) = summary.toMap().toNativeObject()

        private fun downloadStatusToNative(status: DownloadStatus) = status.toMap().toNativeObject()

    }

}
