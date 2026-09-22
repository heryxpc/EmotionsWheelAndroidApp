package com.emotionwheel.app.ui.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emotionwheel.app.data.JournalRepository
import com.emotionwheel.app.data.catalog.Emotion
import com.emotionwheel.app.data.catalog.EmotionCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Backs both creating and editing an entry: [entryId] decides which.
 * [initialEmotionId] is what the wheel handed over, if the user came from there.
 */
class EntryViewModel(
    private val repository: JournalRepository,
    private val catalog: EmotionCatalog,
    private val entryId: String?,
    initialEmotionId: String?,
) : ViewModel() {

    data class UiState(
        val date: LocalDate = LocalDate.now(),
        val emotions: List<Emotion> = emptyList(),
        val customEmotion: String = "",
        val situation: String = "",
        val isEditing: Boolean = false,
        val loading: Boolean = true,
        val saved: Boolean = false,
    ) {
        val hasEmotion: Boolean get() = emotions.isNotEmpty() || customEmotion.isNotBlank()
        val canSave: Boolean get() = hasEmotion && situation.isNotBlank()
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = entryId?.let { repository.findById(it) }
            _state.update {
                if (existing != null) {
                    it.copy(
                        date = existing.date,
                        emotions = existing.emotions,
                        customEmotion = existing.customEmotion.orEmpty(),
                        situation = existing.situation,
                        isEditing = true,
                        loading = false,
                    )
                } else {
                    it.copy(
                        emotions = listOfNotNull(initialEmotionId?.let(catalog::get)),
                        loading = false,
                    )
                }
            }
        }
    }

    fun setDate(date: LocalDate) = _state.update { it.copy(date = date) }

    fun setSituation(text: String) = _state.update { it.copy(situation = text) }

    fun setCustomEmotion(text: String) = _state.update { it.copy(customEmotion = text) }

    fun addEmotion(id: String) {
        val emotion = catalog[id] ?: return
        _state.update { current ->
            if (current.emotions.any { it.id == emotion.id }) current
            else current.copy(emotions = current.emotions + emotion)
        }
    }

    fun removeEmotion(id: String) = _state.update { current ->
        current.copy(emotions = current.emotions.filterNot { it.id == id })
    }

    fun search(query: String): List<Emotion> = catalog.search(query)

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        viewModelScope.launch {
            repository.save(
                id = entryId,
                date = current.date,
                emotionIds = current.emotions.map { it.id },
                customEmotion = current.customEmotion,
                situation = current.situation,
            )
            _state.update { it.copy(saved = true) }
        }
    }
}
