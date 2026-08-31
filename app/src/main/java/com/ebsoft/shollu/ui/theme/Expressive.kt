@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.ebsoft.shollu.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Thin wrappers around still-experimental Expressive APIs (issue #15 confinement):
 * @OptIn stays here, never sprayed across screens.
 */

@Composable
fun SholluLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = LoadingIndicatorDefaults.indicatorColor
) {
    LoadingIndicator(modifier = modifier, color = color)
}

/**
 * Exclusive connected [ToggleButton] row (issue #17) — replaces deprecated
 * [androidx.compose.material3.SegmentedButton]. Not [androidx.compose.material3.ButtonGroup]:
 * ButtonGroup `clickableItem` is one-shot (Home #16). Exclusive modes stay ToggleButton.
 * Tapping the already-selected item is a no-op (never collapses to zero selection).
 */
@Composable
fun <T> ConnectedExclusiveToggleRow(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        items.forEachIndexed { index, item ->
            ToggleButton(
                checked = selected == item,
                onCheckedChange = { checked -> if (checked) onSelect(item) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    items.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                }
            ) {
                Text(
                    text = label(item),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
