package com.paul.infrastructure.service

import com.paul.domain.StravaActivity
import com.paul.domain.StravaGear
import com.paul.domain.StravaMap
import com.paul.domain.StravaStreamEntity
import com.paul.infrastructure.dao.StravaDao
import com.paul.infrastructure.repositories.SpatialIndexRepository
import com.paul.protocol.todevice.Point
import com.paul.protocol.todevice.Route
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

class StravaImportService(
    private val dao: StravaDao,
    private val fileHelper: IFileHelper,
    private val gpxFileLoader: IGpxFileLoader,
    private val fitFileLoader: IFitFileLoader,
    private val spatialIndexRepository: SpatialIndexRepository,
) {
    private val TAG = "StravaImportService"

    suspend fun importFromZip(zipUri: String, onProgress: (String) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            onProgress("Opening ZIP archive...")
            val zipArchive = fileHelper.openZip(zipUri) ?: return@withContext Result.failure(Exception("Failed to open ZIP archive"))
            
            zipArchive.use { zip ->
                val allEntries = zip.entries()
                Napier.d("ZIP opened. Total entries: ${allEntries.size}", tag = TAG)
                
                // Index entries by name (suffix-only for fast lookup of metadata and files)
                // This converts O(N) entry searches to O(1)
                val entryMap = allEntries.associateBy { it.name.lowercase().replace("\\", "/") }
                val suffixMap = allEntries.associateBy { it.name.substringAfterLast("/").lowercase() }

                onProgress("Reading metadata...")
                val activitiesMetadata = mutableListOf<ActivityExport>()
                val bikeNames = mutableListOf<String>()
                val shoeNames = mutableListOf<String>()

                fun getEntry(suffix: String) = entryMap[suffix.lowercase()] ?: suffixMap[suffix.substringAfterLast("/").lowercase()]

                getEntry("activities.csv")?.let { activitiesMetadata.addAll(parseActivitiesCsv(it.readBytes().decodeToString())) }
                getEntry("bikes.csv")?.let { bikeNames.addAll(parseBikesCsv(it.readBytes().decodeToString())) }
                getEntry("shoes.csv")?.let { shoeNames.addAll(parseShoesCsv(it.readBytes().decodeToString())) }

                if (activitiesMetadata.isEmpty()) {
                    Napier.w("No activities found in metadata!", tag = TAG)
                    return@use
                }

                // Discovered gear from activities (since bikes/shoes.csv don't have IDs)
                val discoveredGears = mutableMapOf<String, StravaGear>()
                for (activityMeta in activitiesMetadata) {
                    val gId = activityMeta.gearId
                    val gName = activityMeta.gearName
                    if (gId != null && gName != null && !discoveredGears.containsKey(gId)) {
                        val isBike = bikeNames.any { gName.contains(it, ignoreCase = true) || it.contains(gName, ignoreCase = true) }
                        val isShoe = shoeNames.any { gName.contains(it, ignoreCase = true) || it.contains(gName, ignoreCase = true) }

                        val type = when {
                            isBike -> StravaGear.TYPE_BIKE
                            isShoe -> StravaGear.TYPE_SHOE
                            activityMeta.type == "Run" || activityMeta.type == "Walk" || activityMeta.type == "Hike" -> StravaGear.TYPE_SHOE
                            else -> StravaGear.TYPE_BIKE
                        }
                        discoveredGears[gId] = StravaGear(gId, gName, false, type)
                    }
                }
                if (discoveredGears.isNotEmpty()) dao.insertGear(discoveredGears.values.toList())

                val gearMap = discoveredGears.values.associate { it.name to it.id }
                val newActivitiesMeta = mutableListOf<ActivityExport>()

                onProgress("Syncing activity info...")
                for (activityMeta in activitiesMetadata) {
                    ensureActive()
                    val existing = dao.getActivity(activityMeta.id)
                    val targetGearId = activityMeta.gearId ?: activityMeta.gearName?.let { gn ->
                        gearMap.entries.find { entry ->
                            gn.equals(entry.key, ignoreCase = true) ||
                                    gn.contains(entry.key, ignoreCase = true) ||
                                    entry.key.contains(gn, ignoreCase = true)
                        }?.value
                    }

                    // activityMeta.distance is already in meters (converted during CSV parsing)
                    val metaDistanceMeters = activityMeta.distance

                    if (existing == null) {
                        newActivitiesMeta.add(activityMeta)
                    } else {
                        var updated = false
                        var activityToUpdate = existing

                        if (targetGearId != null && existing.gearId != targetGearId) {
                            activityToUpdate = activityToUpdate.copy(gearId = targetGearId)
                            updated = true
                        }

                        // If the existing distance is exactly the KM value (or close to it), update it to meters
                        // We check if it's roughly 1000x different to avoid overwriting correct API data with slightly different CSV data
                        if (existing.distance > 0 && metaDistanceMeters > 0) {
                            val ratio = metaDistanceMeters / existing.distance
                            if (ratio > 500f && ratio < 1500f) {
                                activityToUpdate = activityToUpdate.copy(distance = metaDistanceMeters)
                                updated = true
                            }
                        }

                        if (updated) {
                            dao.insertActivities(listOf(activityToUpdate))
                        }
                    }
                }

                val totalToImport = newActivitiesMeta.size
                if (totalToImport == 0) {
                    onProgress("Finished updating metadata.")
                    return@use
                }

                var importedCount = 0

                for (activityMeta in newActivitiesMeta) {
                    // Check for cancellation (fixes Cancel button)
                    ensureActive()
                    
                    val entry = getEntry(activityMeta.filename)
                    
                    if (entry != null) {
                        try {
                            var bytes = entry.readBytes()
                            if (entry.name.endsWith(".gz", ignoreCase = true)) {
                                bytes = fileHelper.decompressGzip(bytes)
                            }

                            val points = if (entry.name.contains(".fit", ignoreCase = true)) {
                                fitFileLoader.loadFitFromBytes(bytes, activityMeta.name).getPoints()
                            } else if (entry.name.contains(".gpx", ignoreCase = true) || entry.name.contains(".tcx", ignoreCase = true)) {
                                gpxFileLoader.loadGpxFromBytes(bytes).getPoints() ?: emptyList()
                            } else emptyList()
                            
                            val summaryPolyline = if (points.isNotEmpty()) {
                                StravaMap.encodePolyline(Route.summary(points))
                            } else null

                            val targetGearId = activityMeta.gearId ?: activityMeta.gearName?.let { gn ->
                                gearMap.entries.find { entry ->
                                    gn.equals(entry.key, ignoreCase = true) ||
                                            gn.contains(entry.key, ignoreCase = true) ||
                                            entry.key.contains(gn, ignoreCase = true)
                                }?.value
                            }

                            val activity = StravaActivity(
                                id = activityMeta.id,
                                name = activityMeta.name,
                                startDate = activityMeta.startDate,
                                type = activityMeta.type,
                                gearId = targetGearId,
                                map = summaryPolyline?.let { StravaMap(it) },
                                distance = activityMeta.distance
                            )
                            
                            // Insert one by one for lively progress and immediate availability
                            dao.insertActivities(listOf(activity))
                            if (points.isNotEmpty()) {
                                dao.insertStream(StravaStreamEntity(activityMeta.id, points))
                            }
                            spatialIndexRepository.indexStravaActivity(activityMeta.id, activity.summaryToRoute().route)

                            importedCount++
                            onProgress("Imported $importedCount / $totalToImport\n${activity.name}")
                        } catch (e: Exception) {
                            Napier.e("Failed to import ${entry.name}", e, tag = TAG)
                        }
                    } else {
                        // Skip if file not found, but count it as processed
                        importedCount++
                        Napier.w("File not found in ZIP: ${activityMeta.filename}", tag = TAG)
                    }
                }

                onProgress("Finished! Imported $importedCount activities.")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Napier.e("Import failed", e, tag = TAG)
            Result.failure(e)
        }
    }

    private fun parseActivitiesCsv(content: String): List<ActivityExport> {
        val activities = mutableListOf<ActivityExport>()
        val lines = splitCsvLines(content)
        if (lines.isEmpty()) return emptyList()
        val header = parseCsvLine(lines[0])
        val idIdx = header.indexOfFirst { it.contains("Activity ID", ignoreCase = true) }
        val dateIdx = header.indexOfFirst { it.contains("Activity Date", ignoreCase = true) }
        val nameIdx = header.indexOfFirst { it.contains("Activity Name", ignoreCase = true) }
        val typeIdx = header.indexOfFirst { it.contains("Activity Type", ignoreCase = true) }
        val bikeIdIdx = header.indexOfFirst { it.equals("Bike", ignoreCase = true) || it.equals("Bike ID", ignoreCase = true) }
        val gearIdIdx = header.indexOfFirst { it.equals("Gear", ignoreCase = true) || it.equals("Gear ID", ignoreCase = true) }
        val gearNameIdx = header.indexOfFirst { it.contains("Activity Gear", ignoreCase = true) }
        val filenameIdx = header.indexOfFirst { it.contains("Filename", ignoreCase = true) }
        val distanceIdx = header.indexOfFirst { it.contains("Distance", ignoreCase = true) }

        if (idIdx == -1 || filenameIdx == -1) return emptyList()
        for (i in 1 until lines.size) {
            val parts = parseCsvLine(lines[i])
            if (parts.size > idIdx && parts.size > filenameIdx) {
                try {
                    val id = parts[idIdx].trim().toLong()
                    val filename = parts[filenameIdx].trim()
                    if (filename.isEmpty()) continue
                    
                    val distance = if (distanceIdx != -1 && distanceIdx < parts.size) {
                        // Strava CSV export uses kilometers, but API uses meters.
                        // We convert to meters to maintain consistency.
                        (parts[distanceIdx].trim().toFloatOrNull() ?: 0f) * 1000f
                    } else 0f

                    val bikeId = if (bikeIdIdx != -1 && bikeIdIdx < parts.size) parts[bikeIdIdx].trim() else ""
                    val gearId = if (gearIdIdx != -1 && gearIdIdx < parts.size) parts[gearIdIdx].trim() else ""
                    val gearName = if (gearNameIdx != -1 && gearNameIdx < parts.size) parts[gearNameIdx].trim() else ""

                    activities.add(
                        ActivityExport(
                            id,
                            parts.getOrNull(nameIdx) ?: "Imported Activity",
                            parseStravaDate(parts.getOrNull(dateIdx) ?: ""),
                            parts.getOrNull(typeIdx),
                            if (bikeId.isNotEmpty()) bikeId else if (gearId.isNotEmpty()) gearId else null,
                            if (gearName.isNotEmpty()) gearName else null,
                            filename,
                            distance
                        )
                    )
                } catch (e: Exception) { }
            }
        }
        return activities
    }

    private fun splitCsvLines(content: String): List<String> {
        val lines = mutableListOf<String>(); val cur = StringBuilder(); var inQuotes = false
        for (c in content) {
            if (c == '\"') inQuotes = !inQuotes
            if (c == '\n' && !inQuotes) { lines.add(cur.toString()); cur.setLength(0) } else cur.append(c)
        }
        if (cur.isNotEmpty()) lines.add(cur.toString()); return lines
    }

    private fun parseStravaDate(dateStr: String): Instant {
        return try { Instant.parse(dateStr) } catch (e: Exception) {
            try {
                val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                val parts = dateStr.replace(",", "").split(" ")
                val month = months.indexOf(parts[0]) + 1
                val day = parts[1].toInt(); val year = parts[2].toInt()
                val timeParts = parts[3].split(":"); var hour = timeParts[0].toInt()
                val min = timeParts[1].toInt(); val sec = timeParts[2].toInt()
                if (parts.getOrNull(4) == "PM" && hour < 12) hour += 12
                if (parts.getOrNull(4) == "AM" && hour == 12) hour = 0
                LocalDateTime(year, month, day, hour, min, sec).toInstant(TimeZone.UTC)
            } catch (e2: Exception) {
                try {
                    val parts = dateStr.split(" "); val d = parts[0].split("-"); val t = parts[1].split(":")
                    LocalDateTime(d[0].toInt(), d[1].toInt(), d[2].toInt(), t[0].toInt(), t[1].toInt(), t[2].toInt()).toInstant(TimeZone.UTC)
                } catch (e3: Exception) { Instant.fromEpochMilliseconds(0) }
            }
        }
    }

    private fun parseBikesCsv(content: String): List<String> {
        val names = mutableListOf<String>(); val lines = splitCsvLines(content)
        if (lines.isEmpty()) return emptyList(); val header = parseCsvLine(lines[0])
        val nameIdx = header.indexOfFirst { it.contains("Bike Name", ignoreCase = true) }
        val brandIdx = header.indexOfFirst { it.contains("Bike Brand", ignoreCase = true) }
        val modelIdx = header.indexOfFirst { it.contains("Bike Model", ignoreCase = true) }
        for (i in 1 until lines.size) {
            val parts = parseCsvLine(lines[i])
            val name = parts.getOrNull(nameIdx)?.trim() ?: ""
            val brand = parts.getOrNull(brandIdx)?.trim() ?: ""
            val model = parts.getOrNull(modelIdx)?.trim() ?: ""
            if (name.isNotEmpty()) names.add(name)
            if (brand.isNotEmpty() || model.isNotEmpty()) names.add("$brand $model".trim())
        }
        return names
    }

    private fun parseShoesCsv(content: String): List<String> {
        val names = mutableListOf<String>(); val lines = splitCsvLines(content)
        if (lines.isEmpty()) return emptyList(); val header = parseCsvLine(lines[0])
        val nameIdx = header.indexOfFirst { it.contains("Shoe Name", ignoreCase = true) }
        val brandIdx = header.indexOfFirst { it.contains("Shoe Brand", ignoreCase = true) }
        val modelIdx = header.indexOfFirst { it.contains("Shoe Model", ignoreCase = true) }
        for (i in 1 until lines.size) {
            val parts = parseCsvLine(lines[i])
            val name = parts.getOrNull(nameIdx)?.trim() ?: ""
            val brand = parts.getOrNull(brandIdx)?.trim() ?: ""
            val model = parts.getOrNull(modelIdx)?.trim() ?: ""
            if (name.isNotEmpty()) names.add(name)
            if (brand.isNotEmpty() || model.isNotEmpty()) names.add("$brand $model".trim())
        }
        return names
    }

    private fun parseCsvLine(line: String): List<String> {
        val res = mutableListOf<String>(); val cur = StringBuilder(); var inQ = false; var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\"') { if (inQ && i + 1 < line.length && line[i + 1] == '\"') { cur.append('\"'); i++ } else inQ = !inQ }
            else if (c == ',' && !inQ) { res.add(cur.toString().trim()); cur.setLength(0) }
            else cur.append(c); i++
        }
        res.add(cur.toString().trim()); return res
    }

    private data class ActivityExport(
        val id: Long,
        val name: String,
        val startDate: Instant,
        val type: String?,
        val gearId: String?,
        val gearName: String?,
        val filename: String,
        val distance: Float
    )
}
