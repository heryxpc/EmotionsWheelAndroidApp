package com.emotionwheel.app.ui.wheel

import androidx.lifecycle.ViewModel
import com.emotionwheel.app.data.catalog.Emotion
import com.emotionwheel.app.data.catalog.EmotionCatalog
import com.emotionwheel.app.data.catalog.EmotionFamily
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class WheelViewModel(val catalog: EmotionCatalog) : ViewModel() {

    data class UiState(
        val selected: Emotion? = null,
        val query: String = "",
        val results: List<Emotion> = emptyList(),
        /** Family whose full list is open in the bottom sheet, if any. */
        val expandedFamily: EmotionFamily? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun select(emotion: Emotion) {
        _state.update { it.copy(selected = emotion, expandedFamily = null) }
    }

    fun clearSelection() {
        _state.update { it.copy(selected = null) }
    }

    fun search(query: String) {
        _state.update { it.copy(query = query, results = catalog.search(query)) }
    }

    fun clearSearch() {
        _state.update { it.copy(query = "", results = emptyList()) }
    }

    fun expandFamily(family: EmotionFamily?) {
        _state.update { it.copy(expandedFamily = family) }
    }
}
