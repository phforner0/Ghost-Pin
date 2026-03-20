package com.ghostpin.app.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ghostpin.app.automation.AutomationReceiver
import com.ghostpin.app.data.SimulationRepository
import com.ghostpin.app.service.SimulationService
import com.ghostpin.app.service.SimulationState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ScheduleReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduleDao: ScheduleDao
    @Inject lateinit var scheduleManager: ScheduleManager
    @Inject lateinit var simulationRepository: SimulationRepository

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_SCHEDULE_EVENT -> handleScheduleEvent(context, intent)
                    Intent.ACTION_BOOT_COMPLETED -> scheduleManager.rearmPersistedSchedules()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleScheduleEvent(context: Context, intent: Intent) {
        val scheduleId = intent.getStringExtra(EXTRA_SCHEDULE_ID) ?: return
        val event = intent.getStringExtra(EXTRA_EVENT) ?: return
        val schedule = scheduleDao.getById(scheduleId) ?: return
        if (!schedule.enabled) return

        when (event) {
            EVENT_START -> {
                val busy = simulationRepository.state.value is SimulationState.Running ||
                    simulationRepository.state.value is SimulationState.FetchingRoute
                if (busy) {
                    Log.i(TAG, "START ignorado; simulação já está em execução.")
                    return
                }

                val automationIntent = Intent().apply {
                    putExtra(AutomationReceiver.EXTRA_PROFILE_ID, schedule.profileName)
                    putExtra(AutomationReceiver.EXTRA_LAT, schedule.startLat)
                    putExtra(AutomationReceiver.EXTRA_LNG, schedule.startLng)
                    putExtra(AutomationReceiver.EXTRA_SPEED_RATIO, schedule.speedRatio)
                    putExtra(AutomationReceiver.EXTRA_FREQUENCY_HZ, schedule.frequencyHz)
                }
                val serviceIntent = AutomationReceiver.buildStartServiceIntent(context, automationIntent)
                context.startForegroundService(serviceIntent)
            }

            EVENT_STOP -> {
                val hasActiveSession = simulationRepository.state.value is SimulationState.Running ||
                    simulationRepository.state.value is SimulationState.Paused ||
                    simulationRepository.state.value is SimulationState.FetchingRoute
                if (!hasActiveSession) {
                    Log.i(TAG, "STOP ignorado; não há sessão ativa.")
                    return
                }

                context.startService(
                    Intent(context, SimulationService::class.java).apply {
                        action = SimulationService.ACTION_STOP
                    }
                )
                scheduleManager.cancelSchedule(schedule.id)
            }
        }
    }

    companion object {
        private const val TAG = "ScheduleReceiver"

        const val ACTION_SCHEDULE_EVENT = "com.ghostpin.app.scheduling.ACTION_SCHEDULE_EVENT"
        const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
        const val EXTRA_EVENT = "extra_event"

        const val EVENT_START = "START"
        const val EVENT_STOP = "STOP"
    }
}
