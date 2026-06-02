package com.paul.infrastructure.service

import com.garmin.fit.Decode
import com.garmin.fit.FitMessages
import com.garmin.fit.MesgBroadcaster
import com.garmin.fit.MesgListener
import com.garmin.fit.RecordMesg
import com.paul.domain.GpxRoute
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
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

class StravaImportService(
    private val dao: StravaDao,
    private val fileHelper: IFileHelper,
    private val gpxFileLoader: IGpxFileLoader,
) {
    private val TAG = "StravaImportService"

    suspend fun importFromZip(zipUri: String, onProgress: (String) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val activities = mutableListOf<ActivityExport>()
            val bikes = mutableListOf<StravaGear>()
            val shoes = mutableListOf<StravaGear>()
            
            // First pass: Read all CSVs in a single scan
            onProgress("Scanning metadata...")
            var foundActivities = false
            var foundBikes = false
            var foundShoes = false

            processZip(zipUri, onScan = { count ->
                val status = StringBuilder("Scanning metadata...\n")
                status.append(if (foundActivities) "[X] Activities\n" else "[ ] Activities\n")
                status.append(if (foundBikes) "[X] Bikes\n" else "[ ] Bikes\n")
                status.append(if (foundShoes) "[X] Shoes\n" else "[ ] Shoes\n")
                status.append("Scanned $count files")
                onProgress(status.toString())
            }) { entryName, inputStream ->
                when (entryName) {
                    "activities.csv" -> {
                        onProgress("Reading activities.csv...")
                        activities.addAll(parseActivitiesCsv(inputStream))
                        foundActivities = true
                    }
                    "bikes.csv" -> {
                        onProgress("Reading bikes.csv...")
                        bikes.addAll(parseBikesCsv(inputStream))
                        foundBikes = true
                    }
                    "shoes.csv" -> {
                        onProgress("Reading shoes.csv...")
                        shoes.addAll(parseShoesCsv(inputStream))
                        foundShoes = true
                    }
                }
                // Stop first pass only if we found all three
                foundActivities && foundBikes && foundShoes
            }

            if (bikes.isNotEmpty() || shoes.isNotEmpty()) {
                dao.insertGear(bikes + shoes)
            }

            onProgress("Found ${activities.size} activities. Checking database...")

            val totalExisting = dao.size()
            val newActivities = if (totalExisting == 0L) {
                activities
            } else {
                // Bulk check for existing activities to avoid thousands of individual queries
                activities.filter { dao.getActivity(it.id) == null }
            }

            val totalToImport = newActivities.size
            if (totalToImport == 0) {
                onProgress("No new activities to import.")
                return@withContext Result.success(Unit)
            }

            onProgress("Importing $totalToImport new activities...")

            // Create a map for O(1) lookup during second pass
            val activityMap = newActivities.associateBy { it.filename }
            val gearMap = (bikes + shoes).associate { it.name to it.id }
            var importedCount = 0

            // Second pass: Read activity files
            processZip(zipUri, onScan = { count ->
                if (count % 50 == 0) {
                    onProgress("Importing: Found $importedCount/$totalToImport. Scanned $count files in ZIP...")
                }
            }) { entryName, inputStream ->
                val activityExport = activityMap[entryName]
                if (activityExport != null) {
                    try {
                        val entryBytes = inputStream.readBytes()
                        var stream: InputStream = ByteArrayInputStream(entryBytes)
                        if (entryName.endsWith(".gz")) {
                            stream = java.util.zip.GZIPInputStream(stream)
                        }

                        val points = if (entryName.contains(".fit")) {
                            parseFitPoints(stream)
                        } else if (entryName.contains(".gpx")) {
                            val bytes = stream.readBytes()
                            val gpxRoute = gpxFileLoader.loadGpxFromBytes(bytes)
                            gpxRoute.getPoints() ?: emptyList()
                        } else {
                            emptyList()
                        }
                        
                        if (points.isNotEmpty()) {
                            val summaryPoints = Route.summary(points)
                            val summaryPolyline = StravaMap.encodePolyline(summaryPoints)

                            val activity = StravaActivity(
                                id = activityExport.id,
                                name = activityExport.name,
                                startDate = activityExport.startDate,
                                type = activityExport.type,
                                gearId = activityExport.gearName?.let { gn ->
                                    // Match gear name that is contained in the activity's gear field
                                    gearMap.entries.find { gn.contains(it.key, ignoreCase = true) }?.value
                                },
                                map = StravaMap(summaryPolyline)
                            )
                            dao.insertActivities(listOf(activity))
                            dao.insertStream(StravaStreamEntity(activityExport.id, points))
                            importedCount++
                            onProgress("Imported $importedCount / $totalToImport: ${activity.name}")
                        }
                    } catch (e: Exception) {
                        Napier.e("Failed to import activity $entryName", e, tag = TAG)
                    }
                }
                // Continue until we've imported all new activities
                importedCount >= totalToImport
            }

            onProgress("Finished! Imported $importedCount activities.")
            Result.success(Unit)
        } catch (e: Exception) {
            Napier.e("Import failed", e, tag = TAG)
            Result.failure(e)
        }
    }

    private suspend fun processZip(
        zipUri: String, 
        onScan: (suspend (Int) -> Unit)? = null,
        block: suspend (String, InputStream) -> Boolean
    ) {
        fileHelper.openInputStream(zipUri)?.use { fis ->
            ZipInputStream(fis).use { zis ->
                var entry = zis.nextEntry
                var count = 0
                while (entry != null) {
                    withContext(Dispatchers.IO) {
                        ensureActive()
                    }
                    count++
                    onScan?.invoke(count)

                    var shouldStop = false
                    if (!entry.isDirectory) {
                        shouldStop = block(entry.name, zis)
                    }
                    zis.closeEntry()
                    if (shouldStop) break
                    entry = zis.nextEntry
                }
            }
        }
    }

    private fun parseActivitiesCsv(inputStream: InputStream): List<ActivityExport> {
        val activities = mutableListOf<ActivityExport>()
        val reader = BufferedReader(InputStreamReader(inputStream))
        val header = reader.readLine()?.split(",") ?: return emptyList()
        
        val idIdx = header.indexOf("Activity ID")
        val dateIdx = header.indexOf("Activity Date")
        val nameIdx = header.indexOf("Activity Name")
        val typeIdx = header.indexOf("Activity Type")
        val gearIdx = header.indexOf("Activity Gear")
        val filenameIdx = header.indexOf("Filename")

        if (idIdx == -1 || filenameIdx == -1) return emptyList()

        var line = reader.readLine()
        while (line != null) {
            val parts = parseCsvLine(line)
            if (parts.size > idIdx && parts.size > filenameIdx) {
                try {
                    val id = parts[idIdx].toLong()
                    val filename = parts[filenameIdx]
                    val dateStr = parts.getOrNull(dateIdx) ?: ""
                    val name = parts.getOrNull(nameIdx) ?: "Imported Activity"
                    val type = parts.getOrNull(typeIdx)
                    val gearName = parts.getOrNull(gearIdx)
                    
                    val startDate = parseStravaDate(dateStr)

                    activities.add(ActivityExport(id, name, startDate, type, gearName, filename))
                } catch (e: Exception) {
                    // skip invalid lines
                }
            }
            line = reader.readLine()
        }
        return activities
    }

    private fun parseStravaDate(dateStr: String): Instant {
        return try {
            Instant.parse(dateStr)
        } catch (e: Exception) {
            try {
                // Try format: Apr 14, 2024, 8:43:08 AM
                val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                val parts = dateStr.replace(",", "").split(" ")
                // Apr, 14, 2024, 8:43:08, AM
                val month = months.indexOf(parts[0]) + 1
                val day = parts[1].toInt()
                val year = parts[2].toInt()
                val timeParts = parts[3].split(":")
                var hour = timeParts[0].toInt()
                val min = timeParts[1].toInt()
                val sec = timeParts[2].toInt()
                val amPm = parts.getOrNull(4)
                
                if (amPm == "PM" && hour < 12) hour += 12
                if (amPm == "AM" && hour == 12) hour = 0
                
                LocalDateTime(year, month, day, hour, min, sec).toInstant(TimeZone.UTC)
            } catch (e2: Exception) {
                try {
                    // Try format: 2024-04-14 08:43:08
                    val parts = dateStr.split(" ")
                    val dateParts = parts[0].split("-")
                    val timeParts = parts[1].split(":")
                    LocalDateTime(
                        dateParts[0].toInt(), dateParts[1].toInt(), dateParts[2].toInt(),
                        timeParts[0].toInt(), timeParts[1].toInt(), timeParts[2].toInt()
                    ).toInstant(TimeZone.UTC)
                } catch (e3: Exception) {
                    Instant.fromEpochMilliseconds(0)
                }
            }
        }
    }

    private fun parseBikesCsv(inputStream: InputStream): List<StravaGear> {
        val gear = mutableListOf<StravaGear>()
        val reader = BufferedReader(InputStreamReader(inputStream))
        val header = reader.readLine()?.split(",") ?: return emptyList()
        
        // standard Strava export: Name,Description,Frame Type,Wheel Size,Retired,Primary,ID
        // user provided: Bike Name, Bike Brand, Bike Model, Bike Default Sport Types
        val idIdx = header.indexOf("ID")
        val nameIdx = header.indexOf("Bike Name").let { if (it == -1) header.indexOf("Name") else it }
        val primaryIdx = header.indexOf("Primary")

        var line = reader.readLine()
        while (line != null) {
            val parts = parseCsvLine(line)
            val name = if (nameIdx != -1) parts.getOrNull(nameIdx)?.trim() ?: "" else ""
            
            if (name.isNotEmpty()) {
                val id = if (idIdx != -1 && idIdx < parts.size) parts[idIdx] else "b_${name.replace(" ", "_").lowercase()}"
                val primary = if (primaryIdx != -1) parts.getOrNull(primaryIdx)?.lowercase() == "true" else false
                gear.add(StravaGear(id, name, primary, StravaGear.TYPE_BIKE))
            }
            line = reader.readLine()
        }
        return gear
    }

    private fun parseShoesCsv(inputStream: InputStream): List<StravaGear> {
        val gear = mutableListOf<StravaGear>()
        val reader = BufferedReader(InputStreamReader(inputStream))
        val header = reader.readLine()?.split(",") ?: return emptyList()
        
        // standard Strava export: Brand,Model,Description,Retired,Primary,ID
        // user provided: Shoe Name, Shoe Brand, Shoe Model, Shoe Default Sport Types
        val idIdx = header.indexOf("ID")
        val brandIdx = header.indexOf("Shoe Brand").let { if (it == -1) header.indexOf("Brand") else it }
        val modelIdx = header.indexOf("Shoe Model").let { if (it == -1) header.indexOf("Model") else it }
        val descIdx = header.indexOf("Shoe Name").let { if (it == -1) header.indexOf("Description") else it }
        val primaryIdx = header.indexOf("Primary")

        // If ID is missing (unlikely in real export but for safety), use a generated one based on name
        var line = reader.readLine()
        while (line != null) {
            val parts = parseCsvLine(line)
            val brand = if (brandIdx != -1) parts.getOrNull(brandIdx) ?: "" else ""
            val model = if (modelIdx != -1) parts.getOrNull(modelIdx) ?: "" else ""
            val desc = if (descIdx != -1) parts.getOrNull(descIdx) ?: "" else ""
            
            val name = "$brand $model $desc".trim().replace("\\s+".toRegex(), " ")
            if (name.isNotEmpty()) {
                val id = if (idIdx != -1 && idIdx < parts.size) parts[idIdx] else "g_${name.replace(" ", "_").lowercase()}"
                val primary = if (primaryIdx != -1) parts.getOrNull(primaryIdx)?.lowercase() == "true" else false
                gear.add(StravaGear(id, name, primary, StravaGear.TYPE_SHOE))
            }
            line = reader.readLine()
        }
        return gear
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        for (c in line) {
            if (c == '\"') {
                inQuotes = !inQuotes
            } else if (c == ',' && !inQuotes) {
                result.add(cur.toString().trim())
                cur.setLength(0)
            } else {
                cur.append(c)
            }
        }
        result.add(cur.toString().trim())
        return result
    }

    private fun parseFitPoints(inputStream: InputStream): List<Point> {
        val points = mutableListOf<Point>()
        val decode = Decode()
        val mesgBroadcaster = MesgBroadcaster(decode)
        
        mesgBroadcaster.addListener(object : RecordMesgListener {
            override fun onMesg(mesg: RecordMesg) {
                val lat = mesg.positionLat
                val lng = mesg.positionLong
                val alt = mesg.altitude
                
                if (lat != null && lng != null) {
                    // FIT coordinates are in semicircles. Convert to degrees.
                    // degrees = semicircles * (180 / 2^31)
                    val latDeg = lat.toFloat() * (180f / 2147483648f)
                    val lngDeg = lng.toFloat() * (180f / 2147483648f)
                    points.add(Point(latDeg, lngDeg, alt ?: 0f))
                }
            }
        })

        try {
            // Read all bytes from the provided stream (which may be a GZIPInputStream)
            val bytes = inputStream.readBytes()
            decode.read(ByteArrayInputStream(bytes), mesgBroadcaster, mesgBroadcaster)
        } catch (e: Exception) {
            Napier.e("FIT parse error", e, tag = TAG)
        }
        
        return points
    }

    private interface RecordMesgListener : MesgListener {
        fun onMesg(mesg: RecordMesg)
        override fun onMesg(mesg: com.garmin.fit.Mesg) {
            if (mesg is RecordMesg) onMesg(mesg)
        }
    }

    private data class ActivityExport(
        val id: Long,
        val name: String,
        val startDate: Instant,
        val type: String?,
        val gearName: String?,
        val filename: String
    )
}
