package com.ghostpin.app.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

internal fun shouldRearmSchedulesForAction(action: String?): Boolean {
    return action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED
}

@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduleManager: ScheduleManager

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        if (!shouldRearmSchedulesForAction(intent.action)) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                scheduleManager.rearmPersistedSchedules()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
