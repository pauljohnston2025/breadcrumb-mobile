package com.paul.infrastructure.service

import com.paul.domain.StravaActivity
import com.paul.domain.StravaGear
import com.paul.domain.StravaMap
import com.paul.domain.StravaStreamEntity
import com.paul.infrastructure.dao.StravaDao
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
                val bikes = mutableListOf<StravaGear>()
                val shoes = mutableListOf<StravaGear>()

                fun getEntry(suffix: String) = entryMap[suffix.lowercase()] ?: suffixMap[suffix.substringAfterLast("/").lowercase()]

                getEntry("activities.csv")?.let { activitiesMetadata.addAll(parseActivitiesCsv(it.readBytes().decodeToString())) }
                getEntry("bikes.csv")?.let { bikes.addAll(parseBikesCsv(it.readBytes().decodeToString())) }
                getEntry("shoes.csv")?.let { shoes.addAll(parseShoesCsv(it.readBytes().decodeToString())) }

                if (bikes.isNotEmpty() || shoes.isNotEmpty()) dao.insertGear(bikes + shoes)

                if (activitiesMetadata.isEmpty()) {
                    Napier.w("No activities found in metadata!", tag = TAG)
                    return@use
                }

                val totalExisting = dao.size()
                val newActivitiesMeta = if (totalExisting == 0L) activitiesMetadata else activitiesMetadata.filter { dao.getActivity(it.id) == null }

                val totalToImport = newActivitiesMeta.size
                if (totalToImport == 0) {
                    onProgress("No new activities to import.")
                    return@use
                }

                val gearMap = (bikes + shoes).associate { it.name to it.id }
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

                            val activity = StravaActivity(
                                id = activityMeta.id,
                                name = activityMeta.name,
                                startDate = activityMeta.startDate,
                                type = activityMeta.type,
                                gearId = activityMeta.gearName?.let { gn -> 
                                    gearMap.entries.find { gn.contains(it.key, ignoreCase = true) }?.value 
                                },
                                map = summaryPolyline?.let { StravaMap(it) },
                                distance = activityMeta.distance
                            )
                            
                            // Insert one by one for lively progress and immediate availability
                            dao.insertActivities(listOf(activity))
                            if (points.isNotEmpty()) {
                                dao.insertStream(StravaStreamEntity(activityMeta.id, points))
                            }
                            
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
        val gearIdx = header.indexOfFirst { it.contains("Activity Gear", ignoreCase = true) }
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
                        parts[distanceIdx].trim().toFloatOrNull() ?: 0f
                    } else 0f

                    activities.add(
                        ActivityExport(
                            id,
                            parts.getOrNull(nameIdx) ?: "Imported Activity",
                            parseStravaDate(parts.getOrNull(dateIdx) ?: ""),
                            parts.getOrNull(typeIdx),
                            parts.getOrNull(gearIdx),
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

    private fun parseBikesCsv(content: String): List<StravaGear> {
        val gear = mutableListOf<StravaGear>(); val lines = content.lines()
        if (lines.isEmpty()) return emptyList(); val header = lines[0].split(",")
        val idIdx = header.indexOf("ID")
        val nameIdx = header.indexOf("Bike Name").let { if (it == -1) header.indexOf("Name") else it }
        val priIdx = header.indexOf("Primary")
        for (i in 1 until lines.size) {
            val parts = parseCsvLine(lines[i])
            val name = if (nameIdx != -1) parts.getOrNull(nameIdx)?.trim() ?: "" else ""
            if (name.isNotEmpty()) {
                val id = if (idIdx != -1 && idIdx < parts.size) parts[idIdx] else "b_${name.replace(" ", "_").lowercase()}"
                gear.add(StravaGear(id, name, parts.getOrNull(priIdx)?.lowercase() == "true", StravaGear.TYPE_BIKE))
            }
        }
        return gear
    }

    private fun parseShoesCsv(content: String): List<StravaGear> {
        val gear = mutableListOf<StravaGear>(); val lines = content.lines()
        if (lines.isEmpty()) return emptyList(); val header = lines[0].split(",")
        val idIdx = header.indexOf("ID")
        val brandIdx = header.indexOf("Brand"); val modelIdx = header.indexOf("Model")
        val descIdx = header.indexOf("Description"); val priIdx = header.indexOf("Primary")
        for (i in 1 until lines.size) {
            val parts = parseCsvLine(lines[i])
            val name = "${parts.getOrNull(brandIdx) ?: ""} ${parts.getOrNull(modelIdx) ?: ""} ${parts.getOrNull(descIdx) ?: ""}".trim().replace("\\s+".toRegex(), " ")
            if (name.isNotEmpty()) {
                val id = if (idIdx != -1 && idIdx < parts.size) parts[idIdx] else "s_${name.replace(" ", "_").lowercase()}"
                gear.add(StravaGear(id, name, parts.getOrNull(priIdx)?.lowercase() == "true", StravaGear.TYPE_SHOE))
            }
        }
        return gear
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
        val gearName: String?,
        val filename: String,
        val distance: Float
    )
}
