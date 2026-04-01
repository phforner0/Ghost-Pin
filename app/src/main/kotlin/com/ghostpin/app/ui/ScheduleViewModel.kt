package com.ghostpin.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostpin.app.data.ProfileManager
import com.ghostpin.app.data.SimulationConfig
import com.ghostpin.app.scheduling.ScheduleDao
import com.ghostpin.app.scheduling.ScheduleEntity
import com.ghostpin.app.scheduling.ScheduleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScheduleViewModel
    @Inject
    constructor(
        private val scheduleDao: ScheduleDao,
        private val scheduleManager: ScheduleManager,
        profileManager: ProfileManager,
    ) : ViewModel() {
        val schedules: StateFlow<List<ScheduleEntity>> =
            scheduleDao
                .observeEnabledSchedules()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        data class ProfileOption(
            val label: String,
            val lookupKey: String,
        )

        val profileOptions: StateFlow<List<ProfileOption>> =
            profileManager
                .observeAllEntities()
                .map { entities ->
                    entities.map { ProfileOption(label = it.name, lookupKey = it.id) }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
        val events = _events.asSharedFlow()
        private val _exactAlarmUiState = MutableStateFlow(scheduleManager.exactAlarmUiState())
        val exactAlarmUiState = _exactAlarmUiState.asStateFlow()

        fun refreshExactAlarmUiState() {
            _exactAlarmUiState.value = scheduleManager.exactAlarmUiState()
        }

        fun createSchedule(
            startDelayMinutes: Int,
            durationMinutes: Int,
            config: SimulationConfig,
        ) {
            viewModelScope.launch {
                val now = System.currentTimeMillis()
                val startAtMs = now + (startDelayMinutes.coerceAtLeast(1) * 60_000L)
                val stopAtMs = startAtMs + (durationMinutes.coerceAtLeast(1) * 60_000L)

                when (
                    val result =
                        scheduleManager.createSchedule(
                            ScheduleManager.CreateScheduleRequest(
                                startAtMs = startAtMs,
                                stopAtMs = stopAtMs,
                                config = config,
                            )
                        )
                ) {
                    is ScheduleManager.CreateScheduleResult.Success -> {
                        val suffix =
                            if (result.exactAlarmGranted) {
                                ""
                            } else {
                                " (sem alarmes exatos; o sistema pode atrasar a execução)"
                            }
                        _events.emit("Agendamento criado: ${result.schedule.id.take(8)}$suffix")
                    }

                    is ScheduleManager.CreateScheduleResult.Conflict -> {
                        _events.emit(result.message)
                    }

                    is ScheduleManager.CreateScheduleResult.Unsupported -> {
                        _events.emit(result.message)
                    }
                }
            }
        }

        fun cancelSchedule(scheduleId: String) {
            viewModelScope.launch {
                scheduleManager.cancelSchedule(scheduleId)
                _events.emit("Agendamento cancelado")
            }
        }
    }
