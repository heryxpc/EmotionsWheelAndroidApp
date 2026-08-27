package com.emotionwheel.app.ui.journal

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emotionwheel.app.R
import com.emotionwheel.app.data.JournalEntry
import com.emotionwheel.app.data.catalog.EmotionFamily
import com.emotionwheel.app.ui.components.EmotionChip
import com.emotionwheel.app.ui.containerViewModel
import com.emotionwheel.app.ui.theme.UnmappedEmotionColor
import com.emotionwheel.app.ui.theme.color
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HeaderDate: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE d 'de' MMMM, yyyy", Locale.forLanguageTag("es"))

/**
 * The journal, newest first and grouped by day. Filtering by family and searching the
 * text is what makes fifty-plus entries — and growing — readable at a glance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    onEditEntry: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: JournalViewModel = containerViewModel {
        JournalViewModel(it.repository, it.catalog)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { viewModel.export(context, it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.import(context, it) } }

    val deletedLabel = stringResource(R.string.journal_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    val exportFailedLabel = stringResource(R.string.journal_export_failed)
    val importFailedLabel = stringResource(R.string.journal_import_failed)
    val exportedTemplate = stringResource(R.string.journal_export_done)
    val importedTemplate = stringResource(R.string.journal_import_done)

    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        when (current) {
            is JournalViewModel.Message.Deleted -> {
                val result = snackbarHost.showSnackbar(
                    message = deletedLabel,
                    actionLabel = undoLabel,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.restore(current.entry)
            }
            is JournalViewModel.Message.Exported ->
                snackbarHost.showSnackbar(String.format(exportedTemplate, current.count))
            is JournalViewModel.Message.Imported ->
                snackbarHost.showSnackbar(
                    String.format(importedTemplate, current.imported, current.skipped)
                )
            JournalViewModel.Message.ExportFailed ->
                snackbarHost.showSnackbar(exportFailedLabel)
            JournalViewModel.Message.ImportFailed ->
                snackbarHost.showSnackbar(importFailedLabel)
        }
        viewModel.consumeMessage()
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.journal_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                        )
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.journal_export)) },
                            leadingIcon = {
                                Icon(Icons.Default.FileDownload, contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                exportLauncher.launch("bitacora-emociones.csv")
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.journal_import)) },
                            leadingIcon = {
                                Icon(Icons.Default.FileUpload, contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*"))
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = state.filters.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.journal_search_hint)) },
            )

            FamilyFilters(
                selected = state.filters.family,
                onSelect = viewModel::setFamily,
            )

            if (state.recentByFamily.isNotEmpty()) {
                RecentSummary(
                    counts = state.recentByFamily,
                    entryCount = state.recentCount,
                )
            }

            if (state.entries.isEmpty() && !state.loading) {
                EmptyState(filteredOut = state.totalCount > 0)
            } else {
                JournalList(
                    entries = state.entries,
                    onEdit = onEditEntry,
                    onDelete = viewModel::delete,
                )
            }
        }
    }
}

@Composable
private fun FamilyFilters(
    selected: EmotionFamily?,
    onSelect: (EmotionFamily?) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.journal_filter_all)) },
        )
        EmotionFamily.entries.forEach { family ->
            FilterChip(
                selected = selected == family,
                onClick = { onSelect(if (selected == family) null else family) },
                label = { Text(stringResource(family.labelRes)) },
                leadingIcon = {
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(50))
                            .background(family.color)
                    )
                },
            )
        }
    }
}

/**
 * A stacked bar of the last thirty days: which families have been showing up.
 *
 * [counts] adds up to more than [entryCount], because one entry can name emotions from
 * two families. The bar is drawn from the former; the number shown is the latter.
 */
@Composable
private fun RecentSummary(counts: Map<EmotionFamily, Int>, entryCount: Int) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.journal_summary_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.journal_summary_count, entryCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(50)),
        ) {
            EmotionFamily.entries.forEach { family ->
                val count = counts[family] ?: 0
                if (count > 0) {
                    Box(
                        Modifier
                            .weight(count.toFloat())
                            .fillMaxSize()
                            .background(family.color)
                    )
                }
            }
        }
    }
}

@Composable
/** [filteredOut] tells apart an empty journal from one hidden by the filters. */
private fun EmptyState(filteredOut: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(
                if (filteredOut) R.string.journal_empty_filtered_title
                else R.string.journal_empty_title
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(
                if (filteredOut) R.string.journal_empty_filtered
                else R.string.journal_empty_body
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun JournalList(
    entries: List<JournalEntry>,
    onEdit: (String) -> Unit,
    onDelete: (JournalEntry) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        var lastHeader: String? = null
        entries.forEach { entry ->
            val header = entry.date.format(HeaderDate)
            if (header != lastHeader) {
                lastHeader = header
                item(key = "header-$header") {
                    Text(
                        text = header.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                    )
                }
            }
            item(key = entry.id) {
                EntryCard(entry = entry, onEdit = { onEdit(entry.id) }, onDelete = { onDelete(entry) })
            }
        }
    }
}

@Composable
private fun EntryCard(
    entry: JournalEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val accent = entry.families.firstOrNull()?.color ?: UnmappedEmotionColor

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(Modifier.clickable { expanded = !expanded }) {
            Box(
                Modifier
                    .width(5.dp)
                    .fillMaxSize()
                    .background(accent)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    entry.emotions.forEach { EmotionChip(emotion = it) }
                    entry.customEmotion?.takeIf { it.isNotBlank() }?.let {
                        EmotionChip(label = it)
                    }
                }
                Text(
                    text = entry.situation,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Column {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.action_edit),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                    )
                }
            }
        }
    }
}
