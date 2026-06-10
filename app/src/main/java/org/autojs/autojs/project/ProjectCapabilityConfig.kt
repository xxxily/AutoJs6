package org.autojs.autojs.project

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import org.autojs.autojs.annotation.SerializedNameCompatible
import org.autojs.autojs.annotation.SerializedNameCompatible.With

class ProjectRiskPolicy : FuzzyDeserializer.OriginalJsonKeyAware {

    @Transient
    private val originalJsonKeys = LinkedHashMap<String, String>()

    @SerializedName("default")
    @field:SerializedNameCompatible(
        With(value = "defaultPolicy"),
        With(value = "fallback"),
        With(value = "fallbackPolicy"),
    )
    var defaultAction: String = "prompt"

    @SerializedName("undeclared")
    @field:SerializedNameCompatible(
        With(value = "undeclaredPolicy"),
        With(value = "missingCapability"),
    )
    var undeclaredAction: String = "prompt"

    @SerializedName("high")
    @field:SerializedNameCompatible(
        With(value = "highRisk"),
        With(value = "highRiskPolicy"),
    )
    var highRiskAction: String = "prompt"

    @SerializedName("critical")
    @field:SerializedNameCompatible(
        With(value = "criticalRisk"),
        With(value = "criticalRiskPolicy"),
    )
    var criticalRiskAction: String = "reject"

    @SerializedName("rememberAllowed")
    @field:SerializedNameCompatible(
        With(value = "remember"),
        With(value = "rememberChoice"),
    )
    var rememberAllowed: Boolean = true

    override fun recordOriginalJsonKey(canonicalKey: String, originalKey: String) {
        originalJsonKeys[canonicalKey] = originalKey
    }

    fun actionFor(riskLevel: String, undeclared: Boolean): String {
        if (undeclared) return undeclaredAction.ifBlank { defaultAction }
        return when (riskLevel.lowercase()) {
            "critical" -> criticalRiskAction
            "high" -> highRiskAction
            else -> defaultAction
        }.ifBlank { defaultAction }
    }

    fun applyOriginalJsonKeys(json: JsonObject, detectConflicts: Boolean) {
        ProjectCapabilityJsonKeys.apply(json, originalJsonKeys, detectConflicts, "riskPolicy")
    }
}

class ProjectNetworkPolicy : FuzzyDeserializer.OriginalJsonKeyAware {

    @Transient
    private val originalJsonKeys = LinkedHashMap<String, String>()

    @SerializedName("allowedDomains")
    @field:SerializedNameCompatible(
        With(value = "domains"),
        With(value = "domainWhitelist"),
        With(value = "allowDomains"),
    )
    var allowedDomains: List<String> = emptyList()

    @SerializedName("blockedDomains")
    @field:SerializedNameCompatible(
        With(value = "denyDomains"),
        With(value = "domainBlacklist"),
    )
    var blockedDomains: List<String> = emptyList()

    @SerializedName("allowCleartext")
    @field:SerializedNameCompatible(
        With(value = "cleartext"),
        With(value = "http"),
    )
    var allowCleartext: Boolean = true

    override fun recordOriginalJsonKey(canonicalKey: String, originalKey: String) {
        originalJsonKeys[canonicalKey] = originalKey
    }

    fun applyOriginalJsonKeys(json: JsonObject, detectConflicts: Boolean) {
        ProjectCapabilityJsonKeys.apply(json, originalJsonKeys, detectConflicts, "networkPolicy")
    }
}

class ProjectFilePolicy : FuzzyDeserializer.OriginalJsonKeyAware {

    @Transient
    private val originalJsonKeys = LinkedHashMap<String, String>()

    @SerializedName("allowedPaths")
    @field:SerializedNameCompatible(
        With(value = "paths"),
        With(value = "readablePaths"),
        With(value = "allowPaths"),
    )
    var allowedPaths: List<String> = emptyList()

    @SerializedName("writablePaths")
    @field:SerializedNameCompatible(
        With(value = "writePaths"),
        With(value = "writeablePaths"),
    )
    var writablePaths: List<String> = emptyList()

    @SerializedName("deletablePaths")
    @field:SerializedNameCompatible(
        With(value = "deletePaths"),
        With(value = "removablePaths"),
    )
    var deletablePaths: List<String> = emptyList()

    override fun recordOriginalJsonKey(canonicalKey: String, originalKey: String) {
        originalJsonKeys[canonicalKey] = originalKey
    }

    fun applyOriginalJsonKeys(json: JsonObject, detectConflicts: Boolean) {
        ProjectCapabilityJsonKeys.apply(json, originalJsonKeys, detectConflicts, "filePolicy")
    }
}

class ProjectPrivilegedPolicy : FuzzyDeserializer.OriginalJsonKeyAware {

    @Transient
    private val originalJsonKeys = LinkedHashMap<String, String>()

    @SerializedName("root")
    @field:SerializedNameCompatible(
        With(value = "allowRoot"),
        With(value = "usesRoot"),
    )
    var root: Boolean? = null

    @SerializedName("shizuku")
    @field:SerializedNameCompatible(
        With(value = "allowShizuku"),
        With(value = "usesShizuku"),
    )
    var shizuku: Boolean? = null

    @SerializedName("shell")
    @field:SerializedNameCompatible(
        With(value = "allowShell"),
        With(value = "usesShell"),
    )
    var shell: Boolean? = null

    @SerializedName("backend")
    @field:SerializedNameCompatible(
        With(value = "preferredBackend"),
        With(value = "by"),
    )
    var backend: String = "auto"

    @SerializedName("operations")
    @field:SerializedNameCompatible(
        With(value = "declaredOperations"),
        With(value = "allowedOperations"),
    )
    var operations: List<String> = emptyList()

    override fun recordOriginalJsonKey(canonicalKey: String, originalKey: String) {
        originalJsonKeys[canonicalKey] = originalKey
    }

    fun applyOriginalJsonKeys(json: JsonObject, detectConflicts: Boolean) {
        ProjectCapabilityJsonKeys.apply(json, originalJsonKeys, detectConflicts, "privilegedPolicy")
    }
}

private object ProjectCapabilityJsonKeys {
    fun apply(
        json: JsonObject,
        originalJsonKeys: Map<String, String>,
        detectConflicts: Boolean,
        owner: String,
    ) {
        originalJsonKeys.forEach { (canonicalKey, originalKey) ->
            if (canonicalKey == originalKey || !json.has(canonicalKey)) return@forEach
            if (json.has(originalKey)) {
                if (detectConflicts) {
                    throw IllegalStateException("Conflicting keys when serializing $owner: \"$canonicalKey\" and \"$originalKey\"")
                }
                json.remove(canonicalKey)
                return@forEach
            }
            val value = json.remove(canonicalKey)
            json.add(originalKey, value)
        }
    }
}
