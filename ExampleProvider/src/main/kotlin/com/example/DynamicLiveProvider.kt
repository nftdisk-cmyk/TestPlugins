package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

data class PatronChannel(
    val Mac: String = "",
    val Logo: String = "",
    val URL: String = ""
)

data class PatronMatch(
    val HomeTeam: String = "",
    val AwayTeam: String = "",
    val HomeLogo: String = "",
    val AwayLogo: String = "",
    val Time: String = "",
    val URL: String = "",
    val type: String = "",
    val league: String = ""
)

data class PatronDomain(
    val baseurl: String = ""
)

class DynamicLiveProvider : MainAPI() {
    override var name = "Patron Live"
    override var mainUrl = "https://patronvip32.cfd"
    override var lang = "tr"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Live)

    override val mainPage = mainPageOf(
        Pair("channels",   "Canli TV Kanallari"),
        Pair("football",   "Futbol Maclari"),
        Pair("basketball", "Basketbol Maclari"),
        Pair("other",      "Diger Maclar")
    )

    companion object {
        private const val CHANNELS_API = "https://patronsports2.cfd/channels.php"
        private const val MATCHES_API  = "https://patronsports2.cfd/matches.php"
        private const val DOMAIN_API   = "https://patronsports2.cfd/domain.php"
        private const val SITE_BASE    = "https://patronvip32.cfd"
        private const val UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7 Build/TQ3A.230901.001; wv) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 " +
            "Chrome/120.0.6099.210 Mobile Safari/537.36"

        @Volatile private var cachedBaseUrl: String? = null

        suspend fun getStreamBaseUrl(): String {
            cachedBaseUrl?.let { return it }
            return try {
                val domain = parseJson<PatronDomain>(app.get(DOMAIN_API).text)
                domain.baseurl.also { cachedBaseUrl = it }
            } catch (e: Exception) {
                "https://2i4.d72577a9dd0ec71.cfd/"
            }
        }

        fun buildStreamUrl(baseUrl: String, channelPath: String): String {
            val id = channelPath.substringAfter("id=").trim()
            return if (id.startsWith("http://") || id.startsWith("https://")) {
                id
            } else {
                "${baseUrl.trimEnd('/')}/$id/mono.m3u8"
            }
        }

        fun resolveLogoUrl(path: String): String {
            if (path.startsWith("http")) return path
            return "$SITE_BASE/${path.trimStart('/')}"
        }
    }

    private fun headers() = mapOf(
        "User-Agent" to UA,
        "Referer"    to "$SITE_BASE/",
        "Origin"     to SITE_BASE
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val baseUrl = getStreamBaseUrl()
        val list = mutableListOf<SearchResponse>()

        if (request.data == "channels") {
            val channels = parseJson<List<PatronChannel>>(app.get(CHANNELS_API, headers = headers()).text)
            channels.forEach { ch ->
                list.add(newLiveSearchResponse(
                    name = ch.Mac,
                    url  = buildStreamUrl(baseUrl, ch.URL),
                    type = TvType.Live
                ) { this.posterUrl = resolveLogoUrl(ch.Logo) })
            }
        } else {
            val matches = parseJson<List<PatronMatch>>(app.get(MATCHES_API, headers = headers()).text)
            val filtered = when (request.data) {
                "football"   -> matches.filter { it.type == "football" }
                "basketball" -> matches.filter { it.type == "basketball" }
                else         -> matches.filter { it.type !in listOf("football", "basketball") }
            }
            filtered.forEach { m ->
                list.add(newLiveSearchResponse(
                    name = "${m.HomeTeam} - ${m.AwayTeam}  [${m.Time}]",
                    url  = buildStreamUrl(baseUrl, m.URL),
                    type = TvType.Live
                ) { this.posterUrl = m.HomeLogo })
            }
        }

        return newHomePageResponse(request, list)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val baseUrl = getStreamBaseUrl()
        val q = query.lowercase().trim()
        val results = mutableListOf<SearchResponse>()

        parseJson<List<PatronChannel>>(app.get(CHANNELS_API, headers = headers()).text)
            .filter { it.Mac.lowercase().contains(q) }
            .forEach { ch ->
                results.add(newLiveSearchResponse(
                    name = ch.Mac,
                    url  = buildStreamUrl(baseUrl, ch.URL),
                    type = TvType.Live
                ) { this.posterUrl = resolveLogoUrl(ch.Logo) })
            }

        parseJson<List<PatronMatch>>(app.get(MATCHES_API, headers = headers()).text)
            .filter {
                it.HomeTeam.lowercase().contains(q) ||
                it.AwayTeam.lowercase().contains(q) ||
                it.league.lowercase().contains(q)
            }
            .forEach { m ->
                results.add(newLiveSearchResponse(
                    name = "${m.HomeTeam} - ${m.AwayTeam}  [${m.Time}]",
                    url  = buildStreamUrl(baseUrl, m.URL),
                    type = TvType.Live
                ) { this.posterUrl = m.HomeLogo })
            }

        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val title = url.substringAfterLast("/").substringBefore("/mono.m3u8")
            .replace("-", " ").uppercase().ifEmpty { "Canli Yayin" }
        return newLiveStreamLoadResponse(
            name    = title,
            url     = url,
            dataUrl = url
        ) { this.plot = "Patron Sports Canli Yayin" }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (!data.contains(".m3u8")) return false
        callback.invoke(
            ExtractorLink(
                source  = this.name,
                name    = this.name,
                url     = data,
                referer = "$SITE_BASE/",
                quality = Qualities.Unknown.value,
                isM3u8  = true,
                headers = mapOf(
                    "User-Agent" to UA,
                    "Referer"    to "$SITE_BASE/",
                    "Origin"     to SITE_BASE
                )
            )
        )
        return true
    }
}
