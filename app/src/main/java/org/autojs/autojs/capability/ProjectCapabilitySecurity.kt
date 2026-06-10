package org.autojs.autojs.capability

import android.content.Context
import com.google.gson.GsonBuilder
import org.autojs.autojs.observability.ScriptObservability
import org.autojs.autojs.project.ProjectConfig
import org.autojs.autojs.runtime.ScriptRuntime
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

enum class ProjectCapabilityAction(val wireName: String) {
    ALLOW("allow"),
    PROMPT("prompt"),
    REJECT("reject"),
}

data class ProjectCapabilityGuardRequest(
    val api: String,
    val capabilities: List<String>,
    val riskLevel: String = "high",
    val target: String = "",
    val accessType: String = "",
    val message: String = "",
)

data class ProjectCapabilityDecision(
    val action: ProjectCapabilityAction,
    val allowed: Boolean,
    val projectKey: String,
    val api: String,
    val capabilities: List<String>,
    val riskLevel: String,
    val target: String,
    val remembered: Boolean = false,
    val reason: String = "",
)

data class ProjectCapabilityPromptResult(
    val action: ProjectCapabilityAction,
    val remember: Boolean = false,
)

interface ProjectCapabilityAuthorizationStore {
    fun read(projectKey: String, capability: String): ProjectCapabilityAction?
    fun remember(projectKey: String, capability: String, action: ProjectCapabilityAction)
}

fun interface ProjectCapabilityPrompter {
    fun prompt(projectConfig: ProjectConfig, request: ProjectCapabilityGuardRequest, missingCapabilities: List<String>): ProjectCapabilityPromptResult
}

class SharedPreferencesProjectCapabilityAuthorizationStore(context: Context) : ProjectCapabilityAuthorizationStore {

    private val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    override fun read(projectKey: String, capability: String): ProjectCapabilityAction? {
        val value = prefs.getString(key(projectKey, capability), null) ?: return null
        return ProjectCapabilitySecurity.parseAction(value, ProjectCapabilityAction.PROMPT)
    }

    override fun remember(projectKey: String, capability: String, action: ProjectCapabilityAction) {
        prefs.edit().putString(key(projectKey, capability), action.wireName).apply()
    }

    private fun key(projectKey: String, capability: String): String {
        return "cap.${sha256(projectKey)}.${capability.lowercase(Locale.ROOT)}"
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val PREF_NAME = "project-capability-authorizations"
    }
}

class InMemoryProjectCapabilityAuthorizationStore : ProjectCapabilityAuthorizationStore {

    private val values = linkedMapOf<String, ProjectCapabilityAction>()

    override fun read(projectKey: String, capability: String): ProjectCapabilityAction? {
        return values[key(projectKey, capability)]
    }

    override fun remember(projectKey: String, capability: String, action: ProjectCapabilityAction) {
        values[key(projectKey, capability)] = action
    }

    private fun key(projectKey: String, capability: String): String {
        return "${projectKey.lowercase(Locale.ROOT)}|${capability.lowercase(Locale.ROOT)}"
    }
}

data class ProjectCapabilityAuditEntry(
    val id: Long,
    val timestamp: Long,
    val projectKey: String,
    val api: String,
    val capabilities: List<String>,
    val riskLevel: String,
    val action: String,
    val allowed: Boolean,
    val target: String,
    val message: String,
)

object ProjectCapabilityAuditLog {

    private val nextId = AtomicLong(1L)
    private val entries = CopyOnWriteArrayList<ProjectCapabilityAuditEntry>()
    private val gson = GsonBuilder().setPrettyPrinting().create()

