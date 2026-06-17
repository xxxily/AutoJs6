package org.autojs.autojs.core.plugin.center

import android.os.Bundle
import org.autojs.plugin.paddle.ocr.api.PluginInfo
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

data class PluginProvidedEngine(
    val id: String? = null,
    val engine: String? = null,
    val variant: String? = null,
    val label: String? = null,
) {
    val displayName: String
        get() = listOfNotNull(label, id, engine, variant).firstOrNull { it.isNotBlank() }.orEmpty()
}

data class PluginCapabilityManifest(
    val pluginType: String = TYPE_APPLICATION,
    val capabilities: List<String> = emptyList(),
    val permissions: List<String> = emptyList(),
    val riskLevel: String? = null,
    val minAutoJsVersion: String? = null,
    val documentationUrl: String? = null,
    val examples: List<String> = emptyList(),
    val engines: List<PluginProvidedEngine> = emptyList(),
) {
    val isEmpty: Boolean
        get() = capabilities.isEmpty()
            && permissions.isEmpty()
            && riskLevel.isNullOrBlank()
            && minAutoJsVersion.isNullOrBlank()
            && documentationUrl.isNullOrBlank()
            && examples.isEmpty()
            && engines.isEmpty()

    fun mergedWith(fallback: PluginCapabilityManifest?): PluginCapabilityManifest {
        if (fallback == null || fallback.isEmpty) return this
        return PluginCapabilityManifest(
            pluginType = pluginType.takeIf { it.isNotBlank() } ?: fallback.pluginType,
            capabilities = capabilities.ifEmpty { fallback.capabilities },
            permissions = permissions.ifEmpty { fallback.permissions },
            riskLevel = riskLevel.takeUnless { it.isNullOrBlank() } ?: fallback.riskLevel,
            minAutoJsVersion = minAutoJsVersion.takeUnless { it.isNullOrBlank() } ?: fallback.minAutoJsVersion,
            documentationUrl = documentationUrl.takeUnless { it.isNullOrBlank() } ?: fallback.documentationUrl,
            examples = examples.ifEmpty { fallback.examples },
            engines = engines.ifEmpty { fallback.engines },
        )
    }

    fun compactSummary(): String? {
        val parts = mutableListOf<String>()
        engines.mapNotNull { it.displayName.takeIf { name -> name.isNotBlank() } }
            .takeIf { it.isNotEmpty() }
            ?.let { parts += "Engines: ${it.joinToString()}" }
        capabilities.takeIf { it.isNotEmpty() }?.let { parts += "Capabilities: ${it.joinToString()}" }
        permissions.takeIf { it.isNotEmpty() }?.let { parts += "Permissions: ${it.joinToString()}" }
        riskLevel?.takeIf { it.isNotBlank() }?.let { parts += "Risk: $it" }
        minAutoJsVersion?.takeIf { it.isNotBlank() }?.let { parts += "Min AutoJs6: $it" }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" | ")
    }

    companion object {
        const val TYPE_APPLICATION = "application"
        const val TYPE_OCR = "ocr"

        fun fromJson(
            json: JSONObject?,
            fallbackEngine: String? = null,
            fallbackVariant: String? = null,
            fallbackEngineId: String? = null,
        ): PluginCapabilityManifest {
            val obj = json ?: JSONObject()
            val engines = parseEngines(obj, fallbackEngine, fallbackVariant, fallbackEngineId)
            return PluginCapabilityManifest(
                pluginType = obj.optStringAny("pluginType", "type").ifBlank { inferPluginType(engines, fallbackEngine) },
                capabilities = obj.optStringList("capabilities", "capabilityIds", "provides"),
                permissions = obj.optStringList("permissions", "requiredPermissions", "androidPermissions"),
                riskLevel = obj.optStringAny("riskLevel", "risk").takeIf { it.isNotBlank() },
                minAutoJsVersion = obj.optStringAny("minAutoJsVersion", "minAutoJs6Version", "minVersion").takeIf { it.isNotBlank() },
                documentationUrl = obj.optStringAny("documentationUrl", "docsUrl", "docUrl").takeIf { it.isNotBlank() },
                examples = obj.optStringList("examples", "exampleUrls", "sampleUrls"),
                engines = engines,
            )
        }

        fun fromOcrPluginInfo(info: PluginInfo?, servicePermission: String?): PluginCapabilityManifest {
            val bundle = info?.capabilities
            val engine = PluginProvidedEngine(
                id = info?.id,
                engine = info?.engine,
                variant = info?.variant,
                label = info?.name,
            ).takeIf { it.displayName.isNotBlank() }
            return PluginCapabilityManifest(
                pluginType = TYPE_OCR,
                capabilities = bundle.stringList("capabilities", "capabilityIds", "provides").ifEmpty { listOf("ocr") },
                permissions = (bundle.stringList("permissions", "requiredPermissions", "androidPermissions") + listOfNotNull(servicePermission))
                    .filter { it.isNotBlank() }
                    .distinct(),
                riskLevel = bundle.stringValue("riskLevel", "risk") ?: "medium",
                minAutoJsVersion = bundle.stringValue("minAutoJsVersion", "minAutoJs6Version", "minVersion"),
                documentationUrl = bundle.stringValue("documentationUrl", "docsUrl", "docUrl"),
                examples = bundle.stringList("examples", "exampleUrls", "sampleUrls"),
                engines = listOfNotNull(engine),
            )
        }

        private fun inferPluginType(engines: List<PluginProvidedEngine>, fallbackEngine: String?): String {
            return if (engines.isNotEmpty() || fallbackEngine?.contains("ocr", ignoreCase = true) == true) TYPE_OCR else TYPE_APPLICATION
        }

        private fun parseEngines(
            json: JSONObject,
            fallbackEngine: String?,
            fallbackVariant: String?,
            fallbackEngineId: String?,
        ): List<PluginProvidedEngine> {
            val fromArray = json.optJSONArray("engines")?.let { arr ->
                List(arr.length()) { idx ->
                    val item = arr.optJSONObject(idx)
                    if (item != null) {
                        PluginProvidedEngine(
                            id = item.optStringAny("id", "engineId").takeIf { it.isNotBlank() },
                            engine = item.optString("engine").takeIf { it.isNotBlank() },
                            variant = item.optString("variant").takeIf { it.isNotBlank() },
                            label = item.optStringAny("label", "name", "title").takeIf { it.isNotBlank() },
                        )
                    } else {
                        PluginProvidedEngine(label = arr.optString(idx).takeIf { it.isNotBlank() })
                    }
                }
            }.orEmpty()
            val fallback = PluginProvidedEngine(
                id = fallbackEngineId,
                engine = fallbackEngine,
                variant = fallbackVariant,
            ).takeIf { it.displayName.isNotBlank() }
            return (fromArray + listOfNotNull(fallback)).distinctBy {
                listOf(it.id, it.engine, it.variant, it.label).joinToString("|")
            }
        }
    }
}

