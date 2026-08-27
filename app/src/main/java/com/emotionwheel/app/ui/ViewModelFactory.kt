package com.emotionwheel.app.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.emotionwheel.app.AppContainer
import com.emotionwheel.app.EmotionWheelApp

/** Reaches the app's dependency container from inside a ViewModel factory. */
val CreationExtras.container: AppContainer
    get() = (this[APPLICATION_KEY] as EmotionWheelApp).container

/**
 * Builds a ViewModel from the app container, keeping every screen's `viewModel()`
 * call to one line without pulling in a DI framework.
 */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM = viewModel(
    key = key,
    factory = viewModelFactory {
        initializer { create(container) }
    },
)
