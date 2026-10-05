package com.kairo.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KairoApp : Application() {
    lateinit var container: AppContainer
        private set

    /** Outlives any screen, so first-launch seeding isn't cancelled by a quick rotation or back press. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch { container.roleRepository.seedDefaultsIfEmpty() }
    }
}
