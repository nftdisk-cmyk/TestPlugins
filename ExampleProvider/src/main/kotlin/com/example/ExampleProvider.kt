package com.example

import android.net.Uri
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.Jsoup

class ExampleProvider : MainAPI() {
    override var mainUrl = "https://inattv1321.xyz"
    override var name = "Canlı TV"
    override val supportedTypes = setOf(TvType.Live)
    override var lang = "tr"
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        Pair("24-7", "7/24 Canlı TV"),
        Pair("matches", "Canlı Maçlar"),
        Pair("all", "Tüm Kanallar")
    )

    private val sheetUrl = "https://docs.google.com/spreadsheets/d/1IHYlgjzhLCX3MKhewg7FGTf_oIkNlXzl2ogYXkSRjFM/export?format=csv"
    private val defaultPoster = "https://upload.wikimedia.org/wikipedia/commons/2/2f/Korduene_Logo.png"

    private val browserHeaders = mapOf(
        "User-Agent"      to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept"          to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language" to "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7"
    )

    /**
     * Dynamically fetches source URLs/domains or custom channels from the Google Sheet CSV
     */
    private suspend fun getSources(): List<String> {
        return try {
            val response = app.get(sheetUrl).text
            response.lines()
                .map { it.trim().trim('"').trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
        } catch (e: Exception) {
            listOf(mainUrl)
        }
    }

    private suspend fun fetchHtml(url: String, domain: String): String {
        val headers = browserHeaders + mapOf(
            "Referer" to "$domain/",
            "Origin"  to domain
        )
        return try {
            val response = app.get(url, headers = headers)
            val body = response.text
            val isChallenge = response.code in listOf(403, 503) ||
                    body.contains("Just a moment...", ignoreCase = true) ||
                    body.contains("cf-chl-bypass", ignoreCase = true)

            if (isChallenge) {
                app.get(url, headers = headers, interceptor = WebViewResolver(Regex("""$domain.*"""))).text
            } else {
                body
            }
        } catch (e: Exception) {
            try {
                app.get(url, headers = headers, interceptor = WebViewResolver(Regex("""$domain.*"""))).text
            } catch (e2: Exception) {
                ""
            }
        }
    }

    // 1. MAIN PAGE: List all live channels from all active sources
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val channelList = mutableListOf<SearchResponse>()
        val sources = getSources()

        for (source in sources) {
            // Check if this source row is a direct CSV channel (Name, URL, Poster)
            if (source.contains(",") && !source.startsWith("http://") && !source.startsWith("https://")) {
                val parts = source.split(",").map { it.trim() }
                if (parts.size >= 2) {
                    val title = parts[0]
                    val streamUrl = parts[1]
                    val poster = if (parts.size >= 3 && parts[2].isNotEmpty()) parts[2] else defaultPoster
                    channelList.add(
                        newLiveSearchResponse(
                            name = title,
                            url  = streamUrl,
                            type = TvType.Live
                        ) {
                            this.posterUrl = poster
                        }
                    )
                }
                continue
            }

            // Otherwise treat source as a portal domain
            val domain = source.trimEnd('/')
            val htmlResponse = fetchHtml("$domain/", domain)
            if (htmlResponse.isEmpty()) continue

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
                    val fullUrl = if (channelUrl.startsWith("http")) channelUrl else "$domain/${channelUrl.trimStart('/')}"
                    if (channelList.none { it.url == fullUrl || it.name == title }) {
                        channelList.add(
                            newLiveSearchResponse(
                                name = title,
                                url  = fullUrl,
                                type = TvType.Live
                            ) {
                                this.posterUrl = defaultPoster
                            }
                        )
                    }
                }
            }
        }

        return newHomePageResponse(request, channelList)
    }

    // 2. SEARCH: Search across all active channels
    override suspend fun search(query: String): List<SearchResponse> {
        val searchList = mutableListOf<SearchResponse>()
        val sources = getSources()

        for (source in sources) {
            if (source.contains(",") && !source.startsWith("http://") && !source.startsWith("https://")) {
                val parts = source.split(",").map { it.trim() }
                if (parts.size >= 2 && parts[0].lowercase().contains(query.lowercase())) {
                    val title = parts[0]
                    val streamUrl = parts[1]
                    val poster = if (parts.size >= 3 && parts[2].isNotEmpty()) parts[2] else defaultPoster
                    searchList.add(
                        newLiveSearchResponse(
                            name = title,
                            url  = streamUrl,
                            type = TvType.Live
                        ) {
                            this.posterUrl = poster
                        }
                    )
                }
                continue
            }

            val domain = source.trimEnd('/')
            val htmlResponse = fetchHtml("$domain/", domain)
            if (htmlResponse.isEmpty()) continue

            val document = Jsoup.parse(htmlResponse)
            document.select("a.channel-item").forEach { element ->
                val title      = element.select(".channel-name").text().trim()
                val channelUrl = element.attr("href")
                if (title.isNotEmpty() && title.lowercase().contains(query.lowercase())) {
                    val fullUrl = if (channelUrl.startsWith("http")) channelUrl else "$domain/${channelUrl.trimStart('/')}"
                    if (searchList.none { it.url == fullUrl }) {
                        searchList.add(
                            newLiveSearchResponse(
                                name = title,
                                url  = fullUrl,
                                type = TvType.Live
                            ) {
                                this.posterUrl = defaultPoster
                            }
                        )
                    }
                }
            }
        }
        return searchList
    }

    // 3. LOAD: Return live stream details for UI
    override suspend fun load(url: String): LoadResponse {
        val uri = Uri.parse(url)
        val domain = if (uri.host != null) "${uri.scheme}://${uri.host}" else mainUrl

        val (title, poster) = if (url.endsWith(".m3u8") || url.contains("mono.m3u8")) {
            Pair("Canlı Yayın", defaultPoster)
        } else {
            val htmlResponse = fetchHtml(url, domain)
            val document     = Jsoup.parse(htmlResponse)
            val t = document.title().trim().ifEmpty { "Canlı Kanal" }
            val p = document.select("meta[property=og:image]").attr("content").ifEmpty { defaultPoster }
            Pair(t, p)
        }

        return newLiveStreamLoadResponse(
            name    = title,
            url     = url,
            dataUrl = url
        ) {
            this.posterUrl = poster
            this.plot      = "Canlı TV – Kesintisiz Yayın Akışı."
        }
    }

    // 4. LOAD LINKS: Extract live stream URLs
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Direct M3U8 link
        if (data.endsWith(".m3u8") || data.contains(".m3u8?")) {
            callback.invoke(
                newExtractorLink(
                    source  = name,
                    name    = "$name - Doğrudan Akış",
                    url     = data,
                    type    = ExtractorLinkType.M3U8
                ) {
                    this.quality = Qualities.P1080.value
                }
            )
            return true
        }

        val uri = Uri.parse(data)
        val domain = if (uri.host != null) "${uri.scheme}://${uri.host}" else mainUrl
        val pageHtml = fetchHtml(data, domain)

        val streamHeaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Referer"    to "$domain/",
            "Origin"     to domain,
            "Accept"     to "*/*"
        )

        var foundLink = false

        // 1. Direct Extraction via page CONFIG script
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
                    this.referer = "$domain/"
                    this.headers = streamHeaders
                    this.quality = Qualities.P1080.value
                }
            )

            if (primaryStreamUrl.contains(".cfd/")) {
                val directStreamUrl = "https://d72577a9dd0ec71.cfd/$channelId/mono.m3u8"
                if (directStreamUrl != primaryStreamUrl) {
                    callback.invoke(
                        newExtractorLink(
                            source  = name,
                            name    = "$name - Yedek Sunucu",
                            url     = directStreamUrl,
                            type    = ExtractorLinkType.M3U8
                        ) {
                            this.referer = "$domain/"
                            this.headers = streamHeaders
                            this.quality = Qualities.P1080.value
                        }
                    )
                }
            }

            foundLink = true
        }

        // 2. Cloudflare / Anti-Bot Bypass via WebViewResolver
        try {
            val webViewRes = app.get(data, headers = streamHeaders, interceptor = WebViewResolver(Regex("""\.m3u8""")))
            val resolvedUrl = webViewRes.url

            if (resolvedUrl.isNotEmpty() && resolvedUrl.contains(".m3u8") && !resolvedUrl.contains("video.bsky.app") && !resolvedUrl.contains("preroll")) {
                callback.invoke(
                    newExtractorLink(
                        source  = name,
                        name    = "$name - Canlı Yayın (Bypass)",
                        url     = resolvedUrl,
                        type    = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "$domain/"
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
                        this.referer = "$domain/"
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