data class PluginIndexSignature(
    val algorithm: String? = null,
    val keyId: String? = null,
    val signature: String? = null,
    val payloadSha256: String? = null,
    val signedAt: String? = null,
) {
    companion object {
        fun fromJson(json: JSONObject?): PluginIndexSignature? {
            json ?: return null
            return PluginIndexSignature(
                algorithm = json.optStringAny("algorithm", "alg").takeIf { it.isNotBlank() },
                keyId = json.optStringAny("keyId", "kid").takeIf { it.isNotBlank() },
                signature = json.optString("signature").takeIf { it.isNotBlank() },
                payloadSha256 = json.optStringAny("payloadSha256", "sha256").takeIf { it.isNotBlank() },
                signedAt = json.optStringAny("signedAt", "createdAt").takeIf { it.isNotBlank() },
            )
        }
    }
}

object PluginIndexSecurity {
    private val SHA256_PATTERN = Regex("[0-9a-f]{64}")

    fun verifyPayloadDigest(payload: String, signature: PluginIndexSignature?): Boolean {
        val expected = signature?.payloadSha256?.let(::normalizeSha256) ?: return true
        return expected == sha256Hex(payload.toByteArray())
    }

    fun validateOfficialIndexEntry(entry: PluginIndexEntry): List<String> {
        val issues = mutableListOf<String>()
        if (entry.packageName.isBlank()) issues += "packageName is required"
        if (entry.title.isBlank()) issues += "title is required"

        val manifest = entry.manifest
        if (manifest.capabilities.isEmpty()) issues += "manifest.capabilities is required"
        if (manifest.riskLevel.isNullOrBlank()) issues += "manifest.riskLevel is required"
        if (manifest.minAutoJsVersion.isNullOrBlank()) issues += "manifest.minAutoJsVersion is required"
        if (manifest.documentationUrl.isNullOrBlank()) issues += "manifest.documentationUrl is required"

        if (entry.releases.isEmpty()) issues += "releases is required"
        entry.releases.forEachIndexed { index, release ->
            val prefix = "releases[$index]"
            if (release.versionName.isBlank()) issues += "$prefix.versionName is required"
            if (release.versionCode <= 0L) issues += "$prefix.versionCode must be positive"
            if (release.apkUrl.isNullOrBlank()) issues += "$prefix.apkUrl is required"
            if (!isValidSha256(release.apkSha256)) issues += "$prefix.apkSha256 must be a 64-character SHA-256 hex digest"
            if (release.certificateSha256.isEmpty()) {
                issues += "$prefix.certificateSha256 is required"
            } else if (release.certificateSha256.any { !isValidSha256(it) }) {
                issues += "$prefix.certificateSha256 must contain only 64-character SHA-256 hex digests"
            }
        }
        return issues
    }

