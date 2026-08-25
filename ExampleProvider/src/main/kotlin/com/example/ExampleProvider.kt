package com.example

import android.net.Uri
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.Jsoup

class ExampleProvider : MainAPI() {
    override var mainUrl = "https://inattv1321.xyz"
    override var name = "İnat TV"
    override val supportedTypes = setOf(TvType.Live)
    override var lang = "tr"
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        Pair("24-7", "7/24 Canlı TV"),
        Pair("matches", "Canlı Maçlar"),
        Pair("all", "Tüm Kanallar")
    )

    private val defaultPoster = "https://upload.wikimedia.org/wikipedia/commons/2/2f/Korduene_Logo.png"

    private val requestHeaders = mapOf(
        "User-Agent"      to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer"         to "$mainUrl/",
        "X-Requested-With" to "XMLHttpRequest",
        "Accept"          to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.9,tr;q=0.8"
    )

    // 1. MAIN PAGE: List all live channels from the homepage tabs
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val channelList = mutableListOf<SearchResponse>()
        val htmlResponse = app.get("$mainUrl/", headers = requestHeaders).text
        val document = Jsoup.parse(htmlResponse)

        val selector = when (request.data) {
            "24-7" -> "#24-7-tab a.channel-item"
            "matches" -> "#matches-tab a.channel-item"
            else -> "a.channel-item"
        }

        var elements = document.select(selector)
        if (elements.isEmpty()) {
            elements = document.select("a.channel-item")
        }

        elements.forEach { element ->
            val title = element.select(".channel-name").text().trim()
            val channelUrl = element.attr("href")

            if (title.isNotEmpty() && channelUrl.isNotEmpty()) {
                channelList.add(
                    newLiveSearchResponse(
                        name = title,
                        url = fixUrl(channelUrl),
                        type = TvType.Live
                    ) {
                        this.posterUrl = defaultPoster
                    }
                )
            }
        }
        return newHomePageResponse(request, channelList)
    }

    // 2. SEARCH: Handle search queries across all channels
    override suspend fun search(query: String): List<SearchResponse> {
        val searchList = mutableListOf<SearchResponse>()
        val htmlResponse = app.get("$mainUrl/", headers = requestHeaders).text
        val document = Jsoup.parse(htmlResponse)

        document.select("a.channel-item").forEach { element ->
            val title = element.select(".channel-name").text().trim()
            val channelUrl = element.attr("href")

            if (title.isNotEmpty() && title.lowercase().contains(query.lowercase())) {
                searchList.add(
                    newLiveSearchResponse(
                        name = title,
                        url = fixUrl(channelUrl),
                        type = TvType.Live
                    ) {
                        this.posterUrl = defaultPoster
                    }
                )
            }
        }
        return searchList
    }

    // 3. LOAD: Prepare the player UI when a channel is selected
    override suspend fun load(url: String): LoadResponse {
        val htmlResponse = app.get(url, headers = requestHeaders).text
        val document = Jsoup.parse(htmlResponse)
        val title = document.select("h1.entry-title, .channel-title").text().trim().ifEmpty { "Canlı Kanal" }
        val poster = document.select("meta[property=og:image]").attr("content").ifEmpty { defaultPoster }

        return newLiveStreamLoadResponse(
            name = title,
            url = url,
            dataUrl = url
        ) {
            this.posterUrl = poster
            this.plot = "$title – 7/24 Kesintisiz Canlı TV yayını."
        }
    }

    // 4. LOAD LINKS: Extract the real stream URL and filter out bet advertisements
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val pageHtml = app.get(data, headers = requestHeaders).text

        // 1. Extract dynamic baseUrl from page CONFIG script, e.g. "https://2i4.d72577a9dd0ec71.cfd/"
        val baseUrlRegex = Regex("""baseUrl\s*:\s*['"]([^'"]+)['"]""")
        val baseUrl = baseUrlRegex.find(pageHtml)?.groupValues?.get(1) ?: "https://2i4.d72577a9dd0ec71.cfd/"

        // 2. Extract channel id from page URL (e.g. ?id=patron)
        val channelId = Uri.parse(data).getQueryParameter("id")

        if (!channelId.isNullOrEmpty()) {
            val streamUrl = if (channelId.startsWith("http://") || channelId.startsWith("https://")) {
                channelId
            } else {
                "${baseUrl.trimEnd('/')}/$channelId/mono.m3u8"
            }

            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = streamUrl,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = "$mainUrl/"
                    this.headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                        "Referer" to "$mainUrl/",
                        "Origin" to mainUrl
                    )
                    this.quality = Qualities.P1080.value
                }
            )
            return true
        }

        // 3. Fallback: only match .m3u8 that are NOT bet preroll ad videos
        val m3u8Regex = Regex("""https?://[^\s"'<>]+?\.m3u8[^\s"'<>]*""")
        val matchedUrl = m3u8Regex.findAll(pageHtml)
            .map { it.value }
            .firstOrNull { !it.contains("video.bsky.app") && !it.contains("preroll") }

        if (!matchedUrl.isNullOrEmpty()) {
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = matchedUrl,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = "$mainUrl/"
                    this.headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                        "Referer" to "$mainUrl/",
                        "Origin" to mainUrl
                    )
                    this.quality = Qualities.P1080.value
                }
            )
            return true
        }

        return false
    }
}
