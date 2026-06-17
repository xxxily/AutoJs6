package org.autojs.autojs.runtime

class ResourceScope(private val ownerId: String) {

    private val results = mutableListOf<ResourceReleaseResult>()

    fun release(name: String, action: () -> Unit) {
        val startedAt = System.currentTimeMillis()
        try {
            action()
            results += ResourceReleaseResult(
                name = name,
                success = true,
                elapsedMillis = System.currentTimeMillis() - startedAt,
            )
        } catch (throwable: Throwable) {
            results += ResourceReleaseResult(
                name = name,
                success = false,
                elapsedMillis = System.currentTimeMillis() - startedAt,
                errorClass = throwable.javaClass.name,
                errorMessage = throwable.message.orEmpty(),
            )
        }
    }

    fun snapshot(): ResourceReleaseSummary {
        val completed = results.toList()
        return ResourceReleaseSummary(
            ownerId = ownerId,
            total = completed.size,
            succeeded = completed.count { it.success },
            failed = completed.count { !it.success },
            resources = completed,
        )
    }

    fun clear() {
        results.clear()
    }
}

data class ResourceReleaseSummary(
    val ownerId: String,
    val total: Int,
    val succeeded: Int,
    val failed: Int,
    val resources: List<ResourceReleaseResult>,
) {
    fun toAuditString(): String {
        val failedResources = resources
            .filterNot { it.success }
            .joinToString(", ") { "${it.name}:${it.errorClass}" }
        return buildString {
            append("owner=")
            append(ownerId)
            append(", total=")
            append(total)
            append(", succeeded=")
            append(succeeded)
            append(", failed=")
            append(failed)
            if (failedResources.isNotBlank()) {
                append(", failedResources=")
                append(failedResources)
            }
        }
    }
}

data class ResourceReleaseResult(
    val name: String,
    val success: Boolean,
    val elapsedMillis: Long,
    val errorClass: String = "",
    val errorMessage: String = "",
)
