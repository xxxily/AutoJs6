package org.autojs.autojs.runtime.api

import android.content.Context
import org.autojs.autojs.capability.CapabilityCheck
import org.autojs.autojs.capability.CapabilityDefinition
import org.autojs.autojs.capability.CapabilityRegistry

class Capabilities(private val context: Context) {

    fun check(idsOrApis: Collection<String> = emptyList()): List<CapabilityCheck> {
        return CapabilityRegistry.check(context, idsOrApis)
    }

    fun ensure(
        idsOrApis: Collection<String>,
        request: Boolean = false,
        timeoutMillis: Long = 0L,
    ): List<CapabilityCheck> {
        return CapabilityRegistry.ensure(context, idsOrApis, request, timeoutMillis)
    }

    fun explain(idOrApi: String): CapabilityDefinition? {
        return CapabilityRegistry.explain(idOrApi)
    }

    fun scan(script: String): List<CapabilityDefinition> {
        return CapabilityRegistry.inferCapabilitiesFromScript(script)
    }
}
