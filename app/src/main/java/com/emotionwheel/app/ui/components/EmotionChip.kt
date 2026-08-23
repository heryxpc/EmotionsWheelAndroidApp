package com.emotionwheel.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.emotionwheel.app.R
import com.emotionwheel.app.data.catalog.Emotion
import com.emotionwheel.app.ui.theme.UnmappedEmotionColor
import com.emotionwheel.app.ui.theme.color
import com.emotionwheel.app.ui.theme.contentColorFor

/**
 * An emotion as a colored pill. Emotions typed by hand — the ones the wheel does not
 * name — get the neutral tone so they read as "outside the wheel" at a glance.
 */
@Composable
fun EmotionChip(
    label: String,
    modifier: Modifier = Modifier,
    background: Color = UnmappedEmotionColor,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    val content = contentColorFor(background)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
        )
        if (onRemove != null) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.entry_emotions_remove, label),
                tint = content,
                modifier = Modifier
                    .size(16.dp)
                    .clickable(onClick = onRemove),
            )
        }
    }
}

@Composable
fun EmotionChip(
    emotion: Emotion,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) = EmotionChip(
    label = emotion.label,
    modifier = modifier,
    background = emotion.family.color,
    onClick = onClick,
    onRemove = onRemove,
)
