package com.paul.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Divider
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PropertyEditorRow(
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Column {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .padding(vertical = 12.dp, horizontal = 16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.body1,
                    modifier = Modifier.padding(end = 8.dp) // Space between label and editor
                    // Removed weight(1f) from here so Label gets what it needs first
                )

                // The Content (The TextField or Color Picker)
                // We don't wrap this in another Row usually, just call content()
                // or let content() use weight
                content()
            }

            // Description Row: Sits underneath the Label and Content
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.caption,
                    color = LocalContentColor.current.copy(alpha = ContentAlpha.medium),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        Divider(modifier = Modifier.padding(horizontal = 16.dp))
    }
}