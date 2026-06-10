package org.autojs.autojs.core.plugin.center

import android.os.DeadObjectException
import android.os.RemoteException

object PluginErrorMapper {

    fun fromThrowable(t: Throwable, elapsedMillis: Long? = null): PluginError {
        val message = t.message ?: t.toString()
        val causeClass = t.javaClass.name
        val code = when {
            message.contains("not found in installed apps", ignoreCase = true) -> PluginErrorCode.PLUGIN_NOT_INSTALLED
            message.contains("not installed", ignoreCase = true) -> PluginErrorCode.PLUGIN_NOT_INSTALLED
            message.contains("not enabled", ignoreCase = true) -> PluginErrorCode.PLUGIN_DISABLED
            message.contains("not authorized", ignoreCase = true) -> PluginErrorCode.NOT_AUTHORIZED
            message.contains("not trusted", ignoreCase = true) -> PluginErrorCode.SIGNATURE_UNTRUSTED
            message.contains("signature", ignoreCase = true) && message.contains("mismatch", ignoreCase = true) -> PluginErrorCode.SIGNATURE_UNTRUSTED
            message.contains("fingerprint", ignoreCase = true) && message.contains("mismatch", ignoreCase = true) -> PluginErrorCode.SIGNATURE_UNTRUSTED
            message.contains("version", ignoreCase = true) && message.contains("incompatible", ignoreCase = true) -> PluginErrorCode.VERSION_INCOMPATIBLE
            message.contains("version", ignoreCase = true) && message.contains("too low", ignoreCase = true) -> PluginErrorCode.VERSION_INCOMPATIBLE
            message.contains("protocol", ignoreCase = true) && message.contains("mismatch", ignoreCase = true) -> PluginErrorCode.PROTOCOL_MISMATCH
            t is SecurityException -> PluginErrorCode.BIND_SECURITY_EXCEPTION
            t is DeadObjectException -> PluginErrorCode.DEAD_OBJECT
            t is RemoteException -> PluginErrorCode.DEAD_OBJECT
            message.contains("No OCR service found", ignoreCase = true) -> PluginErrorCode.SERVICE_NOT_FOUND
            message.contains("bindService SecurityException", ignoreCase = true) -> PluginErrorCode.BIND_SECURITY_EXCEPTION
            message.contains("bindService failed", ignoreCase = true) -> PluginErrorCode.BIND_FAILED
            message.contains("bindService timeout", ignoreCase = true) -> PluginErrorCode.HANDSHAKE_TIMEOUT
            message.contains("timeout", ignoreCase = true) && message.contains("bind", ignoreCase = true) -> PluginErrorCode.HANDSHAKE_TIMEOUT
            else -> PluginErrorCode.INTERNAL_ERROR
        }
        return PluginError(
            code = code,
            message = message,
            causeClass = causeClass,
            elapsedMillis = elapsedMillis,
        )
    }

    fun shouldRecommendActivation(error: PluginError): Boolean {
        return when (error.code) {
            PluginErrorCode.BIND_FAILED,
            PluginErrorCode.HANDSHAKE_TIMEOUT,
            PluginErrorCode.DEAD_OBJECT,
            -> true
            else -> false
        }
    }
}
