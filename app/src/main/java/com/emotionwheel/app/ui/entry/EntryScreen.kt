package com.emotionwheel.app.ui.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emotionwheel.app.R
import com.emotionwheel.app.ui.components.EmotionChip
import com.emotionwheel.app.ui.containerViewModel
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val DisplayDate: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/**
 * The form: a day, the emotions, and what happened. Supports several emotions at once
 * because the hand-kept journal already did — rows like "impaciencia / hostilidad /
 * desilusión" are common when a single event pulls in more than one direction.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(
    entryId: String?,
    initialEmotionId: String?,
    onDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: EntryViewModel = containerViewModel(key = entryId ?: "new") { container ->
        EntryViewModel(container.repository, container.catalog, entryId, initialEmotionId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    var emotionQuery by remember { mutableStateOf("") }

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    // A newly picked emotion arrives while this screen is already on top.
    LaunchedEffect(initialEmotionId) {
        initialEmotionId?.let(viewModel::addEmotion)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isEditing) R.string.entry_title_edit
                            else R.string.entry_title_new
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // The app draws edge to edge, so the keyboard overlays the window
                // instead of resizing it. Without this the situation field ends up
                // hidden behind the keys while you type into it.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SectionTitle(stringResource(R.string.entry_date_label))
            AssistChip(
                onClick = { showDatePicker = true },
                label = { Text(state.date.format(DisplayDate)) },
                leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
            )

            SectionTitle(stringResource(R.string.entry_emotions_label))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.emotions.forEach { emotion ->
                    EmotionChip(
                        emotion = emotion,
                        onRemove = { viewModel.removeEmotion(emotion.id) },
                    )
                }
            }
            if (!state.hasEmotion) {
                Text(
                    text = stringResource(R.string.entry_emotions_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            OutlinedTextField(
                value = emotionQuery,
                onValueChange = { emotionQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.entry_emotions_add)) },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
            )
            if (emotionQuery.isNotBlank()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    viewModel.search(emotionQuery).take(8).forEach { emotion ->
                        EmotionChip(
                            emotion = emotion,
                            onClick = {
                                viewModel.addEmotion(emotion.id)
                                emotionQuery = ""
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.customEmotion,
                onValueChange = viewModel::setCustomEmotion,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.entry_custom_label)) },
            )

            SectionTitle(stringResource(R.string.entry_situation_label))
            OutlinedTextField(
                value = state.situation,
                onValueChange = viewModel::setSituation,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp),
                placeholder = { Text(stringResource(R.string.entry_situation_hint)) },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = viewModel::save,
                    enabled = state.canSave,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.action_save))
                }
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            viewModel.setDate(
                                Instant.ofEpochMilli(millis)
                                    .atZone(ZoneOffset.UTC)
                                    .toLocalDate()
                            )
                        }
                        showDatePicker = false
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
