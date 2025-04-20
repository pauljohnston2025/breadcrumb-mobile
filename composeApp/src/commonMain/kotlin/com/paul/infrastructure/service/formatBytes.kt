package com.paul.infrastructure.service

/**
 * Formats a byte count into a human-readable string with binary prefixes (KB, MB, GB, etc.).
 *
 * @param bytes The number of bytes.
 * @param decimals The number of decimal places to display (default is 1).
 * @return A formatted string like "1.5 KB", "100 MB", "2 GB", "500 B".
 */
fun formatBytes(bytes: Long, decimals: Int = 1): String {
    if (bytes < 0) return "Invalid size" // Or handle negative values as needed
    if (bytes == 0L) return "0 B"

    val k = 1024.0 // Using Double for calculations
    val sizes = listOf("B", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB")

    // Calculate the power index (0 for B, 1 for KB, 2 for MB, etc.)
    // Using log base 1024 - simpler way is iterative division:
    var i = 0
    var sizeInDouble = bytes.toDouble()
    while (sizeInDouble >= k && i < sizes.size - 1) {
        sizeInDouble /= k
        i++
    }

    // Format the number with the specified decimal places
    // Example: "%.1f" formats to 1 decimal place
    val formatSpecifier = "%.${decimals}f"
    val formattedSize = String.format(java.util.Locale.US, formatSpecifier, sizeInDouble) // Use Locale.US for '.' decimal separator

    return "$formattedSize ${sizes[i]}"
}