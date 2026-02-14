package com.paul.infrastructure.repositories

import com.paul.domain.CoordinatesRoute
import com.paul.domain.DirectionInfo
import com.paul.infrastructure.web.KtorClient
import com.paul.protocol.todevice.DirectionPoint
import com.paul.protocol.todevice.Point
import io.github.aakira.napier.Napier
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.toByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// there are other properties, we are just getting the ones we care about
@Serializable
data class KomootSetPropsRoot(val page: KomootPage)

@Serializable
data class KomootPage(val _embedded: KomootPageEmbedded)

@Serializable
data class KomootPageEmbedded(val tour: KomootTour)

@Serializable
data class KomootTour(
    val name: String,
    val _embedded: KomootTourEmbedded
)

@Serializable
data class KomootTourEmbedded(
    val coordinates: KomootTourCoordinates,
    val directions: KomootTourDirections
)
// there is also a directions array, but cannot see any way to link to coordinates (other than distance)
// unless the index corresponds to the coordinate index?
//"directions": {
//						"items": [
//							{
//								"index": 0,
//								"complex": false,
//								"distance": 469,
//								"type": "S",
//								"cardinal_direction": "E",
//								"change_way": false,
//								"last_similar": 0,
//								"street_name": "",
//								"way_type": "wt#hike_d2"
//							},
//							{
//								"index": 10,
//								"complex": false,
//								"distance": 262,
//								"type": "TR",
//								"cardinal_direction": "E",
//								"change_way": true,
//								"last_similar": 4,
//								"street_name": "Trail",
//								"way_type": "wt#hike_d2"
//							},
//							{
//								"index": 15,
//								"complex": false,
//								"distance": 74,
//								"type": "TSR",
//								"cardinal_direction": "SE",
//								"change_way": false,
//								"last_similar": 10,
//								"street_name": "Trail",
//								"way_type": "wt#way"
//							},

@Serializable
data class KomootTourCoordinates(val items: List<KomootTourCoordinate>)

@Serializable
data class KomootTourDirections(val items: List<KomootTourDirection>)

@Serializable
data class KomootTourCoordinate(
    val lat: Float,
    val lng: Float,
    val alt: Float,
    val t: Int,
)

// we do not really need the point, we just need the angle between the 2 segments
// turn right etc. This is mainly for offline hiking, not `turn left at street x`
// it's more just `turn left at next intersection`
// it there is a fork though, maybe we ned to say take the left most fork?
@Serializable
data class KomootTourDirection(
    val index: Int,
)

class KomootRepository {
    private val client = KtorClient.client

    suspend fun getRoute(url: String): CoordinatesRoute? {
        // inspired from https://github.com/DreiDe/komootGPXport
        val komootRoot = parseGpxRouteFromKomoot(url)
        if (komootRoot == null) {
            return null
        }

        val name = komootRoot.page._embedded.tour.name
        val coordinates = komootRoot.page._embedded.tour._embedded.coordinates.items
        val directions = komootRoot.page._embedded.tour._embedded.directions.items
        val points = coordinates.map { Point(it.lat, it.lng, it.alt) }

        return CoordinatesRoute(name, points, directions.map { DirectionInfo(it.index) })
    }

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    private suspend fun parseGpxRouteFromKomoot(komootUrl: String): KomootSetPropsRoot? {
        val response = client.get(komootUrl)
        if (response.status.isSuccess()) {
            val htmlString = response.bodyAsChannel().toByteArray().decodeToString()

            // this is incredibly flaky, but should work most of the time
            // the html page has a script tag containing kmtBoot.setProps("json with coordinates");
            // should parse using xml/html parser and find the whole string, but regex will work for now
            val regex = Regex("""kmtBoot\.setProps\((.*?)\);""", RegexOption.DOT_MATCHES_ALL)
            val matchResult = regex.find(htmlString)
            val jsonStringEscaped = matchResult?.groupValues?.getOrNull(1)
            if (jsonStringEscaped == null) {
                throw RuntimeException("could find json in komoot webpage")
            }
            val jsonString = json.decodeFromString<String>(jsonStringEscaped)

            val root = json.decodeFromString<KomootSetPropsRoot>(
                jsonString
            )
//            Napier.d("$root")
            return root
        }

        return null
    }
}