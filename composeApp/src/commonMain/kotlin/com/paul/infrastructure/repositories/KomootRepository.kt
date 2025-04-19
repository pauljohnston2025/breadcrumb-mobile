package com.paul.infrastructure.repositories

import com.paul.domain.CoordinatesRoute
import com.paul.infrastructure.web.KtorClient
import com.paul.protocol.todevice.Point
import io.github.aakira.napier.Napier
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.toByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
data class KomootTourEmbedded(val coordinates: KomootTourCoordinates)

@Serializable
data class KomootTourCoordinates(val items: List<KomootTourCoordinate>)

@Serializable
data class KomootTourCoordinate(
    val lat: Float,
    val lng: Float,
    val alt: Float,
    val t: Int,
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
        return CoordinatesRoute(name, coordinates.map { Point(it.lat, it.lng, it.alt) })
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
            val jsonString = Json {
                isLenient = true
            }.decodeFromString<String>(jsonStringEscaped)

            val root = Json { ignoreUnknownKeys = true }.decodeFromString<KomootSetPropsRoot>(
                jsonString
            )
            Napier.d("$root")
            return root
        }

        return null
    }
}