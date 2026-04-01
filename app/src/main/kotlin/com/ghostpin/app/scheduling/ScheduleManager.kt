package com.ghostpin.app.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.ghostpin.app.BuildConfig
import com.ghostpin.app.data.SimulationConfig
import com.ghostpin.core.security.LogSanitizer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

internal fun shouldUseExactAlarms(
    apiLevel: Int,
    canScheduleExactAlarms: Boolean
): Boolean = apiLevel < Build.VERSION_CODES.S || canScheduleExactAlarms

@Singleton
class ScheduleManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val scheduleDao: ScheduleDao,
    ) {
        companion object {
            private const val TAG = "ScheduleManager"
        }

        private val alarmManager: AlarmManager by lazy {
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        }

        data class CreateScheduleRequest(
            val startAtMs: Long,
            val stopAtMs: Long?,
            val config: SimulationConfig,
        )

        sealed class CreateScheduleResult {
            data class Success(
                val schedule: ScheduleEntity,
                val exactAlarmGranted: Boolean,
            ) : CreateScheduleResult()

            data class Conflict(
                val message: String
            ) : CreateScheduleResult()
        }

        data class ExactAlarmUiState(
            val exactAlarmGranted: Boolean,
            val canOpenSettings: Boolean,
            val message: String,
        )

        suspend fun createSchedule(request: CreateScheduleRequest): CreateScheduleResult {
            val conflict = scheduleDao.findEnabledByStartAt(request.startAtMs).isNotEmpty()
            if (conflict) {
                return CreateScheduleResult.Conflict(
                    "Já existe um agendamento ativo no mesmo instante de início."
                )
            }

            val schedule =
                ScheduleEntity(
                    id = UUID.randomUUID().toString(),
                    startAtMs = request.startAtMs,
                    stopAtMs = request.stopAtMs,
                    profileName = request.config.profileName,
                    profileLookupKey = request.config.profileLookupKey,
                    startLat = request.config.startLat,
                    startLng = request.config.startLng,
                    endLat = request.config.endLat,
                    endLng = request.config.endLng,
                    routeId = request.config.routeId,
                    appMode = request.config.appMode.name,
                    waypointsJson = request.config.serializedWaypoints(),
                    waypointPauseSec = request.config.waypointPauseSec,
                    speedRatio = request.config.speedRatio,
                    frequencyHz = request.config.frequencyHz,
                    repeatPolicy = request.config.repeatPolicy.name,
                    repeatCount = request.config.repeatCount,
                    enabled = true,
                    createdAtMs = System.currentTimeMillis(),
                )
            scheduleDao.upsert(schedule)
            val exactAlarmGranted = armSchedule(schedule)
            return CreateScheduleResult.Success(schedule, exactAlarmGranted)
        }

        fun exactAlarmUiState(): ExactAlarmUiState {
            val granted = canUseExactAlarms()
            val canOpenSettings = BuildConfig.EXACT_ALARM_SETTINGS_ENABLED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !granted
            val message =
                if (granted) {
                    "Agendamentos usarão alarmes exatos quando o sistema permitir."
                } else {
                    "O Android pode atrasar a execução deste agendamento porque alarmes exatos não estão disponíveis."
                }

            return ExactAlarmUiState(
                exactAlarmGranted = granted,
                canOpenSettings = canOpenSettings,
                message = message,
            )
        }

        suspend fun cancelSchedule(scheduleId: String) {
            val existing = scheduleDao.getById(scheduleId) ?: return
            cancelAlarms(existing.id)
            scheduleDao.disable(scheduleId)
        }

        suspend fun rearmPersistedSchedules(nowMs: Long = System.currentTimeMillis()) {
            scheduleDao.getAllEnabled().forEach { schedule ->
                val startWasMissed = schedule.startAtMs in 1..nowMs
                if ((schedule.stopAtMs != null && schedule.stopAtMs <= nowMs) || schedule.startAtMs <= 0L || startWasMissed) {
                    if (startWasMissed) {
                        Log.w(TAG, LogSanitizer.sanitizeString("Schedule ${schedule.id} missed its start window and will be disabled."))
                    }
                    scheduleDao.disable(schedule.id)
                    cancelAlarms(schedule.id)
                } else {
                    armSchedule(schedule)
                }
            }
        }

        private fun armSchedule(schedule: ScheduleEntity): Boolean {
            val exactAlarmGranted = canUseExactAlarms()
            if (!schedule.enabled) return exactAlarmGranted

            if (schedule.startAtMs > System.currentTimeMillis()) {
                scheduleAlarm(
                    triggerAtMs = schedule.startAtMs,
                    pendingIntent = buildPendingIntent(schedule.id, ScheduleReceiver.EVENT_START),
                    exactAlarmGranted = exactAlarmGranted,
                )
            }

            val stopAt = schedule.stopAtMs
            if (stopAt != null && stopAt > System.currentTimeMillis()) {
                scheduleAlarm(
                    triggerAtMs = stopAt,
                    pendingIntent = buildPendingIntent(schedule.id, ScheduleReceiver.EVENT_STOP),
                    exactAlarmGranted = exactAlarmGranted,
                )
            }

            return exactAlarmGranted
        }

        private fun scheduleAlarm(
            triggerAtMs: Long,
            pendingIntent: PendingIntent,
            exactAlarmGranted: Boolean,
        ) {
            if (exactAlarmGranted) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            } else {
                Log.w(
                    TAG,
                    LogSanitizer.sanitizeString(
                        "Exact alarms unavailable; falling back to inexact scheduling."
                    )
                )
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            }
        }

        private fun canUseExactAlarms(): Boolean =
            shouldUseExactAlarms(
                apiLevel = Build.VERSION.SDK_INT,
                canScheduleExactAlarms =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        alarmManager.canScheduleExactAlarms()
                    } else {
                        true
                    }
            )

        private fun cancelAlarms(scheduleId: String) {
            alarmManager.cancel(buildPendingIntent(scheduleId, ScheduleReceiver.EVENT_START))
            alarmManager.cancel(buildPendingIntent(scheduleId, ScheduleReceiver.EVENT_STOP))
        }

        private fun buildPendingIntent(
            scheduleId: String,
            event: String
        ): PendingIntent {
            val requestCode = stableRequestCode(scheduleId, event)
            val intent =
                Intent(context, ScheduleReceiver::class.java).apply {
                    action = ScheduleReceiver.ACTION_SCHEDULE_EVENT
                    putExtra(ScheduleReceiver.EXTRA_SCHEDULE_ID, scheduleId)
                    putExtra(ScheduleReceiver.EXTRA_EVENT, event)
                }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun stableRequestCode(
            scheduleId: String,
            event: String
        ): Int = "$event:$scheduleId".hashCode()
    }
