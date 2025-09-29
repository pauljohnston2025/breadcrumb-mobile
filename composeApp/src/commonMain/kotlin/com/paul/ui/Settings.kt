package com.paul.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ExposedDropdownMenuBox
import androidx.compose.material.ExposedDropdownMenuDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.RadioButton
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paul.composables.ColorPickerDialog
import com.paul.composables.LoadingOverlay
import com.paul.domain.ColourPalette
import com.paul.domain.PaletteMappingMode
import com.paul.domain.RGBColor
import com.paul.domain.RouteSettings
import com.paul.domain.ServerType
import com.paul.domain.TileServerInfo
import com.paul.infrastructure.repositories.ColourPaletteRepository
import com.paul.infrastructure.repositories.TileServerRepo
import com.paul.infrastructure.web.TileType
import java.util.UUID
import kotlin.math.roundToInt
import com.paul.viewmodels.Settings as SettingsViewModel

// Function to validate template (basic example)
fun isValidTileUrlTemplate(url: String): Boolean {
    return url.isNotBlank() && url.contains("{z}") && url.contains("{x}") && url.contains("{y}") &&
            (url.startsWith("http://") || url.startsWith("https://"))
}

@Composable
fun AddCustomServerDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    onSave: (TileServerInfo) -> Unit, // Callback with the full server object
    serverToEdit: TileServerInfo?, // Null for new/clone, non-null for editing
) {
    if (!showDialog) return

    var name by remember(serverToEdit) { mutableStateOf(serverToEdit?.title ?: "") }
    var url by remember(serverToEdit) { mutableStateOf(serverToEdit?.url ?: "") }
    var tileLayerMin by remember(serverToEdit) { mutableStateOf(serverToEdit?.tileLayerMin ?: 0) }
    var tileLayerMinString by remember(serverToEdit) { mutableStateOf(tileLayerMin.toString()) }
    var tileLayerMax by remember(serverToEdit) { mutableStateOf(serverToEdit?.tileLayerMax ?: 15) }
    var tileLayerMaxString by remember(serverToEdit) { mutableStateOf(tileLayerMax.toString()) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var urlError by remember { mutableStateOf<String?>(null) }
    var tileLayerMinError by remember { mutableStateOf<String?>(null) }
    var tileLayerMaxError by remember { mutableStateOf<String?>(null) }

    fun validate(): Boolean {
        nameError = if (name.isBlank()) "Name cannot be empty" else null
        urlError = when {
            url.isBlank() -> "URL template cannot be empty"
            !isValidTileUrlTemplate(url) -> "Invalid template (must contain {z}, {x}, {y} and start with http:// or https://)"
            else -> null
        }
        tileLayerMinError = if (tileLayerMinString.toIntOrNull() == null) "Invalid number" else null
        tileLayerMaxError = if (tileLayerMaxString.toIntOrNull() == null) "Invalid number" else null
        return nameError == null && urlError == null && tileLayerMinError == null && tileLayerMaxError == null
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                if (serverToEdit?.isCustom == true && serverToEdit.title.startsWith("Copy of ")
                        .not()
                ) "Edit Custom Tile Server" else "Add Custom Tile Server"
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = null },
                    label = { Text("Display Name") },
                    placeholder = { Text("e.g., My Map Server") },
                    singleLine = true,
                    isError = nameError != null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (nameError != null) {
                    Text(
                        nameError!!,
                        color = MaterialTheme.colors.error,
                        style = MaterialTheme.typography.body1
                    )
                }

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; urlError = null },
                    label = { Text("URL Template") },
                    placeholder = { Text("e.g., https://server.com/{z}/{x}/{y}.png") },
                    singleLine = true,
                    isError = urlError != null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (urlError != null) {
                    Text(
                        urlError!!,
                        color = MaterialTheme.colors.error,
                        style = MaterialTheme.typography.body1
                    )
                }
                Text(
                    "Include {z}, {x}, {y} placeholders.",
                    style = MaterialTheme.typography.caption
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = tileLayerMinString,
                        onValueChange = {
                            tileLayerMinString = it
                            tileLayerMinError = null
                            it.toIntOrNull()?.let { parsed -> tileLayerMin = parsed }
                        },
                        label = { Text("Min Zoom") },
                        singleLine = true,
                        isError = tileLayerMinError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = tileLayerMaxString,
                        onValueChange = {
                            tileLayerMaxString = it
                            tileLayerMaxError = null
                            it.toIntOrNull()?.let { parsed -> tileLayerMax = parsed }
                        },
                        label = { Text("Max Zoom") },
                        singleLine = true,
                        isError = tileLayerMaxError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (validate()) {
                        val finalServer = TileServerInfo(
                            id = serverToEdit?.id ?: UUID.randomUUID()
                                .toString(), // Preserve ID if editing
                            serverType = serverToEdit?.serverType ?: ServerType.CUSTOM,
                            title = name,
                            url = url,
                            tileLayerMin = tileLayerMin,
                            tileLayerMax = tileLayerMax,
                            isCustom = true // Any saved server from this dialog is custom
                        )
                        onSave(finalServer)
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}


@Composable
fun ColourPaletteDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    onSave: (ColourPalette) -> Unit,
    paletteToEdit: ColourPalette?, // Null for new, non-null for editing or cloning
) {
    if (!showDialog) return

    var name by remember(paletteToEdit) { mutableStateOf(paletteToEdit?.name ?: "") }
    var colors by remember(paletteToEdit) {
        mutableStateOf(
            paletteToEdit?.colors?.toMutableList()
                ?: mutableListOf(
                    RGBColor(255, 255, 255),
                    RGBColor(0, 0, 0)
                ) // Default for a new palette
        )
    }
    var mappingMode by remember(paletteToEdit) {
        mutableStateOf(paletteToEdit?.mappingMode ?: PaletteMappingMode.NEAREST_NEIGHBOR)
    }
    var nameError by remember { mutableStateOf<String?>(null) }
    var colorToEditInfo by remember { mutableStateOf<Pair<Int, RGBColor>?>(null) }


    fun validate(): Boolean {
        nameError = if (name.isBlank()) "Palette name cannot be empty" else null
        val colorsAreValid = colors.all { it.r in 0..255 && it.g in 0..255 && it.b in 0..255 }
        return nameError == null && colors.isNotEmpty() && colorsAreValid
    }

    // --- Color Picker Dialog ---
    colorToEditInfo?.let { (index, color) ->
        ColorPickerDialog(
            initialColor = Color(color.r, color.g, color.b),
            showDialog = true,
            onDismissRequest = { colorToEditInfo = null },
            onColorSelected = { newComposeColor ->
                val newRgbColor = RGBColor(
                    r = (newComposeColor.red * 255).roundToInt().coerceIn(0, 255),
                    g = (newComposeColor.green * 255).roundToInt().coerceIn(0, 255),
                    b = (newComposeColor.blue * 255).roundToInt().coerceIn(0, 255)
                )
                val newColors = colors.toMutableList()
                newColors[index] = newRgbColor
                colors = newColors
                colorToEditInfo = null // Close the dialog
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(if (paletteToEdit?.isEditable == false) "Clone Colour Palette" else if (paletteToEdit == null) "Create New Colour Palette" else "Edit Colour Palette") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = null },
                    label = { Text("Palette Name") },
                    isError = nameError != null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (nameError != null) {
                    Text(nameError!!, color = MaterialTheme.colors.error, style = MaterialTheme.typography.body1)
                }

                Spacer(Modifier.height(16.dp))

                // --- Mapping Mode Selector ---
                Text("Mapping Mode:", style = MaterialTheme.typography.subtitle1)
                // Use Column for vertical layout
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { mappingMode = PaletteMappingMode.NEAREST_NEIGHBOR }.padding(vertical = 2.dp)
                    ) {
                        RadioButton(selected = mappingMode == PaletteMappingMode.NEAREST_NEIGHBOR, onClick = { mappingMode = PaletteMappingMode.NEAREST_NEIGHBOR })
                        Spacer(Modifier.width(4.dp))
                        Text("Nearest (RGB)")
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { mappingMode = PaletteMappingMode.CIELAB }.padding(vertical = 2.dp)
                    ) {
                        RadioButton(selected = mappingMode == PaletteMappingMode.CIELAB, onClick = { mappingMode = PaletteMappingMode.CIELAB })
                        Spacer(Modifier.width(4.dp))
                        Text("Perceptual (CIELAB)")
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { mappingMode = PaletteMappingMode.ORDERED_BY_BRIGHTNESS }.padding(vertical = 2.dp)
                    ) {
                        RadioButton(selected = mappingMode == PaletteMappingMode.ORDERED_BY_BRIGHTNESS, onClick = { mappingMode = PaletteMappingMode.ORDERED_BY_BRIGHTNESS })
                        Spacer(Modifier.width(4.dp))
                        Text("Brightness (Gradient)")
                    }
                }
                // --- End Mapping Mode Selector ---

                Spacer(Modifier.height(8.dp))
                Text("Colors: (${colors.size}/64)", style = MaterialTheme.typography.subtitle1)
                Spacer(Modifier.height(4.dp))

                LazyColumn(modifier = Modifier.height(200.dp)) {
                    itemsIndexed(colors) { index, color ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color(color.r, color.g, color.b))
                                    .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                                    .clickable { colorToEditInfo = index to color }
                            )

                            Text("R:${color.r} G:${color.g} B:${color.b}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.body2)

                            IconButton(onClick = {
                                val newColors = colors.toMutableList(); newColors.removeAt(index); colors = newColors
                            }, enabled = colors.size > 1) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove color")
                            }
                        }
                    }
                }
                Button(
                    onClick = { colors = (colors + RGBColor(0, 0, 0)).toMutableList() },
                    enabled = colors.size < 64,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("Add Color")
                }
                if (colors.size >= 64) {
                    Text(
                        "Maximum of 64 colors reached.",
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (validate()) {
                    val finalColors = colors.take(64)
                    val newPalette = ColourPalette(
                        watchAppPaletteId = paletteToEdit?.watchAppPaletteId ?: 0,
                        uniqueId = paletteToEdit?.uniqueId ?: UUID.randomUUID().toString(),
                        name = name,
                        colors = finalColors,
                        mappingMode = mappingMode,
                        isEditable = true
                    )
                    onSave(newPalette)
                }
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun Settings(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel,
    paletteToCreate: ColourPalette?
) {
    var expanded by remember { mutableStateOf(false) }
    var tileTypeExpanded by remember { mutableStateOf(false) }
    var colourPaletteExpanded by remember { mutableStateOf(false) }

    var serverToEdit by remember { mutableStateOf<TileServerInfo?>(null) }
    var showAddServerDialog by remember { mutableStateOf(false) }
    var serverToDelete by remember { mutableStateOf<TileServerInfo?>(null) }
    val showDeleteConfirmDialog = serverToDelete != null

    var showPaletteDialog by remember { mutableStateOf(false) }
    var paletteToEdit by remember { mutableStateOf<ColourPalette?>(null) }
    var paletteToDelete by remember { mutableStateOf<ColourPalette?>(null) }
    val showDeletePaletteConfirmDialog = paletteToDelete != null


    val currentTileServer by viewModel.tileServerRepo.currentServerFlow()
        .collectAsState(TileServerRepo.defaultTileServer)
    val currentTileType by viewModel.tileServerRepo.currentTileTypeFlow()
        .collectAsState(TileServerRepo.defaultTileType)
    val availableServers by viewModel.tileServerRepo.availableServersFlow()
        .collectAsState(listOf(TileServerRepo.defaultTileServer))
    val availableTileTypes by viewModel.tileServerRepo.availableTileTypesFlow()
        .collectAsState(listOf(TileType.TILE_DATA_TYPE_64_COLOUR))
    val tileServerEnabled by viewModel.tileServerRepo.tileServerEnabledFlow().collectAsState(true)
    val authTokenFlow by viewModel.tileServerRepo.authTokenFlow().collectAsState("")

    val currentColourPalette by viewModel.currentColourPalette.collectAsState(
        ColourPaletteRepository.opentopoPalette
    )
    val availableColourPalettes by viewModel.availableColourPalettes.collectAsState(
        ColourPaletteRepository.systemPalettes
    )

    val routeSettings by viewModel.routesRepo.currentSettingsFlow()
        .collectAsState(RouteSettings.default)
    var coordsLimitString by remember { mutableStateOf("") }
    var dirsLimitString by remember { mutableStateOf("") }

    LaunchedEffect(paletteToCreate) {
        if (paletteToCreate != null) {
            paletteToEdit = paletteToCreate
            showPaletteDialog = true
        }
    }

    LaunchedEffect(routeSettings) {
        routeSettings?.let {
            coordsLimitString = it.coordinatesPointLimit.toString()
            dirsLimitString = it.directionsPointLimit.toString()
        }
    }

    AddCustomServerDialog(
        showDialog = showAddServerDialog,
        onDismissRequest = { showAddServerDialog = false; serverToEdit = null },
        onSave = { server ->
            viewModel.onSaveCustomServer(server)
            showAddServerDialog = false
            serverToEdit = null
        },
        serverToEdit = serverToEdit
    )

    ColourPaletteDialog(
        showDialog = showPaletteDialog,
        onDismissRequest = { showPaletteDialog = false; paletteToEdit = null },
        onSave = { palette ->
            viewModel.onAddOrUpdateCustomPalette(palette)
            showPaletteDialog = false
            paletteToEdit = null
        },
        paletteToEdit = paletteToEdit
    )

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { serverToDelete = null },
            title = { Text("Confirm Deletion") },
            text = { serverToDelete?.let { Text("Are you sure you want to delete '${it.title}'?") } },
            confirmButton = {
                Button(onClick = {
                    serverToDelete?.let(viewModel::onRemoveCustomServer); serverToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { serverToDelete = null }) { Text("Cancel") } }
        )
    }

    if (showDeletePaletteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { paletteToDelete = null },
            title = { Text("Confirm Deletion") },
            text = { paletteToDelete?.let { Text("Are you sure you want to delete '${it.name}'?") } },
            confirmButton = {
                Button(onClick = {
                    paletteToDelete?.let(viewModel::onRemoveCustomPalette); paletteToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { paletteToDelete = null }) { Text("Cancel") } }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Phone Hosted Tile Server:", style = MaterialTheme.typography.body1)
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Tile Server Enabled:", style = MaterialTheme.typography.body2)
                Switch(
                    checked = tileServerEnabled,
                    onCheckedChange = viewModel::onTileServerEnabledChange
                )
            }

            if (tileServerEnabled) {
                ExposedDropdownMenuBox(
                    expanded = tileTypeExpanded,
                    onExpandedChange = { tileTypeExpanded = !tileTypeExpanded }
                ) {
                    OutlinedTextField(
                        value = currentTileType.label(), onValueChange = {}, readOnly = true,
                        label = { Text("Tile Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tileTypeExpanded) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = tileTypeExpanded,
                        onDismissRequest = { tileTypeExpanded = false }) {
                        availableTileTypes.forEach { type ->
                            DropdownMenuItem(onClick = {
                                viewModel.onTileTypeSelected(type); tileTypeExpanded = false
                            }) {
                                Text(type.label())
                            }
                        }
                    }
                }

                if (currentTileType == TileType.TILE_DATA_TYPE_64_COLOUR) {
                    Spacer(Modifier.height(8.dp))
                    Text("Colour Palette:", style = MaterialTheme.typography.body1)
                    Row(
                        Modifier.fillMaxWidth(),
                        Arrangement.SpaceBetween,
                        Alignment.CenterVertically
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = colourPaletteExpanded,
                            onExpandedChange = {
                                colourPaletteExpanded = !colourPaletteExpanded
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = currentColourPalette.name,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Select Colour Palette") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = colourPaletteExpanded) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = colourPaletteExpanded,
                                onDismissRequest = { colourPaletteExpanded = false }) {
                                availableColourPalettes.forEach { palette ->
                                    DropdownMenuItem(onClick = {
                                        viewModel.onColourPaletteSelected(
                                            palette
                                        ); colourPaletteExpanded = false
                                    }) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                palette.name,
                                                Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            IconButton(onClick = {
                                                paletteToEdit = palette.copy(
                                                    watchAppPaletteId = 0,
                                                    uniqueId = UUID.randomUUID().toString(), // Generate new ID for clone
                                                    name = "Copy of ${palette.name}",
                                                    isEditable = true
                                                )
                                                showPaletteDialog = true; colourPaletteExpanded =
                                                false
                                            }, modifier = Modifier.size(24.dp)) {
                                                Icon(
                                                    Icons.Default.ContentCopy,
                                                    "Clone palette"
                                                )
                                            }
                                            if (palette.isEditable) {
                                                IconButton(onClick = {
                                                    paletteToEdit =
                                                        palette; showPaletteDialog =
                                                    true; colourPaletteExpanded = false
                                                }, modifier = Modifier.size(24.dp)) {
                                                    Icon(Icons.Default.Edit, "Edit palette")
                                                }
                                                IconButton(onClick = {
                                                    paletteToDelete =
                                                        palette; colourPaletteExpanded =
                                                    false
                                                }, modifier = Modifier.size(24.dp)) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        "Delete palette",
                                                        tint = MaterialTheme.colors.error
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { paletteToEdit = null; showPaletteDialog = true }) {
                            Icon(Icons.Filled.Add, "Add new blank colour palette")
                        }
                    }
                }
            }


            Spacer(Modifier.height(8.dp))
            Text("Tile Server (map view and phone hosted):", style = MaterialTheme.typography.body1)

            AuthTokenEditor(
                currentAuthToken = authTokenFlow,
                onAuthTokenChange = viewModel::onAuthKeyChange
            )

            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Add Custom Server:", style = MaterialTheme.typography.body2)
                IconButton(onClick = { serverToEdit = null; showAddServerDialog = true }) {
                    Icon(Icons.Filled.Add, "Add custom tile server")
                }
            }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }) {
                OutlinedTextField(
                    value = "${currentTileServer.title}\nlayers - min: ${currentTileServer.tileLayerMin} max: ${currentTileServer.tileLayerMax}",
                    onValueChange = {}, readOnly = true,
                    label = { Text("Select Server") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    availableServers.forEach { server ->
                        DropdownMenuItem(onClick = {
                            viewModel.onServerSelected(server); expanded = false
                        }) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        server.title,
                                        style = MaterialTheme.typography.subtitle1,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "layers - min: ${server.tileLayerMin} max: ${server.tileLayerMax}",
                                        style = MaterialTheme.typography.caption,
                                        maxLines = 1
                                    )
                                }
                                IconButton(onClick = {
                                    serverToEdit = server.copy(
                                        id = UUID.randomUUID().toString(),
                                        title = "Copy of ${server.title}",
                                        isCustom = true
                                    )
                                    showAddServerDialog = true; expanded = false
                                }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.ContentCopy, "Clone server")
                                }
                                if (server.isCustom) {
                                    IconButton(onClick = {
                                        serverToEdit = server; showAddServerDialog =
                                        true; expanded = false
                                    }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Edit, "Edit server")
                                    }
                                    IconButton(onClick = {
                                        serverToDelete = server; expanded = false
                                    }, modifier = Modifier.size(24.dp)) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            "Delete custom server",
                                            tint = MaterialTheme.colors.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Routes:", style = MaterialTheme.typography.body1)

            routeSettings?.let { settings ->
                OutlinedTextField(
                    value = coordsLimitString,
                    onValueChange = {
                        coordsLimitString = it
                        it.toIntOrNull()?.let { limit ->
                            viewModel.onRouteSettingsChanged(
                                settings.copy(coordinatesPointLimit = limit)
                            )
                        }
                    },
                    label = { Text("Coordinates point limit") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    isError = coordsLimitString.toIntOrNull() == null,
                    singleLine = true
                )
                OutlinedTextField(
                    value = dirsLimitString,
                    onValueChange = {
                        dirsLimitString = it
                        it.toIntOrNull()?.let { limit ->
                            viewModel.onRouteSettingsChanged(
                                settings.copy(directionsPointLimit = limit)
                            )
                        }
                    },
                    label = { Text("Turn point limit") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    isError = dirsLimitString.toIntOrNull() == null,
                    singleLine = true
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    Arrangement.SpaceBetween,
                    Alignment.CenterVertically
                ) {
                    Text("Mock Directions:", style = MaterialTheme.typography.body2)
                    Switch(
                        checked = settings.mockDirections,
                        onCheckedChange = {
                            viewModel.onRouteSettingsChanged(
                                settings.copy(mockDirections = it)
                            )
                        })
                }
            }
        }
    }

    LoadingOverlay(
        isLoading = viewModel.sendingMessage.value.isNotEmpty(),
        loadingText = viewModel.sendingMessage.value
    )
}

@Composable
fun AuthTokenEditor(
    modifier: Modifier = Modifier,
    currentAuthToken: String,
    onAuthTokenChange: (newKey: String) -> Unit,
    label: String = "Auth Token",
    enabled: Boolean = true,
    obscureText: Boolean = false
) {
    var textFieldValue by remember(currentAuthToken) { mutableStateOf(currentAuthToken) }
    Row(modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = {
                textFieldValue = it
                onAuthTokenChange(it)
            },
            modifier = Modifier.weight(1f),
            label = { Text(label) },
            singleLine = true,
            enabled = enabled,
            visualTransformation = if (obscureText) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (obscureText) KeyboardType.Password else KeyboardType.Ascii)
        )
    }
}