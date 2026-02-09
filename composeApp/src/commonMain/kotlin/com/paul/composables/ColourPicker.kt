package com.paul.composables

import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Switch
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.paul.viewmodels.padColorString
import io.github.aakira.napier.Napier
import java.lang.Float.max
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Converts an RGB color value to HSV. Conversion formula
 * adapted from https://www.cs.rit.edu/~ncs/color/t_convert.html.
 * Assumes r, g, b are contained in the set [0, 1] and
 * returns hsv values in h = [0, 360], s = [0, 1], v = [0, 1].
 * Alpha is ignored.
 *
 * @param r Red component (0.0f to 1.0f)
 * @param g Green component (0.0f to 1.0f)
 * @param b Blue component (0.0f to 1.0f)
 * @return FloatArray containing HSV values: [hue, saturation, value]
 */
fun rgbToHsv(r: Float, g: Float, b: Float): FloatArray {
    val max = max(max(r, g), b)
    val min = min(min(r, g), b)
    val delta = max - min

    val v = max // Value is the maximum component

    val s = if (max != 0f) delta / max else 0f // Saturation

    val h: Float = if (s == 0f) {
        0f // Hue is undefined for grayscale, return 0
    } else {
        val hue: Float = when (max) {
            r -> (g - b) / delta + (if (g < b) 6 else 0)
            g -> (b - r) / delta + 2
            b -> (r - g) / delta + 4
            else -> 0f // Should not happen
        }
        (hue * 60f) // Convert to degrees
    }

    return floatArrayOf(h, s, v)
}

/**
 * Converts an HSV color value to RGB Color. Conversion formula
 * adapted from https://www.cs.rit.edu/~ncs/color/t_convert.html.
 * Assumes h is contained in the set [0, 360] or -1 for undefined / grayscale,
 * s and v are contained in the set [0, 1] and alpha [0,1].
 *
 * @param h The hue component (0 to 360).
 * @param s The saturation component (0.0f to 1.0f).
 * @param v The value component (0.0f to 1.0f).
 * @param alpha The alpha component (0.0f to 1.0f, defaults to 1.0f).
 * @return The resulting Compose Color object.
 */
fun hsvToColor(h: Float, s: Float, v: Float, alpha: Float = 1.0f): Color {
    if (s <= 0.0f) { // Grayscale
        return Color(red = v, green = v, blue = v, alpha = alpha)
    }

    val hue = if (h >= 360f) 0f else h // Ensure hue is within [0, 360)
    val sector = hue / 60f
    val i = floor(sector).toInt()
    val f = sector - i // Factorial part of hue sector
    val p = v * (1f - s)
    val q = v * (1f - s * f)
    val t = v * (1f - s * (1f - f))

    val red: Float
    val green: Float
    val blue: Float

    when (i) {
        0 -> {
            red = v; green = t; blue = p
        }

        1 -> {
            red = q; green = v; blue = p
        }

        2 -> {
            red = p; green = v; blue = t
        }

        3 -> {
            red = p; green = q; blue = v
        }

        4 -> {
            red = t; green = p; blue = v
        }

        else -> {
            red = v; green = p; blue = q
        } // case 5
    }

    return Color(red = red, green = green, blue = blue, alpha = alpha)
}

