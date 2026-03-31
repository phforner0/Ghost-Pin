package com.ghostpin.app.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.ghostpin.app.data.SimulationConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val scheduleDao: ScheduleDao,
    ) {
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
                val schedule: ScheduleEntity
            ) : CreateScheduleResult()

            data class Conflict(
                val message: String
            ) : CreateScheduleResult()
        }

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
            armSchedule(schedule)
            return CreateScheduleResult.Success(schedule)
        }

        suspend fun cancelSchedule(scheduleId: String) {
            val existing = scheduleDao.getById(scheduleId) ?: return
            cancelAlarms(existing.id)
            scheduleDao.disable(scheduleId)
        }

        suspend fun rearmPersistedSchedules(nowMs: Long = System.currentTimeMillis()) {
            scheduleDao.getAllEnabled().forEach { schedule ->
                if ((schedule.stopAtMs != null && schedule.stopAtMs <= nowMs) || schedule.startAtMs <= 0L) {
                    scheduleDao.disable(schedule.id)
                    cancelAlarms(schedule.id)
                } else {
                    armSchedule(schedule)
                }
            }
        }

        private fun armSchedule(schedule: ScheduleEntity) {
            if (!schedule.enabled) return

            if (schedule.startAtMs > System.currentTimeMillis()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    schedule.startAtMs,
                    buildPendingIntent(schedule.id, ScheduleReceiver.EVENT_START),
                )
            }

            val stopAt = schedule.stopAtMs
            if (stopAt != null && stopAt > System.currentTimeMillis()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    stopAt,
                    buildPendingIntent(schedule.id, ScheduleReceiver.EVENT_STOP),
                )
            }
        }

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
