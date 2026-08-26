package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.network.WebViewResolver
import org.jsoup.Jsoup
import java.net.URI

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

    private val browserHeaders = mapOf(
        "User-Agent"      to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer"         to "$mainUrl/",
        "Origin"          to mainUrl,
        "Accept"          to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language" to "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7"
    )

    // 1. MAIN PAGE: List all live channels from the homepage tabs
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val channelList = mutableListOf<SearchResponse>()
        val htmlResponse = app.get("$mainUrl/", headers = browserHeaders).text
        val document = Jsoup.parse(htmlResponse)

        val selector = when (request.data) {
            "24-7"    -> "#24-7-tab a.channel-item"
            "matches" -> "#matches-tab a.channel-item"
            else      -> "a.channel-item"
        }

        var elements = document.select(selector)
        if (elements.isEmpty()) {
            elements = document.select("a.channel-item")
        }

        elements.forEach { element ->
            val title      = element.select(".channel-name").text().trim()
            val channelUrl = element.attr("href")
            if (title.isNotEmpty() && channelUrl.isNotEmpty()) {
                channelList.add(
                    newLiveSearchResponse(
                        name = title,
                        url  = fixUrl(channelUrl),
                        type = TvType.Live
                    ) {
                        this.posterUrl = defaultPoster
                    }
                )
            }
        }
        return newHomePageResponse(request, channelList)
    }

    // 2. SEARCH
    override suspend fun search(query: String): List<SearchResponse> {
        val searchList   = mutableListOf<SearchResponse>()
        val htmlResponse = app.get("$mainUrl/", headers = browserHeaders).text
        val document     = Jsoup.parse(htmlResponse)

        document.select("a.channel-item").forEach { element ->
            val title      = element.select(".channel-name").text().trim()
            val channelUrl = element.attr("href")
            if (title.isNotEmpty() && title.lowercase().contains(query.lowercase())) {
                searchList.add(
                    newLiveSearchResponse(
                        name = title,
                        url  = fixUrl(channelUrl),
                        type = TvType.Live
                    ) {
                        this.posterUrl = defaultPoster
                    }
                )
            }
        }
        return searchList
    }

    // 3. LOAD: Return a LiveStreamLoadResponse pointing to the channel.html page URL
    override suspend fun load(url: String): LoadResponse {
        val htmlResponse = app.get(url, headers = browserHeaders).text
        val document     = Jsoup.parse(htmlResponse)
        val title        = document.title().trim().ifEmpty { "Canlı Kanal" }
        val poster       = document.select("meta[property=og:image]").attr("content").ifEmpty { defaultPoster }

        return newLiveStreamLoadResponse(
            name    = title,
            url     = url,
            dataUrl = url
        ) {
            this.posterUrl = poster
            this.plot      = "İnat TV – Canlı yayın."
        }
    }

    // 4. LOAD LINKS: Use WebViewResolver to intercept the real HLS .m3u8 stream
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        // The channel page (e.g. /channel.html?id=patron) loads an iframe that
        // sets up a Clappr player pointing to an HLS stream on the CDN.
        // We use WebViewResolver to intercept any .m3u8 request fired by the page.

        val resolvedUrl = WebViewResolver(
            interceptUrl = Regex("""\.m3u8""")
        ).resolveUsingWebView(
            requestCreator(method = "GET", url = data, referer = mainUrl, headers = browserHeaders)
        ).first?.url

        if (!resolvedUrl.isNullOrEmpty() && !resolvedUrl.contains("video.bsky.app")) {
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name   = name,
                    url    = resolvedUrl,
                    type   = ExtractorLinkType.M3U8
                ) {
                    this.referer = mainUrl
                    this.quality = Qualities.P1080.value
                }
            )
            return true
        }

        return false
    }
}
