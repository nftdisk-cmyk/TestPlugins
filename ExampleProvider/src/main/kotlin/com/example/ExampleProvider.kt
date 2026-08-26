package com.example

import android.net.Uri
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.network.requestCreator
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

    private val browserHeaders = mapOf(
        "User-Agent"      to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer"         to "$mainUrl/",
        "Origin"          to mainUrl,
        "Accept"          to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language" to "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7"
    )

    /**
     * Helper to fetch HTML content. If Cloudflare IUAM/Turnstile challenge is detected (403/503/Just a moment),
     * it automatically triggers CloudStream's WebViewResolver to bypass the anti-bot protection.
     */
    private suspend fun fetchHtml(url: String): String {
        return try {
            val response = app.get(url, headers = browserHeaders)
            val body = response.text
            val isChallenge = response.code in listOf(403, 503) ||
                    body.contains("Just a moment...", ignoreCase = true) ||
                    body.contains("cf-chl-bypass", ignoreCase = true) ||
                    body.contains("cf-turnstile", ignoreCase = true)

            if (isChallenge) {
                // Cloudflare bypass via headless WebViewResolver
                val webViewResponse = WebViewResolver(
                    interceptUrl = Regex("""$mainUrl.*""")
                ).resolveUsingWebView(
                    requestCreator(method = "GET", url = url, referer = "$mainUrl/", headers = browserHeaders)
                )
                webViewResponse.first?.body ?: body
            } else {
                body
            }
        } catch (e: Exception) {
            try {
                val webViewResponse = WebViewResolver(
                    interceptUrl = Regex("""$mainUrl.*""")
                ).resolveUsingWebView(
                    requestCreator(method = "GET", url = url, referer = "$mainUrl/", headers = browserHeaders)
                )
                webViewResponse.first?.body ?: ""
            } catch (e2: Exception) {
                ""
            }
        }
    }

    // 1. MAIN PAGE: List all live channels from the homepage tabs
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val channelList = mutableListOf<SearchResponse>()
        val htmlResponse = fetchHtml("$mainUrl/")
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

    // 2. SEARCH: Search across all channels
    override suspend fun search(query: String): List<SearchResponse> {
        val searchList   = mutableListOf<SearchResponse>()
        val htmlResponse = fetchHtml("$mainUrl/")
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

    // 3. LOAD: Return live stream details for UI
    override suspend fun load(url: String): LoadResponse {
        val htmlResponse = fetchHtml(url)
        val document     = Jsoup.parse(htmlResponse)
        val title        = document.title().trim().ifEmpty { "Canlı Kanal" }
        val poster       = document.select("meta[property=og:image]").attr("content").ifEmpty { defaultPoster }

        return newLiveStreamLoadResponse(
            name    = title,
            url     = url,
            dataUrl = url
        ) {
            this.posterUrl = poster
            this.plot      = "İnat TV – 7/24 Kesintisiz Canlı TV yayını."
        }
    }

    // 4. LOAD LINKS: Extract live HLS stream URLs with Cloudflare & Bot bypass
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val pageHtml = fetchHtml(data)

        val streamHeaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Referer"    to "$mainUrl/",
            "Origin"     to mainUrl,
            "Accept"     to "*/*"
        )

        var foundLink = false

        // 1. Direct Extraction via page CONFIG script (Fast Path)
        val baseUrlRegex = Regex("""baseUrl\s*:\s*['"]([^'"]+)['"]""")
        val baseUrl = baseUrlRegex.find(pageHtml)?.groupValues?.get(1) ?: "https://2i4.d72577a9dd0ec71.cfd/"
        val channelId = Uri.parse(data).getQueryParameter("id")

        if (!channelId.isNullOrEmpty()) {
            val primaryStreamUrl = if (channelId.startsWith("http://") || channelId.startsWith("https://")) {
                channelId
            } else {
                "${baseUrl.trimEnd('/')}/$channelId/mono.m3u8"
            }

            callback.invoke(
                newExtractorLink(
                    source  = name,
                    name    = "$name - Canlı Yayın",
                    url     = primaryStreamUrl,
                    type    = ExtractorLinkType.M3U8
                ) {
                    this.referer = "$mainUrl/"
                    this.headers = streamHeaders
                    this.quality = Qualities.P1080.value
                }
            )

            val directStreamUrl = "https://d72577a9dd0ec71.cfd/$channelId/mono.m3u8"
            if (directStreamUrl != primaryStreamUrl) {
                callback.invoke(
                    newExtractorLink(
                        source  = name,
                        name    = "$name - Yedek Sunucu",
                        url     = directStreamUrl,
                        type    = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "$mainUrl/"
                        this.headers = streamHeaders
                        this.quality = Qualities.P1080.value
                    }
                )
            }

            foundLink = true
        }

        // 2. Cloudflare / Anti-Bot Bypass via WebViewResolver (Full Interceptor)
        try {
            val resolvedUrl = WebViewResolver(
                interceptUrl = Regex("""\.m3u8""")
            ).resolveUsingWebView(
                requestCreator(method = "GET", url = data, referer = "$mainUrl/", headers = browserHeaders)
            ).first?.url

            if (!resolvedUrl.isNullOrEmpty() && !resolvedUrl.contains("video.bsky.app") && !resolvedUrl.contains("preroll")) {
                callback.invoke(
                    newExtractorLink(
                        source  = name,
                        name    = "$name - Canlı Yayın (Bypass)",
                        url     = resolvedUrl,
                        type    = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "$mainUrl/"
                        this.headers = streamHeaders
                        this.quality = Qualities.P1080.value
                    }
                )
                foundLink = true
            }
        } catch (e: Exception) {
            // Ignore WebView interceptor failure if direct link was already found
        }

        // 3. Fallback regex extraction from HTML
        if (!foundLink && pageHtml.isNotEmpty()) {
            val m3u8Regex = Regex("""https?://[^\s"'<>]+?\.m3u8[^\s"'<>]*""")
            val matchedUrl = m3u8Regex.findAll(pageHtml)
                .map { it.value }
                .firstOrNull { !it.contains("video.bsky.app") && !it.contains("preroll") }

            if (!matchedUrl.isNullOrEmpty()) {
                callback.invoke(
                    newExtractorLink(
                        source  = name,
                        name    = "$name - Canlı Yayın",
                        url     = matchedUrl,
                        type    = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "$mainUrl/"
                        this.headers = streamHeaders
                        this.quality = Qualities.P1080.value
                    }
                )
                foundLink = true
            }
        }

        return foundLink
    }
}