@Composable
fun ColorPickerDialog(
    initialColor: Color,
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    onColorSelected: (Color) -> Unit,
    allowTransparent: Boolean = false
) {
    if (showDialog) {
        var selectedTabIndex by remember { mutableStateOf(0) }
        val tabs = listOf("RGB", "HSV")

        // 1. Initialize State with sanitization
        // If transparency isn't allowed, we force the initial color to be opaque immediately
        val sanitizedInitial = remember(initialColor, allowTransparent) {
            if (!allowTransparent && initialColor.alpha == 0f) initialColor.copy(alpha = 0xFE / 255f)
            else initialColor
        }

        var currentColor by remember { mutableStateOf(sanitizedInitial) }

        // Toggle only exists if allowed AND the color is actually transparent
        var isFullyTransparent by remember {
            mutableStateOf(allowTransparent && sanitizedInitial.alpha == 0f)
        }

        var red by remember { mutableStateOf(sanitizedInitial.red) }
        var green by remember { mutableStateOf(sanitizedInitial.green) }
        var blue by remember { mutableStateOf(sanitizedInitial.blue) }

        // Sync RGB sliders when currentColor changes
        LaunchedEffect(currentColor, selectedTabIndex) {
            if (selectedTabIndex == 0) {
                red = currentColor.red
                green = currentColor.green
                blue = currentColor.blue
            }
        }

        // 2. Handle external initialColor changes (prop updates)
        LaunchedEffect(initialColor, allowTransparent) {
            val updatedSanitized = if (!allowTransparent && initialColor.alpha == 0f) {
                initialColor.copy(alpha = 1f)
            } else {
                initialColor
            }
            currentColor = updatedSanitized
            isFullyTransparent = allowTransparent && updatedSanitized.alpha == 0f
        }

        Dialog(onDismissRequest = onDismissRequest) {
            Card(
                modifier = Modifier
                    .wrapContentHeight()
                    .fillMaxWidth(0.9f),
                shape = MaterialTheme.shapes.medium,
                elevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(vertical = 20.dp, horizontal = 24.dp)) {
                    Text("Select Color", style = MaterialTheme.typography.h6)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Preview Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .background(
                                if (isFullyTransparent) Color.Transparent else currentColor.copy(
                                    alpha = 1f
                                )
                            )
                            .border(1.dp, MaterialTheme.colors.onSurface.copy(0.12f))
                    )

                    // --- Toggle Logic ---
                    if (allowTransparent) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Fully Transparent (-1)", style = MaterialTheme.typography.body2)
                            Switch(
                                checked = isFullyTransparent,
                                onCheckedChange = { checked ->
                                    isFullyTransparent = checked
                                    if (!checked) currentColor = currentColor.copy(alpha = 1f)
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Picker Section
                    Box(modifier = Modifier.alpha(if (isFullyTransparent) 0.4f else 1f)) {
                        Column {
                            TabRow(selectedTabIndex = selectedTabIndex) {
                                tabs.forEachIndexed { index, title ->
                                    Tab(
                                        selected = selectedTabIndex == index,
                                        onClick = {
                                            if (!isFullyTransparent) selectedTabIndex = index
                                        },
                                        text = { Text(title) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            when (selectedTabIndex) {
                                0 -> Column {
                                    LaunchedEffect(red, green, blue) {
                                        currentColor = Color(red, green, blue, 1f)
                                    }
                                    ColorSlider("Red", red) { if (!isFullyTransparent) red = it }
                                    ColorSlider("Green", green) {
                                        if (!isFullyTransparent) green = it
                                    }
                                    ColorSlider("Blue", blue) { if (!isFullyTransparent) blue = it }
                                }

                                1 -> HsvColorPickerSquareSlider(
                                    initialColor = currentColor.copy(alpha = 1f),
                                    onColorChanged = { newColor ->
                                        if (!isFullyTransparent) currentColor =
                                            newColor.copy(alpha = 1f)
                                    },
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )
                            }
                        }
                        if (isFullyTransparent) {
                            Box(modifier = Modifier
                                .matchParentSize()
                                .clickable(enabled = false) {})
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Final Hex Logic
                    val hexValue = colorToHexString(currentColor, isFullyTransparent)
                    Text(
                        "Hex: #$hexValue",
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.align(Alignment.End)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismissRequest) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            val finalColor =
                                if (isFullyTransparent) Color.Transparent else currentColor.copy(
                                    alpha = 1f
                                )
                            onColorSelected(finalColor)
                            onDismissRequest()
                        }) {
                            Text("OK")
                        }
                    }
                }
            }
        }
    }
}

/**
 * A color picker using an HSV model with a Hue slider and Saturation/Value square.
 *
 * @param initialColor The initial color to display.
 * @param onColorChanged Callback function invoked when the color changes via interaction.
 * @param modifier Modifier for the entire picker component.
 * @param squareSize The size of the Saturation/Value square.
 * @param hueSliderWidth The width of the Hue slider.
 */
@Composable
fun HsvColorPickerSquareSlider(
    initialColor: Color,
    onColorChanged: (color: Color) -> Unit,
    modifier: Modifier = Modifier,
    squareSize: Dp = 200.dp,
    hueSliderWidth: Dp = 30.dp,
    spacing: Dp = 16.dp
) {
    // Internal state for HSV components
    var hue by remember { mutableStateOf(0f) }
    var saturation by remember { mutableStateOf(0f) }
    var value by remember { mutableStateOf(0f) }
    var alpha by remember { mutableStateOf(1f) } // Keep alpha if needed

    // Effect to update HSV state when initialColor changes
    LaunchedEffect(initialColor) {
        val hsv = rgbToHsv(initialColor.red, initialColor.green, initialColor.blue)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
        alpha = initialColor.alpha
    }

    // The derived current color based on internal HSV state
    val currentColor = remember(hue, saturation, value, alpha) {
        hsvToColor(hue, saturation, value, alpha)
    }

    // Callback for when interaction causes HSV change
    val updateColorFromHsv = { h: Float, s: Float, v: Float ->
        hue = h
        saturation = s
        value = v
        onColorChanged(hsvToColor(h, s, v, alpha)) // Notify parent
    }

    Row(modifier = modifier) {
        // Saturation Value Square
        SaturationValueSquare(
            hue = hue,
            saturation = saturation,
            value = value,
            modifier = Modifier.size(squareSize),
            onSatValChanged = { s, v -> updateColorFromHsv(hue, s, v) }
        )

        Spacer(modifier = Modifier.width(spacing))

        // Hue Slider
        HueSlider(
            hue = hue,
            modifier = Modifier
                .width(hueSliderWidth)
                .height(squareSize), // Match height
            onHueChanged = { h -> updateColorFromHsv(h, saturation, value) }
        )

        // Optional: Add Alpha Slider here if needed
        // AlphaSlider(...)
    }
}


/**
 * Composable that displays a square where the X-axis represents Saturation and the
 * Y-axis represents Value (Brightness) for a given Hue. Allows the user to select
 * Saturation and Value by tapping or dragging.
 *
 * @param hue The current Hue (0f-360f) value. The background gradients depend on this.
 * @param saturation The current Saturation (0f-1f) value, used to position the indicator.
 * @param value The current Value (0f-1f) value, used to position the indicator.
 * @param modifier Modifier to be applied to the Canvas. Should specify a size.
 * @param onSatValChanged Callback invoked with the newly selected saturation and value
 *                        when the user interacts with the square.
 */
@Composable
fun SaturationValueSquare(
    hue: Float,
    saturation: Float,
    value: Float,
    modifier: Modifier = Modifier, // Expect size modifier from the caller
    onSatValChanged: (saturation: Float, value: Float) -> Unit
) {
    // State to store the measured size of the Canvas
    var canvasSize by remember { mutableStateOf(Size.Zero) } // Use Size type

    // Calculate the visual position of the indicator circle based on S/V state
    // Remember calculation based on inputs and canvas size for efficiency
    val indicatorOffsetX = remember(saturation, canvasSize.width) {
        if (canvasSize.width > 0) saturation * canvasSize.width else 0f
    }
    val indicatorOffsetY = remember(value, canvasSize.height) {
        // Value 1.0 is at the top (Y=0), Value 0.0 is at the bottom (Y=height)
        if (canvasSize.height > 0) (1f - value) * canvasSize.height else 0f
    }

    // Modifier for handling user input (taps and drags)
    val interactionModifier = Modifier.pointerInput(canvasSize) { // Depend on canvasSize
        // Combined gesture detector for tap and drag
        detectDragGestures(
            // Called when a drag starts (equivalent to tap if no drag occurs)
            onDragStart = { offset ->
                if (canvasSize.width > 0 && canvasSize.height > 0) {
                    val s = (offset.x / canvasSize.width).coerceIn(0f, 1f)
                    val v = (1f - (offset.y / canvasSize.height)).coerceIn(
                        0f,
                        1f
                    ) // Invert Y axis for Value
                    onSatValChanged(s, v)
                }
            },
            // Called repeatedly during a drag
            onDrag = { change, _ ->
                if (canvasSize.width > 0 && canvasSize.height > 0) {
                    val s = (change.position.x / canvasSize.width).coerceIn(0f, 1f)
                    val v = (1f - (change.position.y / canvasSize.height)).coerceIn(
                        0f,
                        1f
                    ) // Invert Y axis for Value
                    onSatValChanged(s, v)
                    change.consume() // Consume the position change event
                }
            }
            // onDragEnd, onDragCancel can be added if needed
        )
    }
    // Alternative using separate detectors (works similarly):
    /*
    val interactionModifier = Modifier
        .pointerInput(canvasSize) { // Detect Taps
            detectTapGestures(
                onTap = { offset ->
                    if (canvasSize.width > 0 && canvasSize.height > 0) {
                        val s = (offset.x / canvasSize.width).coerceIn(0f, 1f)
                        val v = (1f - (offset.y / canvasSize.height)).coerceIn(0f, 1f)
                        onSatValChanged(s, v)
                    }
                }
            )
        }
        .pointerInput(canvasSize) { // Detect Drags
            detectDragGestures(
                onDrag = { change, _ ->
                    if (canvasSize.width > 0 && canvasSize.height > 0) {
                        val s = (change.position.x / canvasSize.width).coerceIn(0f, 1f)
                        val v = (1f - (change.position.y / canvasSize.height)).coerceIn(0f, 1f)
                        onSatValChanged(s, v)
                        change.consume()
                    }
                }
                // Optional: onDragStart might be useful too
            )
        }
    */


    Canvas(modifier = modifier.then(interactionModifier)) {
        // Update the stored canvas size whenever the canvas is drawn/resized
        canvasSize = size

        // Ensure size is valid before drawing gradients or indicator
        if (size.width <= 0f || size.height <= 0f) return@Canvas

        // Calculate the pure hue color (full saturation and value)
        val pureHueColor = hsvToColor(h = hue, s = 1f, v = 1f)

        // 1. Draw Saturation Gradient Background (Left=White -> Right=Pure Hue)
        val saturationGradient = Brush.horizontalGradient(
            colors = listOf(Color.White, pureHueColor),
            startX = 0f,
            endX = size.width
        )
        drawRect(brush = saturationGradient)

        // 2. Draw Value Gradient Overlay (Top=Transparent -> Bottom=Black)
        val valueGradient = Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color.Black),
            startY = 0f,
            endY = size.height
        )
        drawRect(brush = valueGradient)

        // 3. Draw the selection indicator circle
        val indicatorRadius = 8.dp.toPx() // Radius of the circle indicator
        val strokeWidth = 2.dp.toPx()   // Width of the circle's outline

        // Draw white outline
        drawCircle(
            color = Color.White,
            radius = indicatorRadius,
            center = Offset(indicatorOffsetX, indicatorOffsetY),
            style = Stroke(width = strokeWidth)
        )
        // Draw black inner outline for contrast
        drawCircle(
            color = Color.Black,
            radius = indicatorRadius - strokeWidth, // Slightly smaller radius
            center = Offset(indicatorOffsetX, indicatorOffsetY),
            style = Stroke(width = strokeWidth / 2f) // Thinner inner stroke
        )
    }
}


/**
 * Composable that displays a vertical slider for selecting the Hue component of a color.
 *
 * @param hue The current Hue value (0f-360f), used to position the indicator.
 * @param modifier Modifier to be applied to the Canvas. Should specify width and height.
 * @param onHueChanged Callback invoked with the newly selected hue value (0f-360f)
 *                     when the user interacts with the slider.
 */
@Composable
fun HueSlider(
    hue: Float,
    modifier: Modifier = Modifier, // Expect size modifier from the caller
    onHueChanged: (hue: Float) -> Unit
) {
    // State to store the measured size of the Canvas
    var canvasSize by remember { mutableStateOf(Size.Zero) } // Use Size type

    // Calculate the visual position of the indicator based on Hue state
    // Remember calculation based on hue and canvas height for efficiency
    val indicatorOffsetY = remember(hue, canvasSize.height) {
        // Map hue (0-360) to the vertical position (0-height)
        if (canvasSize.height > 0) (hue.coerceIn(0f, 360f) / 360f) * canvasSize.height else 0f
    }

    // Modifier for handling user input (taps and drags)
    val interactionModifier = Modifier.pointerInput(canvasSize) { // Depend on canvasSize
        // Combined gesture detector for tap and drag on the vertical axis
        detectDragGestures(
            // Called when a drag starts (equivalent to tap if no drag occurs)
            onDragStart = { offset ->
                if (canvasSize.height > 0) {
                    // Map vertical offset (0-height) back to hue (0-360)
                    val h = (offset.y / canvasSize.height * 360f).coerceIn(0f, 360f)
                    onHueChanged(h)
                }
            },
            // Called repeatedly during a drag
            onDrag = { change, _ ->
                if (canvasSize.height > 0) {
                    // Map vertical position back to hue (0-360)
                    val h = (change.position.y / canvasSize.height * 360f).coerceIn(0f, 360f)
                    onHueChanged(h)
                    change.consume() // Consume the position change event
                }
            }
            // onDragEnd, onDragCancel can be added if needed
        )
    }

    Canvas(modifier = modifier.then(interactionModifier)) {
        // Update the stored canvas size whenever the canvas is drawn/resized
        canvasSize = size

        // Ensure size is valid before drawing gradient or indicator
        if (size.width <= 0f || size.height <= 0f) return@Canvas

        // 1. Define the colors for the Hue gradient (rainbow)
        val hueColors = listOf(
            Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
        )

        // 2. Draw the vertical Hue gradient
        val hueGradient = Brush.verticalGradient(
            colors = hueColors,
            startY = 0f,
            endY = size.height
        )
        drawRect(brush = hueGradient)

        // 3. Draw the selection indicator (horizontal line)
        val indicatorHeight = 4.dp.toPx() // Thickness of the indicator line
        val strokeWidth = 1.5.dp.toPx()  // Width of the outline stroke

        // Draw white outline portion
        drawLine(
            color = Color.White,
            start = Offset(0f, indicatorOffsetY),
            end = Offset(size.width, indicatorOffsetY),
            strokeWidth = indicatorHeight + strokeWidth // Slightly thicker for outline effect
        )
        // Draw black inner portion
        drawLine(
            color = Color.Black,
            start = Offset(0f, indicatorOffsetY),
            end = Offset(size.width, indicatorOffsetY),
            strokeWidth = indicatorHeight - strokeWidth // Slightly thinner inner line
        )

        // --- Alternative Indicator: Circle ---
        /*
        val indicatorRadius = 6.dp.toPx()
        val centerOffsetX = size.width / 2f // Center horizontally
        drawCircle(
            color = Color.White,
            radius = indicatorRadius,
            center = Offset(centerOffsetX, indicatorOffsetY),
            style = Stroke(width = 2.dp.toPx())
        )
        drawCircle(
            color = Color.Black,
            radius = indicatorRadius - 2.dp.toPx(),
            center = Offset(centerOffsetX, indicatorOffsetY),
            style = Stroke(width = 1.dp.toPx())
        )
        */
    }
}

// Helper composable for a single color slider row
@Composable
private fun ColorSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f, // Default for RGB
    displayMultiplier: Float = 255f, // Default for RGB (0-255 display)
    onValueChange: (Float) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(label, modifier = Modifier.width(50.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            valueRange = valueRange
        )
        Text( // Display value scaled appropriately
            text = (value * displayMultiplier).roundToInt().toString(),
            modifier = Modifier
                .width(40.dp)
                .padding(start = 8.dp), // Wider for 360/100
            style = MaterialTheme.typography.caption
        )
    }
}