    @JvmStatic
    fun record(decision: ProjectCapabilityDecision, message: String = decision.reason): ProjectCapabilityAuditEntry {
        val entry = ProjectCapabilityAuditEntry(
            id = nextId.getAndIncrement(),
            timestamp = System.currentTimeMillis(),
            projectKey = decision.projectKey,
            api = decision.api,
            capabilities = decision.capabilities,
            riskLevel = decision.riskLevel,
            action = decision.action.wireName,
            allowed = decision.allowed,
            target = decision.target.take(MAX_FIELD_LENGTH),
            message = message.take(MAX_FIELD_LENGTH),
        )
        entries += entry
        if (entries.size > MAX_ENTRIES) {
            entries.removeAt(0)
        }
        ScriptObservability.recordCapabilityEvent(entry)
        return entry
    }

    @JvmStatic
    fun snapshot(): List<ProjectCapabilityAuditEntry> = entries.toList()

    @JvmStatic
    fun clear() {
        entries.clear()
    }

    @JvmStatic
    fun exportJson(): String = gson.toJson(snapshot())

    private const val MAX_ENTRIES = 500
    private const val MAX_FIELD_LENGTH = 500
}

object ProjectCapabilitySecurity {

    private const val LEGACY_PROJECT_KEY = "legacy-script-without-project-manifest"

    @JvmStatic
    fun evaluate(
        projectConfig: ProjectConfig?,
        request: ProjectCapabilityGuardRequest,
        authorizationStore: ProjectCapabilityAuthorizationStore? = null,
        prompter: ProjectCapabilityPrompter? = null,
    ): ProjectCapabilityDecision {
        val normalizedCapabilities = normalizeCapabilities(request.capabilities)
        val projectKey = projectKey(projectConfig)
        if (projectConfig == null) {
            return decision(ProjectCapabilityAction.ALLOW, true, projectKey, request, normalizedCapabilities, reason = "No project capability manifest; legacy script allowed.")
        }

        val declared = normalizeCapabilities(projectConfig.capabilities)
        val missing = normalizedCapabilities.filterNot { it in declared }
        privilegedPolicyViolation(projectConfig, normalizedCapabilities)?.let { reason ->
            return decision(ProjectCapabilityAction.REJECT, false, projectKey, request, normalizedCapabilities, reason = reason)
        }
        filePolicyViolation(projectConfig, request)?.let { reason ->
            return decision(ProjectCapabilityAction.REJECT, false, projectKey, request, normalizedCapabilities, reason = reason)
        }

        if (missing.isEmpty()) {
            return decision(ProjectCapabilityAction.ALLOW, true, projectKey, request, normalizedCapabilities, reason = "All requested capabilities are declared.")
        }

        val remembered = rememberedDecision(projectKey, missing, authorizationStore)
        if (remembered != null) {
            return decision(
                action = remembered,
                allowed = remembered == ProjectCapabilityAction.ALLOW,
                projectKey = projectKey,
                request = request,
                capabilities = normalizedCapabilities,
                remembered = true,
                reason = "Remembered project decision for missing capabilities: ${missing.joinToString()}",
            )
        }

        val policyAction = parseAction(
            projectConfig.riskPolicy.actionFor(request.riskLevel, undeclared = true),
            ProjectCapabilityAction.PROMPT,
        )
        if (policyAction != ProjectCapabilityAction.PROMPT) {
            return decision(
                action = policyAction,
                allowed = policyAction == ProjectCapabilityAction.ALLOW,
                projectKey = projectKey,
                request = request,
                capabilities = normalizedCapabilities,
                reason = "Risk policy ${policyAction.wireName} for undeclared capabilities: ${missing.joinToString()}",
            )
        }

        val promptResult = prompter?.prompt(projectConfig, request, missing)
        if (promptResult == null) {
            return decision(
                action = ProjectCapabilityAction.REJECT,
                allowed = false,
                projectKey = projectKey,
                request = request,
                capabilities = normalizedCapabilities,
                reason = "User authorization required for undeclared capabilities: ${missing.joinToString()}",
            )
        }
        if (promptResult.remember && projectConfig.riskPolicy.rememberAllowed) {
            missing.forEach { authorizationStore?.remember(projectKey, it, promptResult.action) }
        }
        return decision(
            action = promptResult.action,
            allowed = promptResult.action == ProjectCapabilityAction.ALLOW,
            projectKey = projectKey,
            request = request,
            capabilities = normalizedCapabilities,
            remembered = promptResult.remember,
            reason = "User authorization ${promptResult.action.wireName} for undeclared capabilities: ${missing.joinToString()}",
        )
    }

