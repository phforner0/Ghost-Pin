package com.ghostpin.app.location

import com.ghostpin.core.model.MockLocation

interface LocationInjector {
    fun registerProvider()

    fun inject(mockLocation: MockLocation)

    fun unregisterProvider()
}