/**
 * Parses an AARRGGBB or RRGGBB hex string (case-insensitive, optional '#' prefix)
 * into a Compose Color. Handles 8-digit (AARRGGBB) and 6-digit (RRGGBB) inputs.
 * For 6-digit inputs, alpha is assumed to be FF (fully opaque).
 * Returns Color.Black if parsing fails or format is invalid.
 */
fun parseColor(hexString: String): Color {
    if (hexString == "FFFFFFFF") {
        // special case on garmin this is fully transparent
        // even though the alpha channel is set to FF
        return Color(red = 0xFF, green = 0xFF, blue = 0xFF, alpha = 0)
    }

    val funcName = "parseColor"
    return try {
        var sanitized = hexString.trim().removePrefix("#").uppercase()

        val finalHex = padColorString(sanitized)

        // Parse the final 8-digit hex string to get the AARRGGBB Int value
        // Using Long to avoid sign issues during parsing before fitting into Int
        val argbInt = finalHex.toLong(16).toInt()

        // *** CHANGE: Use the component constructor ***
        // Extract components directly from the ARGB integer
        val alpha = (argbInt shr 24) and 0xFF
        val red = (argbInt shr 16) and 0xFF
        val green = (argbInt shr 8) and 0xFF
        val blue = argbInt and 0xFF

        // Create Color using the component constructor (Ints 0-255)
        val resultColor = Color(red = red, green = green, blue = blue, alpha = alpha)
        // Napier.d("$funcName: Parsed '$hexString' (ARGB: ${argbInt.toUInt().toString(16)}) to Color: $resultColor")
        resultColor

    } catch (e: Exception) {
        Napier.d("$funcName: Error parsing hex color '$hexString': ${e.message}. Defaulting to Black.")
        Color.Black // Default fallback color
    }
}

// colorToHexString remains the same as the previous AARRGGBB version:
/**
 * Converts a Compose Color into an uppercase 8-character AARRGGBB hex string (without '#').
 */
fun colorToHexString(color: Color, allowTransparent: Boolean = false): String {
    val argb = color.toArgb()
    val ret = argb.toLong().and(0xFFFFFFFF).toString(16).uppercase().padStart(8, '0')
    if (ret == "FFFFFFFF" && !allowTransparent) {
        // -1 is full transparent on the the watch, rather than being 00FFFFFF
        return "FEFFFFFF";
    }

    return ret;
}