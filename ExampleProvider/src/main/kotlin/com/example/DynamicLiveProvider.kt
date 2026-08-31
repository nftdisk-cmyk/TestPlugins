package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class DynamicLiveProvider : MainAPI() {
    override var name = "Inat Live"
    override var mainUrl = FALLBACK_URL
    override var lang = "tr"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Live, TvType.Movie, TvType.TvSeries)

    companion object {
        private const val CONFIG_CSV_URL =
            "https://docs.google.com/spreadsheets/d/1IHYlgjzhLCX3MKhewg7FGTf_oIkNlXzl2ogYXkSRjFM/export?format=csv"
        private const val FALLBACK_URL = "https://www.google.com"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        private var cachedDynamicUrl: String? = null

        // Ad and promotional domain/keyword blocklist
        private val AD_KEYWORDS = listOf(
            "bet", "casino", "promo", "banner", "ad", "ads", "sponsor", "stream-ad",
            "advert", "popunder", "click", "tracker", "affiliate", "bonus", "slot",
            "kumar", "bahis", "reklam", "tanitim", "adserver", "doubleclick"
        )

        /**
         * Dynamically resolves the base URL from Google Sheets (Row 2 / Cell A2).
         */
        suspend fun getDynamicMainUrl(): String {
            cachedDynamicUrl?.let { return it }

            return try {
                val response = app.get(CONFIG_CSV_URL, timeout = 10L).text
                val lines = response.trim().lines()
                if (lines.size >= 2) {
                    val cellA2 = lines[1].trim().replace("\"", "")
                    if (cellA2.startsWith("http://", ignoreCase = true) || cellA2.startsWith("https://", ignoreCase = true)) {
                        cachedDynamicUrl = cellA2
                        return cellA2
                    }
                }
                FALLBACK_URL
            } catch (e: Exception) {
                FALLBACK_URL
            }
        }

        /**
         * Checks if a URL or keyword matches known ad or promotional patterns.
         */
        fun isAdOrPromo(url: String): Boolean {
            val lower = url.lowercase()
            return AD_KEYWORDS.any { keyword ->
                lower.contains("/") || lower.contains(".") || lower.contains("-") || lower.contains("_") || lower.contains("=")
            }
        }
    }

    private suspend fun syncMainUrl(): String {
        val currentUrl = getDynamicMainUrl()
        this.mainUrl = currentUrl
        return currentUrl
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val baseUrl = syncMainUrl()
        val headers = mapOf(
            "User-Agent" to USER_AGENT,
            "Referer" to "$baseUrl/",
            "Origin" to baseUrl.trimEnd('/')
        )

        val html = try {
            app.get(baseUrl, headers = headers).text
        } catch (e: Exception) {
            return newHomePageResponse(emptyList<HomePageList>())
        }

        val document = Jsoup.parse(html)
        val homePageList = mutableListOf<HomePageList>()

        val channels = document.select("div.channel, a.channel-link, div.card, div.item, li.channel-item, a[href*=/]").mapNotNull { element ->
            val title = element.selectFirst(".title, h2, h3, h4, span, p")?.text()?.trim()
                ?: element.text().trim().takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null

            val href = element.attr("href").ifEmpty { element.selectFirst("a")?.attr("href") } ?: return@mapNotNull null
            if (href == "#" || href.startsWith("javascript:") || isAdOrPromo(href)) return@mapNotNull null

            val posterUrl = element.selectFirst("img")?.let {
                it.attr("src").ifEmpty { it.attr("data-src") }
            }

            newLiveSearchResponse(
                name = title,
                url = fixUrl(href),
                type = TvType.Live
            ) {
                this.posterUrl = posterUrl?.let { fixUrl(it) }
            }
        }

        if (channels.isNotEmpty()) {
            homePageList.add(HomePageList("Canlı Yayınlar", channels))
        }

        return newHomePageResponse(homePageList)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val baseUrl = syncMainUrl()
        val headers = mapOf(
            "User-Agent" to USER_AGENT,
            "Referer" to "$baseUrl/"
        )

        val searchUrl = "$baseUrl/?s=$query"
        val html = try {
            app.get(searchUrl, headers = headers).text
        } catch (e: Exception) {
            return emptyList()
        }

        val document = Jsoup.parse(html)

        return document.select("div.search-item, div.card, a.item, div.channel").mapNotNull { element ->
            val title = element.selectFirst(".title, h2, h3, span")?.text()?.trim()
                ?: element.text().trim().takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null

            val href = element.attr("href").ifEmpty { element.selectFirst("a")?.attr("href") } ?: return@mapNotNull null
            if (href == "#" || href.startsWith("javascript:") || isAdOrPromo(href)) return@mapNotNull null

            val posterUrl = element.selectFirst("img")?.let {
                it.attr("src").ifEmpty { it.attr("data-src") }
            }

            newLiveSearchResponse(
                name = title,
                url = fixUrl(href),
                type = TvType.Live
            ) {
                this.posterUrl = posterUrl?.let { fixUrl(it) }
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val baseUrl = syncMainUrl()
        val headers = mapOf(
            "User-Agent" to USER_AGENT,
            "Referer" to "$baseUrl/"
        )

        val html = app.get(url, headers = headers).text
        val document = Jsoup.parse(html)

        val title = document.selectFirst("h1, .channel-title, .title")?.text()?.trim() ?: "Canlı Yayın"
        val posterUrl = document.selectFirst(".poster img, .channel-logo img, img")?.attr("src")

        return newLiveStreamLoadResponse(
            name = title,
            url = url,
            dataUrl = url
        ) {
            this.posterUrl = posterUrl?.let { fixUrl(it) }
            this.plot = document.selectFirst(".description, p.desc")?.text()?.trim()
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val baseUrl = syncMainUrl()
        val parentHeaders = mapOf(
            "User-Agent" to USER_AGENT,
            "Referer" to "$baseUrl/",
            "Origin" to baseUrl.trimEnd('/'),
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        )

        val html = try {
            app.get(data, headers = parentHeaders).text
        } catch (e: Exception) {
            return false
        }

        val parentDoc = Jsoup.parse(html)
        var foundStream = false

        // Regex patterns for extracting .m3u8 live playlists
        val m3u8RegexList = listOf(
            Regex("""(?:source|file|src|url)\s*:\s*['"](https?://[^'"]+\.m3u8[^'"]*)['"]""", RegexOption.IGNORE_CASE),
            Regex("""['"](https?://[^'"]+\.m3u8[^'"]*)['"]""", RegexOption.IGNORE_CASE),
            Regex("""(https?://[^\s"'<>]+\.m3u8[^\s"'<>]*)""", RegexOption.IGNORE_CASE)
        )

        // 1. Target dedicated player containers and inner iframes systematically
        val iframeCandidates = mutableListOf<String>()

        // Priority iframe selectors (inside player containers)
        val playerContainers = parentDoc.select("#player iframe, .player iframe, #stream iframe, .stream iframe, div[id*='player'] iframe, div[class*='player'] iframe")
        playerContainers.forEach { iframe ->
            val src = iframe.attr("src").ifEmpty { iframe.attr("data-src") }
            if (src.isNotEmpty() && !isAdOrPromo(src)) {
                iframeCandidates.add(fixUrl(src))
            }
        }

        // Generic iframe selectors if no dedicated player iframe found
        if (iframeCandidates.isEmpty()) {
            parentDoc.select("iframe[src*='player'], iframe[src*='embed'], iframe[src*='stream'], iframe[src*='live'], iframe[src]").forEach { iframe ->
                val src = iframe.attr("src").ifEmpty { iframe.attr("data-src") }
                if (src.isNotEmpty() && !isAdOrPromo(src)) {
                    iframeCandidates.add(fixUrl(src))
                }
            }
        }

        // 2. Fetch and inspect each candidate inner iframe with parent URL as Referer
        for (iframeUrl in iframeCandidates.distinct()) {
            try {
                val iframeHeaders = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to data,
                    "Origin" to baseUrl.trimEnd('/'),
                    "Accept" to "*/*"
                )

                val iframeHtml = app.get(iframeUrl, headers = iframeHeaders).text
                if (iframeHtml.isEmpty()) continue

                // Check for nested iframes within this iframe
                val innerDoc = Jsoup.parse(iframeHtml)
                val nestedIframe = innerDoc.selectFirst("iframe[src]")?.attr("src")
                val finalIframeHtml = if (!nestedIframe.isNullOrEmpty() && !isAdOrPromo(nestedIframe)) {
                    val nestedUrl = fixUrl(nestedIframe)
                    try {
                        app.get(nestedUrl, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to iframeUrl)).text
                    } catch (e: Exception) {
                        iframeHtml
                    }
                } else {
                    iframeHtml
                }

                // Search for valid non-ad .m3u8 stream links
                for (regex in m3u8RegexList) {
                    val matches = regex.findAll(finalIframeHtml)
                    for (match in matches) {
                        val streamUrl = match.groupValues.getOrNull(1) ?: match.value
                        if (streamUrl.isNotEmpty() && !isAdOrPromo(streamUrl)) {
                            val resolvedStreamUrl = fixUrl(streamUrl)
                            val streamHeaders = mapOf(
                                "User-Agent" to USER_AGENT,
                                "Referer" to iframeUrl,
                                "Origin" to baseUrl.trimEnd('/')
                            )

                            callback.invoke(
                                newExtractorLink(
                                    source = this.name,
                                    name = "$name - Canlı Yayın",
                                    url = resolvedStreamUrl,
                                    type = ExtractorLinkType.M3U8
                                ) {
                                    this.referer = iframeUrl
                                    this.headers = streamHeaders
                                    this.quality = Qualities.P1080.value
                                }
                            )
                            foundStream = true
                            break
                        }
                    }
                    if (foundStream) break
                }
                if (foundStream) break
            } catch (e: Exception) {
                // Continue to next candidate iframe on error
            }
        }

        // 3. Fallback: Parse top-level script only if iframes yielded no valid non-ad stream
        if (!foundStream) {
            for (regex in m3u8RegexList) {
                val match = regex.find(html)?.groupValues?.getOrNull(1)
                if (!match.isNullOrEmpty() && !isAdOrPromo(match)) {
                    val resolvedStreamUrl = fixUrl(match)
                    callback.invoke(
                        newExtractorLink(
                            source = this.name,
                            name = "$name - Canlı (Doğrudan)",
                            url = resolvedStreamUrl,
                            type = ExtractorLinkType.M3U8
                        ) {
                            this.referer = "$baseUrl/"
                            this.headers = parentHeaders
                            this.quality = Qualities.P1080.value
                        }
                    )
                    foundStream = true
                    break
                }
            }
        }

        return foundStream
    }
}
