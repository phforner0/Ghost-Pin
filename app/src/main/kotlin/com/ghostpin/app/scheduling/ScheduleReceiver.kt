package com.ghostpin.app.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ghostpin.app.data.SimulationRepository
import com.ghostpin.app.service.SimulationService
import com.ghostpin.app.service.SimulationState
import com.ghostpin.core.security.LogSanitizer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

internal fun shouldStartScheduledSimulation(state: SimulationState): Boolean =
    state !is SimulationState.Running &&
        state !is SimulationState.Paused &&
        state !is SimulationState.FetchingRoute

internal fun shouldStopScheduledSimulation(state: SimulationState): Boolean =
    state is SimulationState.Running ||
        state is SimulationState.Paused ||
        state is SimulationState.FetchingRoute

internal enum class ScheduledStartAction {
    START_NOW,
    CANCEL_SCHEDULE,
}

internal enum class ScheduledStopAction {
    STOP_NOW,
    CANCEL_SCHEDULE,
}

internal fun scheduledStartAction(state: SimulationState): ScheduledStartAction =
    if (shouldStartScheduledSimulation(state)) {
        ScheduledStartAction.START_NOW
    } else {
        ScheduledStartAction.CANCEL_SCHEDULE
    }

internal fun scheduledStopAction(state: SimulationState): ScheduledStopAction =
    if (shouldStopScheduledSimulation(state)) {
        ScheduledStopAction.STOP_NOW
    } else {
        ScheduledStopAction.CANCEL_SCHEDULE
    }

@AndroidEntryPoint
class ScheduleReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduleDao: ScheduleDao

    @Inject lateinit var scheduleManager: ScheduleManager

    @Inject lateinit var simulationRepository: SimulationRepository

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_SCHEDULE_EVENT -> handleScheduleEvent(context, intent)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleScheduleEvent(
        context: Context,
        intent: Intent
    ) {
        val scheduleId = intent.getStringExtra(EXTRA_SCHEDULE_ID) ?: return
        val event = intent.getStringExtra(EXTRA_EVENT) ?: return
        val schedule = scheduleDao.getById(scheduleId) ?: return
        if (!schedule.enabled) return

        when (event) {
            EVENT_START -> {
                when (scheduledStartAction(simulationRepository.state.value)) {
                    ScheduledStartAction.CANCEL_SCHEDULE -> {
                        Log.i(
                            TAG,
                            LogSanitizer.sanitizeString(
                                "START ignored; schedule cancelled because a session is already active."
                            )
                        )
                        scheduleManager.cancelSchedule(schedule.id)
                        return
                    }

                    ScheduledStartAction.START_NOW -> Unit
                }

                val serviceIntent = SimulationService.createStartIntent(context, schedule.toSimulationConfig())
                context.startForegroundService(serviceIntent)
                if (schedule.stopAtMs == null) {
                    scheduleManager.cancelSchedule(schedule.id)
                }
            }

            EVENT_STOP -> {
                when (scheduledStopAction(simulationRepository.state.value)) {
                    ScheduledStopAction.STOP_NOW -> {
                        context.startService(
                            Intent(context, SimulationService::class.java).apply {
                                action = SimulationService.ACTION_STOP
                            }
                        )
                    }

                    ScheduledStopAction.CANCEL_SCHEDULE -> {
                        Log.i(
                            TAG,
                            LogSanitizer.sanitizeString(
                                "STOP ignored; no active session, cancelling expired schedule."
                            )
                        )
                    }
                }
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
