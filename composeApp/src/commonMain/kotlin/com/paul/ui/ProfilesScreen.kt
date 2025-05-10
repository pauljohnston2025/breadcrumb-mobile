package com.paul.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.paul.domain.Profile
import com.paul.viewmodels.ProfilesViewModel
import kotlinx.datetime.TimeZone.Companion.currentSystemDefault
import kotlinx.datetime.toLocalDateTime


@Composable
fun ProfilesScreen(viewModel: ProfilesViewModel, navController: NavController) {
    BackHandler {
        if (viewModel.sendingMessage.value != "") {
            // prevent back handler when we are trying to do things, todo cancel the job we are trying to do
            return@BackHandler
        }

        navController.popBackStack()
    }

    val profiles by viewModel.profileRepo.availableProfilesFlow().collectAsState()
    val profileBeingEdited by viewModel.editingProfile.collectAsState()
    val profileBeingDeleted by viewModel.deletingProfile.collectAsState()
// Box allows stacking the sending overlay
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // --- UI ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Maybe add a button to create a new profile? (Optional)
            // Button(onClick = { /* TODO: Navigate to profile creation */ }) { Text("Create New Profile") }
            // Spacer(Modifier.height(16.dp))

            ProfilesListSection(
                profiles = profiles,
                onEditClick = { viewModel.startEditing(it) },
                onDeleteClick = { viewModel.requestDelete(it) }
            )
        }

        // --- Dialogs ---
        // Edit Dialog
        profileBeingEdited?.let { profile ->
            EditProfileDialog(
                profile = profile,
                onConfirm = { newName -> viewModel.confirmEdit(profile.profileSettings.id, newName) },
                onDismiss = { viewModel.cancelEditing() }
            )
        }

        // Delete Confirmation Dialog
        profileBeingDeleted?.let { profile ->
            DeleteConfirmationDialog(
                profileName = profile.profileSettings.label,
                onConfirm = { viewModel.confirmDelete() },
                onDismiss = { viewModel.cancelDelete() }
            )
        }

        // --- Sending Overlay ---
        SendingFileOverlay(
            sendingMessage = viewModel.sendingMessage
        )

    } // End Root Box
}

@Composable
private fun ProfilesListSection(
    profiles: List<Profile>,
    onEditClick: (Profile) -> Unit,
    onDeleteClick: (Profile) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (profiles.isEmpty()) {
            Text(
                "No saved profiles found.",
                style = MaterialTheme.typography.body2,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 16.dp)
            )
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(), // Let height grow naturally or set max
                elevation = 2.dp
            ) {
                val list = profiles.toList().sortedByDescending { it.profileSettings.createdAt }
                LazyColumn {
                    items(list, key = { profile -> profile.profileSettings.id }) { profile ->
                        ProfileListItem(
                            profile = profile,
                            onEditClick = { onEditClick(profile) },
                            onDeleteClick = { onDeleteClick(profile) }
                        )
                        Divider() // Separator between items
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileListItem(
    profile: Profile,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {

    Row(
        Modifier
            .padding(start = 8.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = if (profile.profileSettings.label.isNotBlank()) profile.profileSettings.label else "<No Name>",
            style = MaterialTheme.typography.subtitle1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    Row(
        Modifier
            .padding(start = 8.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = "Created At: " + profile.profileSettings.createdAt.toLocalDateTime(
                currentSystemDefault()
            )
                .toString(),
            style = MaterialTheme.typography.caption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    // Action Buttons Section
    Row(
        verticalAlignment = Alignment.Top,
        // Add some space between info and buttons if needed
        modifier = Modifier
            .padding(start = 8.dp)
            .fillMaxWidth()
    ) {
        Spacer(Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically, // Center buttons vertically within this row
            horizontalArrangement = Arrangement.End // Arrange buttons closely together at the end
        ) {
            IconButton(onClick = onEditClick) {
                Icon(Icons.Default.Edit, contentDescription = "Edit Profile Name")
            }
            IconButton(onClick = onDeleteClick) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete Profile",
                    tint = MaterialTheme.colors.error
                ) // Indicate destructive action
            }
        }
    }
}

@Composable
private fun EditProfileDialog(
    profile: Profile,
    onConfirm: (newName: String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentName by remember(profile.profileSettings.id) { mutableStateOf(profile.profileSettings.label) } // Reset when profile changes

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Profile Name") },
        text = {
            Column { // Use column in case you add more fields later
                OutlinedTextField(
                    value = currentName,
                    onValueChange = { currentName = it },
                    label = { Text("Profile Name") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(currentName) },
                enabled = currentName.isNotBlank() // Optionally disable if name is empty
            ) { Text("Save") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun DeleteConfirmationDialog(
    profileName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Delete") },
        text = { Text("Are you sure you want to delete the profile \"${if (profileName.isNotBlank()) profileName else "<No Name>"}\"? This action cannot be undone.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error) // Destructive action color
            ) { Text("Delete") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