    fun requireOfficialIndexEntry(entry: PluginIndexEntry) {
        val issues = validateOfficialIndexEntry(entry)
        require(issues.isEmpty()) {
            "Official plugin index entry ${entry.packageName.ifBlank { "<unknown>" }} is invalid: ${issues.joinToString("; ")}"
        }
    }

    fun normalizeSha256(value: String): String {
        return value.lowercase(Locale.ROOT)
            .replace("sha256:", "")
            .replace(" ", "")
            .replace(":", "")
    }

    fun normalizeSha256List(values: Collection<String>): List<String> {
        return values.map(::normalizeSha256)
            .filter { it.matches(SHA256_PATTERN) }
            .distinct()
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun isValidSha256(value: String?): Boolean {
        return !value.isNullOrBlank() && normalizeSha256(value).matches(SHA256_PATTERN)
    }
}

private fun JSONObject.optStringAny(vararg keys: String): String {
    return keys.firstNotNullOfOrNull { key -> optString(key).takeIf { it.isNotBlank() } }.orEmpty()
}

private fun JSONObject.optStringList(vararg keys: String): List<String> {
    return keys.firstNotNullOfOrNull { key ->
        when (val value = opt(key)) {
            is JSONArray -> List(value.length()) { idx -> value.optString(idx) }.filter { it.isNotBlank() }
            is String -> value.split(',', ';').map { it.trim() }.filter { it.isNotBlank() }
            else -> null
        }?.takeIf { it.isNotEmpty() }
    }.orEmpty().distinct()
}

private fun Bundle?.stringValue(vararg keys: String): String? {
    this ?: return null
    return keys.firstNotNullOfOrNull { key -> getString(key)?.takeIf { it.isNotBlank() } }
}

private fun Bundle?.stringList(vararg keys: String): List<String> {
    this ?: return emptyList()
    return keys.firstNotNullOfOrNull { key ->
        when (val value = get(key)) {
            is Array<*> -> value.filterIsInstance<String>().filter { it.isNotBlank() }
            is ArrayList<*> -> value.filterIsInstance<String>().filter { it.isNotBlank() }
            is String -> value.split(',', ';').map { it.trim() }.filter { it.isNotBlank() }
            else -> null
        }?.takeIf { it.isNotEmpty() }
    }.orEmpty().distinct()
}
