package com.emotionwheel.app.ui.wheel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emotionwheel.app.R
import com.emotionwheel.app.data.catalog.Emotion
import com.emotionwheel.app.ui.components.EmotionChip
import com.emotionwheel.app.ui.containerViewModel
import com.emotionwheel.app.ui.theme.color

/**
 * Home screen: the wheel plus whatever the user just picked.
 *
 * Three ways in, because ninety words on one circle is a lot to hunt through: tap the
 * wheel, type in the search box, or open a family's full list from its chip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WheelScreen(
    onLogEmotion: (Emotion) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: WheelViewModel = containerViewModel { WheelViewModel(it.catalog) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.wheel_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = stringResource(R.string.wheel_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::search,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = viewModel::clearSearch) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.action_close),
                            )
                        }
                    }
                },
                placeholder = { Text(stringResource(R.string.wheel_search_hint)) },
            )

            if (state.query.isNotEmpty()) {
                SearchResults(
                    query = state.query,
                    results = state.results,
                    onPick = {
                        viewModel.select(it)
                        viewModel.clearSearch()
                    },
                )
            }

            EmotionWheel(
                catalog = viewModel.catalog,
                selected = state.selected,
                onSelect = viewModel::select,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            )

            // Room for the selection card so it never covers the wheel's bottom edge.
            Box(Modifier.height(if (state.selected != null) 220.dp else 24.dp))
        }

        AnimatedVisibility(
            visible = state.selected != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            state.selected?.let { emotion ->
                SelectionCard(
                    emotion = emotion,
                    onLog = { onLogEmotion(emotion) },
                    onDismiss = viewModel::clearSelection,
                    onOpenFamily = { viewModel.expandFamily(emotion.family) },
                )
            }
        }
    }

    state.expandedFamily?.let { family ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.expandFamily(null) },
            sheetState = sheetState,
        ) {
            FamilySheet(
                title = stringResource(
                    R.string.wheel_family_sheet_title,
                    stringResource(family.labelRes),
                ),
                emotions = viewModel.catalog.family(family),
                selectedId = state.selected?.id,
                onPick = viewModel::select,
            )
        }
    }
}

@Composable
private fun SearchResults(
    query: String,
    results: List<Emotion>,
    onPick: (Emotion) -> Unit,
) {
    if (results.isEmpty()) {
        Text(
            text = stringResource(R.string.wheel_search_empty, query),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        )
        return
    }
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        results.take(12).forEach { emotion ->
            EmotionChip(emotion = emotion, onClick = { onPick(emotion) })
        }
    }
}

/** What the user just picked, with its definition and the way on to the journal. */
@Composable
private fun SelectionCard(
    emotion: Emotion,
    onLog: () -> Unit,
    onDismiss: () -> Unit,
    onOpenFamily: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 12.dp,
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = emotion.label.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                EmotionChip(
                    label = stringResource(emotion.family.labelRes),
                    background = emotion.family.color,
                    onClick = onOpenFamily,
                )
            }
            Text(
                text = emotion.definition,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onLog, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.wheel_action_log))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.wheel_action_dismiss))
                }
            }
        }
    }
}

/** All fifteen words of one family, for when the outer ring is too fiddly to tap. */
@Composable
private fun FamilySheet(
    title: String,
    emotions: List<Emotion>,
    selectedId: String?,
    onPick: (Emotion) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        item {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        items(emotions, key = { it.id }) { emotion ->
            Card(
                onClick = { onPick(emotion) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (emotion.id == selectedId) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        EmotionChip(emotion = emotion)
                    }
                    Text(
                        text = emotion.definition,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        item { HorizontalDivider(Modifier.padding(vertical = 12.dp)) }
    }
}
