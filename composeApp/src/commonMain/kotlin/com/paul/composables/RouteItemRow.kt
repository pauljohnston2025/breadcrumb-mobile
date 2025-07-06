package com.paul.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paul.viewmodels.RouteItem

@Composable
fun RouteItemRow(
    route: RouteItem,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit // Add confirmation logic before calling this in real app
) {
    val routeColor = remember(route.colour) { parseColor(route.colour) } // Use your updated parser

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp), // Reduced vertical padding a bit
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left side: Color Preview, Name, Enabled Status
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp) // Limit width, add padding
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(routeColor)
                    .border(1.dp, LocalContentColor.current.copy(alpha = ContentAlpha.disabled), MaterialTheme.shapes.small)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    route.name.ifEmpty { "(No Name)" },
                    style = MaterialTheme.typography.body1,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1, // Prevent name wrapping
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    // Concise status, maybe include ID if useful and not editing
                    "ID: ${route.routeId} - ${if (route.enabled) "Enabled" else "Disabled"}  ${if (route.reversed) "Reversed" else "Forward"}",
                    style = MaterialTheme.typography.caption,
                    color = LocalContentColor.current.copy(alpha = ContentAlpha.medium)
                )
            }
        }

        // Right side: Action Buttons
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onEditClick, modifier = Modifier.size(36.dp)) { // Smaller buttons
                Icon(Icons.Default.Edit, contentDescription = "Edit Route")
            }
            IconButton(onClick = onDeleteClick, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Route", tint = MaterialTheme.colors.error)
            }
        }
    }
}