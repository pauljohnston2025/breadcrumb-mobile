package com.paul.domain

import com.paul.infrastructure.repositories.ColourPaletteRepository
import com.paul.infrastructure.repositories.TileServerRepo
import com.paul.infrastructure.web.TileType
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

@Serializable
data class AppSettings(
    val tileServerEnabled: Boolean,
    val tileType: TileType,
    val authToken: String,
    val tileServerId: String,
    val routeSettings: RouteSettings = RouteSettings.default,
    val colourPaletteUniqueId: String? = null,
)

@Serializable
data class ProfileSettings(
    val id: String,
    val label: String,
    val createdAt: Instant,
    val importedAt: Instant? = null,
)

@Serializable
data class LastKnownDevice(
    val appVersion: Int,
    val name: String
)

@Serializable
data class Profile(
    val profileSettings: ProfileSettings,
    val appSettings: AppSettings,
    private val deviceSettings: Map<String, JsonElement>,
    val lastKnownDevice: LastKnownDevice,
) {

    fun deviceSettings(): Map<String, Any> {
        return convertJsonElementMapToAnyMap(deviceSettings)
    }

    companion object {

        fun build(
            profileSettings: ProfileSettings,
            appSettings: AppSettings,
            deviceSettingsAsAnyMap: Map<String, Any>,
            lastKnownDevice: LastKnownDevice,
        ): Profile {
            val mutableDeviceSettings = deviceSettingsAsAnyMap.toMutableMap()
            mutableDeviceSettings.remove("routes") // do not touch users routes when changing profiles
            return Profile(
                profileSettings = profileSettings,
                appSettings = appSettings,
                deviceSettings = convertAnyMapToJsonElementMap(mutableDeviceSettings.toMap()),
                lastKnownDevice,
            )
        }

        private fun convertAnyMapToJsonElementMap(anyMap: Map<String, Any?>): Map<String, JsonElement> {
            return anyMap.mapValues { (_, value) ->
                convertAnyToJsonElement(value)
            }
        }

        private fun convertAnyToJsonElement(value: Any?): JsonElement {
            return when (value) {
                null -> JsonNull
                is String -> JsonPrimitive(value)
                is Number -> JsonPrimitive(value) // Handles Int, Long, Float, Double, Short, Byte
                is Boolean -> JsonPrimitive(value)
                is List<*> -> buildJsonArray { // Recursively convert list elements
                    value.forEach { item ->
                        add(convertAnyToJsonElement(item))
                    }
                }

                is Map<*, *> -> buildJsonObject { // Recursively convert map entries
                    // Ensure keys are strings for JSON objects
                    value.forEach { (k, v) ->
                        if (k is String) {
                            put(k, convertAnyToJsonElement(v))
                        } else {
                            // Handle non-string keys if necessary (e.g., convert to string or error)
                            // For simplicity, this example assumes string keys or skips non-string keys
                            System.err.println("Warning: Non-string key encountered in Map: $k. Skipping or convert to string.")
                            // put(k.toString(), convertAnyToJsonElement(v)) // Option: convert key to string
                        }
                    }
                }

                else -> throw IllegalArgumentException("Unsupported type for JsonElement conversion: ${value::class.simpleName}")
                // You might want to add more specific handlers, e.g., for Date, custom objects if they
                // have a known string representation you want to use.
            }
        }

        private fun convertJsonElementMapToAnyMap(jsonElementMap: Map<String, JsonElement>): Map<String, Any> {
            return jsonElementMap.mapValues { (_, jsonElement) ->
                convertJsonElementToAny(jsonElement)
            }
        }

        private fun convertJsonElementToAny(jsonElement: JsonElement): Any {
            return when (jsonElement) {
                is JsonNull -> throw IllegalStateException("JsonNull not expected here, handle if necessary") // Or return actual null if your 'Any' can be null
                is JsonPrimitive -> {
                    if (jsonElement.isString) {
                        jsonElement.content
                    } else {
                        // Try to infer numeric types or boolean
                        jsonElement.booleanOrNull ?: jsonElement.longOrNull
                        ?: // Check long first for whole numbers
                        jsonElement.doubleOrNull ?: // Then double for floating points
                        jsonElement.content // Fallback to content as string if not recognized
                    }
                }

                is JsonArray -> jsonElement.map { convertJsonElementToAny(it) } // Recursive call for list items
                is JsonObject -> jsonElement.mapValues { (_, valueElement) -> // Recursive call for map values
                    convertJsonElementToAny(valueElement)
                }
            }
        }
    }

    fun export(
        tileServerRepo: TileServerRepo,
        colourPaletteRepo: ColourPaletteRepository
    ): ExportedProfile {
        val obfuscatedAuthToken =
            if (appSettings.authToken == "") appSettings.authToken else "<AppAuthTokenRequired>"
        val customServers = mutableListOf<TileServerInfo>()
        var tileServer = tileServerRepo.get(appSettings.tileServerId)
        if (tileServer == null) {
            throw RuntimeException("tile server does not exist")
        }
        if (tileServer.isCustom) {
            customServers.add(tileServer)
        }
        val obfuscatedDeviceSettings = deviceSettings().toMutableMap()
        if (obfuscatedDeviceSettings.containsKey("authToken") && obfuscatedDeviceSettings["authToken"] != "") {
            obfuscatedDeviceSettings["authToken"] = "<WatchAuthTokenRequired>"
        }

        val unsortedMap = convertAnyMapToJsonElementMap(obfuscatedDeviceSettings.toMap())
        val sortedKeys = unsortedMap.keys.sorted()
        val sortedPropertiesMap = LinkedHashMap<String, JsonElement>() // Preserves insertion order
        for (key in sortedKeys) {
            sortedPropertiesMap[key] = unsortedMap[key]!! // Add entries in sorted key order
        }

        val customPalettes = mutableListOf<ColourPalette>()
        if(appSettings.colourPaletteUniqueId != null) {
            val currentPalette =
                colourPaletteRepo.getPaletteByUUID(appSettings.colourPaletteUniqueId)
            if (currentPalette != null && currentPalette.watchAppPaletteId > 0) {
                customPalettes.add(currentPalette)
            }
        }

        return ExportedProfile(
            profileSettings.copy(),
            appSettings.copy(authToken = obfuscatedAuthToken),
            sortedPropertiesMap,
            lastKnownDevice,
            customServers,
            customPalettes
        )
    }
}

@Serializable
data class ExportedProfile(
    val profileSettings: ProfileSettings,
    val appSettings: AppSettings,
    val deviceSettings: Map<String, JsonElement>,
    val lastKnownDevice: LastKnownDevice,
    val customServers: List<TileServerInfo>,
    val customColourPalettes: List<ColourPalette> = listOf()
) {
    fun toProfile(): Profile {
        return Profile(
            profileSettings.copy(importedAt = Clock.System.now()),
            appSettings,
            deviceSettings,
            lastKnownDevice,
        )
    }
}