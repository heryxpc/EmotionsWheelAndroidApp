package com.emotionwheel.app.ui.journal

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emotionwheel.app.data.JournalEntry
import com.emotionwheel.app.data.JournalRepository
import com.emotionwheel.app.data.catalog.EmotionCatalog
import com.emotionwheel.app.data.catalog.EmotionFamily
import com.emotionwheel.app.data.csv.JournalCsv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class JournalViewModel(
    private val repository: JournalRepository,
    private val catalog: EmotionCatalog,
) : ViewModel() {

    data class Filters(
        val query: String = "",
        val family: EmotionFamily? = null,
    )

    /** One-shot messages for the snackbar, cleared once shown. */
    sealed interface Message {
        data class Deleted(val entry: JournalEntry) : Message
        data class Exported(val count: Int) : Message
        data class Imported(val imported: Int, val skipped: Int) : Message
        data object ExportFailed : Message
        data object ImportFailed : Message
    }

    private val filters = MutableStateFlow(Filters())
    private val _message = MutableStateFlow<Message?>(null)
    val message: StateFlow<Message?> = _message.asStateFlow()

    val state: StateFlow<UiState> =
        combine(repository.observeEntries(), filters) { entries, active ->
            UiState(
                entries = entries.filter { it.matches(active) },
                totalCount = entries.size,
                filters = active,
                recentByFamily = summarize(entries),
                recentCount = entries.count { !it.date.isBefore(thirtyDaysAgo()) },
                loading = false,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    data class UiState(
        val entries: List<JournalEntry> = emptyList(),
        val totalCount: Int = 0,
        val filters: Filters = Filters(),
        /** Counts per family over the last 30 days, for the summary bar. */
        val recentByFamily: Map<EmotionFamily, Int> = emptyMap(),
        /** Entries in the last 30 days. Lower than the sum of [recentByFamily]: one
         *  entry can name emotions from two families and shows up in both. */
        val recentCount: Int = 0,
        val loading: Boolean = true,
    )

    private fun JournalEntry.matches(active: Filters): Boolean {
        val familyOk = active.family == null || active.family in families
        if (!familyOk) return false
        if (active.query.isBlank()) return true
        val needle = EmotionCatalog.normalize(active.query)
        return EmotionCatalog.normalize(situation).contains(needle) ||
            labels.any { EmotionCatalog.normalize(it).contains(needle) }
    }

    private fun thirtyDaysAgo(): LocalDate = LocalDate.now().minusDays(30)

    private fun summarize(entries: List<JournalEntry>): Map<EmotionFamily, Int> =
        entries
            .filter { !it.date.isBefore(thirtyDaysAgo()) }
            .flatMap { it.families }
            .groupingBy { it }
            .eachCount()

    fun setQuery(query: String) = filters.update { it.copy(query = query) }

    fun setFamily(family: EmotionFamily?) = filters.update { it.copy(family = family) }

    fun delete(entry: JournalEntry) {
        viewModelScope.launch {
            repository.delete(entry.id)
            _message.value = Message.Deleted(entry)
        }
    }

    fun restore(entry: JournalEntry) {
        viewModelScope.launch { repository.restore(entry) }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun export(context: Context, target: Uri) {
        viewModelScope.launch {
            val entries = state.value.entries
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(target)?.use { stream ->
                        JournalCsv.export(entries, stream)
                    } ?: error("Could not open $target for writing")
                }.isSuccess
            }
            _message.value =
                if (ok) Message.Exported(entries.size) else Message.ExportFailed
        }
    }

    fun import(context: Context, source: Uri) {
        viewModelScope.launch {
            val parsed = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(source)?.use { stream ->
                        JournalCsv.parse(stream, catalog)
                    } ?: error("Could not open $source for reading")
                }
            }
            parsed.fold(
                onSuccess = { rows ->
                    val inserted = repository.insertNew(rows)
                    _message.value = Message.Imported(inserted, rows.size - inserted)
                },
                onFailure = { _message.value = Message.ImportFailed },
            )
        }
    }
}