    @JvmStatic
    fun guard(
        scriptRuntime: ScriptRuntime,
        api: String,
        capabilities: List<String>,
        riskLevel: String = "high",
        target: String = "",
        accessType: String = "",
    ): ProjectCapabilityDecision {
        val request = ProjectCapabilityGuardRequest(api, capabilities, riskLevel, target, accessType)
        val decision = evaluate(projectConfigForRuntime(scriptRuntime), request, authorizationStoreFor(scriptRuntime))
        ProjectCapabilityAuditLog.record(decision)
        if (!decision.allowed) {
            throw SecurityException("${decision.api} blocked by project capability policy: ${decision.reason}")
        }
        return decision
    }

    @JvmStatic
    fun audit(
        scriptRuntime: ScriptRuntime,
        api: String,
        capabilities: List<String>,
        riskLevel: String = "high",
        target: String = "",
        message: String = "",
    ): ProjectCapabilityAuditEntry {
        val config = projectConfigForRuntime(scriptRuntime)
        val decision = ProjectCapabilityDecision(
            action = ProjectCapabilityAction.ALLOW,
            allowed = true,
            projectKey = projectKey(config),
            api = api,
            capabilities = normalizeCapabilities(capabilities),
            riskLevel = riskLevel,
            target = target,
            reason = message,
        )
        return ProjectCapabilityAuditLog.record(decision, message)
    }

    @JvmStatic
    fun remember(scriptRuntime: ScriptRuntime, capabilities: Collection<String>, action: ProjectCapabilityAction) {
        val config = projectConfigForRuntime(scriptRuntime)
        val projectKey = projectKey(config)
        val store = authorizationStoreFor(scriptRuntime)
        normalizeCapabilities(capabilities).forEach { store.remember(projectKey, it, action) }
    }

    @JvmStatic
    fun projectConfigForRuntime(scriptRuntime: ScriptRuntime): ProjectConfig? {
        val candidates = buildList {
            runCatching { scriptRuntime.engines.myEngine().cwd() }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { add(File(it)) }
            runCatching { scriptRuntime.engines.myEngine().source.fullPath }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { add(File(it).absoluteFile.parentFile ?: File(it).absoluteFile) }
        }
        return candidates
            .asSequence()
            .flatMap { candidate -> generateSequence(candidate.absoluteFile) { it.parentFile } }
            .firstOrNull { ProjectConfig.isProject(it) }
            ?.let { projectDir ->
                ProjectConfig.fromProjectDir(projectDir.path)?.setSourcePath(projectDir.path)
            }
    }

    @JvmStatic
    fun parseAction(value: String, fallback: ProjectCapabilityAction): ProjectCapabilityAction {
        return when (value.trim().lowercase(Locale.ROOT)) {
            "allow", "allowed", "permit", "permissive" -> ProjectCapabilityAction.ALLOW
            "reject", "deny", "denied", "block", "blocked" -> ProjectCapabilityAction.REJECT
            "prompt", "ask", "confirm" -> ProjectCapabilityAction.PROMPT
            else -> fallback
        }
    }

    private fun authorizationStoreFor(scriptRuntime: ScriptRuntime): ProjectCapabilityAuthorizationStore {
        return SharedPreferencesProjectCapabilityAuthorizationStore(scriptRuntime.uiHandler.applicationContext)
    }

