package com.ghostpin.app.location

import com.ghostpin.core.model.MockLocation
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeLocationInjector @Inject constructor() : LocationInjector {
    val registerCalls = AtomicInteger(0)
    val unregisterCalls = AtomicInteger(0)
    val injectedLocations = CopyOnWriteArrayList<MockLocation>()

    override fun registerProvider() {
        registerCalls.incrementAndGet()
    }

    override fun inject(mockLocation: MockLocation) {
        injectedLocations += mockLocation
    }

    override fun unregisterProvider() {
        unregisterCalls.incrementAndGet()
    }

    fun reset() {
        registerCalls.set(0)
        unregisterCalls.set(0)
        injectedLocations.clear()
    }
}
