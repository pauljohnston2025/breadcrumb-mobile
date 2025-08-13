package com.paul.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
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
fun ProfilesScreen(viewModel: ProfilesViewModel) {
    BackHandler(enabled = viewModel.sendingMessage.value != "") {
        // prevent back handler when we are trying to do things, todo cancel the job we are trying to do
    }

    val profiles by viewModel.profileRepo.availableProfilesFlow().collectAsState()
    val profileBeingEdited by viewModel.editingProfile.collectAsState()
    val profileBeingCreated by viewModel.creatingProfile.collectAsState()
    val profileBeingImported by viewModel.importingProfile.collectAsState()
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
            Button(
                onClick = { viewModel.startCreate() },
                Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Profile")
                Text("Create New Profile")
            }
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { viewModel.startImport() },
                Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(Icons.Default.Download, contentDescription = "Import Profile")
                Text("Import Profile From Json")
            }
            Spacer(Modifier.height(16.dp))

            ProfilesListSection(
                profiles = profiles,
                onEditClick = { viewModel.startEditing(it) },
                onApplyClick = { viewModel.applyProfile(it) },
                onExportClick = { viewModel.exportProfile(it) },
                onDeleteClick = { viewModel.requestDelete(it) }
            )
        }

        // --- Dialogs ---
        // Edit Dialog
        profileBeingEdited?.let { profile ->
            EditProfileDialog(
                profile = profile,
                onConfirm = { newName, loadWatchSettings, loadAppSettings ->
                    viewModel.confirmEdit(
                        profile.profileSettings.id,
                        newName,
                        loadWatchSettings,
                        loadAppSettings,
                    )
                },
                onDismiss = { viewModel.cancelEditing() }
            )
        }

        // Create Dialog
        if (profileBeingCreated) {
            CreateProfileDialog(
                onConfirm = { newName ->
                    viewModel.confirmCreate(
                        newName
                    )
                },
                onDismiss = { viewModel.cancelCreate() }
            )
        }

        // Import Dialog
        if (profileBeingImported) {
            ImportProfileDialog(
                onConfirm = { json ->
                    viewModel.confirmImport(
                        json
                    )
                },
                onDismiss = { viewModel.cancelImport() }
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
    onApplyClick: (Profile) -> Unit,
    onExportClick: (Profile) -> Unit,
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
                            onApplyClick = { onApplyClick(profile) },
                            onExportClick = { onExportClick(profile) },
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
    onApplyClick: () -> Unit,
    onExportClick: () -> Unit,
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
                Icon(Icons.Default.Edit, contentDescription = "Edit Profile")
            }
            IconButton(onClick = onApplyClick) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Apply Profile")
            }
            IconButton(onClick = onExportClick) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Export Profile")
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
    onConfirm: (newName: String, loadWatchSettings: Boolean, loadAppSettings: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var currentName by remember(profile.profileSettings.id) { mutableStateOf(profile.profileSettings.label) } // Reset when profile changes
    var loadWatchSettings by remember { mutableStateOf(false) }
    var loadAppSettings by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Profile") },
        text = {
            Column { // Use column in case you add more fields later
                OutlinedTextField(
                    value = currentName,
                    onValueChange = { currentName = it },
                    label = { Text("Profile Name") },
                    singleLine = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween // Align label and add button
                ) {
                    Text(
                        text = "Load Watch Settings:",
                        style = MaterialTheme.typography.body2,
                    )
                    Switch(
                        checked = loadWatchSettings,
                        onCheckedChange = { newValue ->
                            loadWatchSettings = newValue
                        },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween // Align label and add button
                ) {
                    Text(
                        text = "Load App Settings:",
                        style = MaterialTheme.typography.body2,
                    )
                    Switch(
                        checked = loadAppSettings,
                        onCheckedChange = { newValue ->
                            loadAppSettings = newValue
                        },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(currentName, loadWatchSettings, loadAppSettings) },
                enabled = currentName.isNotBlank() // Optionally disable if name is empty
            ) { Text("Save") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CreateProfileDialog(
    onConfirm: (newName: String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Profile Name") },
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
private fun ImportProfileDialog(
    onConfirm: (json: String) -> Unit,
    onDismiss: () -> Unit
) {
    var json by remember { mutableStateOf("") }
    var appAuthToken by remember { mutableStateOf("") }
    var watchAuthToken by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Profile") },
        text = {
            Column { // Use column in case you add more fields later
                OutlinedTextField(
                    value = json,
                    onValueChange = { json = it },
                    label = { Text("Json") },
                    singleLine = true
                )

                if (json.contains("<AppAuthTokenRequired>")) {
                    OutlinedTextField(
                        value = appAuthToken,
                        onValueChange = { appAuthToken = it },
                        label = { Text("App Auth Token") },
                        singleLine = true
                    )
                }

                if (json.contains("<WatchAuthTokenRequired>")) {
                    OutlinedTextField(
                        value = watchAuthToken,
                        onValueChange = { watchAuthToken = it },
                        label = { Text("Watch Auth Token") },
                        singleLine = true
                    )
                }

            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        json.replace("<AppAuthTokenRequired>", appAuthToken)
                            .replace("<WatchAuthTokenRequired>", watchAuthToken)
                    )
                },
                enabled = json.isNotBlank() // Optionally disable if name is empty
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