    private fun rememberedDecision(
        projectKey: String,
        missingCapabilities: List<String>,
        authorizationStore: ProjectCapabilityAuthorizationStore?,
    ): ProjectCapabilityAction? {
        if (authorizationStore == null || missingCapabilities.isEmpty()) return null
        val remembered = missingCapabilities.mapNotNull { authorizationStore.read(projectKey, it) }
        if (remembered.isEmpty()) return null
        if (remembered.any { it == ProjectCapabilityAction.REJECT }) return ProjectCapabilityAction.REJECT
        return if (remembered.size == missingCapabilities.size && remembered.all { it == ProjectCapabilityAction.ALLOW }) {
            ProjectCapabilityAction.ALLOW
        } else {
            null
        }
    }

    private fun privilegedPolicyViolation(projectConfig: ProjectConfig, capabilities: List<String>): String? {
        val policy = projectConfig.privilegedPolicy
        return when {
            CapabilityRegistry.ROOT in capabilities && policy.root == false ->
                "Root capability is not allowed by privilegedPolicy."
            CapabilityRegistry.SHIZUKU in capabilities && policy.shizuku == false ->
                "Shizuku capability is not allowed by privilegedPolicy."
            CapabilityRegistry.SHELL in capabilities && policy.shell == false ->
                "Shell capability is not allowed by privilegedPolicy."
            else -> null
        }
    }

    private fun filePolicyViolation(projectConfig: ProjectConfig, request: ProjectCapabilityGuardRequest): String? {
        if (CapabilityRegistry.STORAGE !in normalizeCapabilities(request.capabilities)) return null
        if (request.target.isBlank() || request.accessType.isBlank()) return null
        val policy = projectConfig.filePolicy
        val base = projectConfig.sourcePath?.takeIf { it.isNotBlank() }?.let(::File)
        val target = resolvePath(base, request.target)
        val readable = policy.allowedPaths
        val writable = policy.writablePaths
        val deletable = policy.deletablePaths
        if (readable.isNotEmpty() && !readable.any { containsPath(base, it, target) }) {
            return "Path is outside filePolicy.allowedPaths: ${request.target}"
        }
        if (request.accessType in setOf("write", "overwrite") && writable.isNotEmpty() && !writable.any { containsPath(base, it, target) }) {
            return "Path is outside filePolicy.writablePaths: ${request.target}"
        }
        if (request.accessType == "delete" && deletable.isNotEmpty() && !deletable.any { containsPath(base, it, target) }) {
            return "Path is outside filePolicy.deletablePaths: ${request.target}"
        }
        return null
    }

    private fun containsPath(base: File?, allowedPath: String, target: File): Boolean {
        val allowed = resolvePath(base, allowedPath)
        return runCatching {
            target.canonicalFile.toPath().normalize().startsWith(allowed.canonicalFile.toPath().normalize())
        }.getOrDefault(false)
    }

    private fun resolvePath(base: File?, path: String): File {
        val file = File(path)
        return if (file.isAbsolute || base == null) file else File(base, path)
    }

    private fun decision(
        action: ProjectCapabilityAction,
        allowed: Boolean,
        projectKey: String,
        request: ProjectCapabilityGuardRequest,
        capabilities: List<String>,
        remembered: Boolean = false,
        reason: String,
    ) = ProjectCapabilityDecision(
        action = action,
        allowed = allowed,
        projectKey = projectKey,
        api = request.api,
        capabilities = capabilities,
        riskLevel = request.riskLevel,
        target = request.target,
        remembered = remembered,
        reason = reason,
    )

    private fun normalizeCapabilities(capabilities: Collection<String>): List<String> {
        return capabilities
            .mapNotNull { it.trim().lowercase(Locale.ROOT).takeIf(String::isNotBlank) }
            .distinct()
    }

    private fun projectKey(projectConfig: ProjectConfig?): String {
        if (projectConfig == null) return LEGACY_PROJECT_KEY
        return projectConfig.sourcePath
            ?.takeIf { it.isNotBlank() }
            ?: projectConfig.packageName
            ?.takeIf { it.isNotBlank() }
            ?: projectConfig.name
            ?.takeIf { it.isNotBlank() }
            ?: LEGACY_PROJECT_KEY
    }
}
