package com.credo.soundgroove.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.credo.soundgroove.ui.theme.TextTertiary

/**
 * En-tête de section d'écoute partagé (Accueil, Recherche, Profil…).
 * Typo [labelMedium] + [TextTertiary] ; action optionnelle en accent.
 */
@Composable
fun ListeningSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onAction: (() -> Unit)? = null
) {
    val hasAction = !actionLabel.isNullOrBlank() && onAction != null
    val hasSubtitle = !subtitle.isNullOrBlank()

    if (!hasAction && !hasSubtitle) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = TextTertiary,
            modifier = modifier.padding(bottom = 2.dp)
        )
        return
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = TextTertiary,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            if (hasSubtitle) {
                Text(
                    text = subtitle.orEmpty(),
                    color = TextTertiary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        if (hasAction) {
            Text(
                text = actionLabel.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = accentColor,
                modifier = Modifier.clickable(onClick = onAction!!)
            )
        }
    }
}
