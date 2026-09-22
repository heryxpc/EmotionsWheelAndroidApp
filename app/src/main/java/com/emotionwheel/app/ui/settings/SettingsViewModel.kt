package com.emotionwheel.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emotionwheel.app.data.SettingsStore
import com.emotionwheel.app.data.remote.CloudBackup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: SettingsStore,
    private val backup: CloudBackup,
) : ViewModel() {

    data class UiState(
        val backupAvailable: Boolean = false,
        val backupEnabled: Boolean = false,
        val pendingCount: Int = 0,
        val lastSyncAt: Long? = null,
    )

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val state: StateFlow<UiState> = combine(
        settings.cloudBackupEnabled,
        backup.pendingCount,
        backup.lastSyncAt,
    ) { enabled, pending, lastSync ->
        UiState(
            backupAvailable = backup.available,
            backupEnabled = enabled,
            pendingCount = pending,
            lastSyncAt = lastSync,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UiState(backupAvailable = backup.available),
    )

    fun setBackupEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setCloudBackupEnabled(enabled) }
    }

    fun syncNow(context: Context) {
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            backup.sync(context).onFailure { _error.value = it.message ?: it::class.simpleName }
            _syncing.value = false
        }
    }

    fun consumeError() {
        _error.value = null
    }
}
