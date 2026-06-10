package org.autojs.autojs.compat

import org.autojs.autojs.capability.CapabilityRegistry
import org.autojs.autojs.timing.TimedTaskSchedulingPolicy
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCompatibilitySmokeTest {

    @Test
    fun instrumentationCanLoadCoreCompatibilitySurfaces() {
        assertTrue(CapabilityRegistry.allDefinitions().size >= 20)
        assertTrue(TimedTaskSchedulingPolicy.MAX_ALARM_SLOTS in 1..500)
        assertTrue(TimedTaskSchedulingPolicy.schedulePeriodicCheckTimeMillis > 0L)
    }
}
