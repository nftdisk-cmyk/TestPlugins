package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.Jsoup

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
            if (href == "#" || href.startsWith("javascript:")) return@mapNotNull null

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
        val streamHeaders = mapOf(
            "User-Agent" to USER_AGENT,
            "Referer" to "$baseUrl/",
            "Origin" to baseUrl.trimEnd('/'),
            "Accept" to "*/*"
        )

        val html = try {
            app.get(data, headers = streamHeaders).text
        } catch (e: Exception) {
            return false
        }

        val document = Jsoup.parse(html)

        // 1. Check for direct .m3u8 regex pattern
        val m3u8Regex = Regex("""(?:"|')(https?://[^"']+\.m3u8[^"']*)(?:"|')""")
        val m3u8Match = m3u8Regex.find(html)?.groupValues?.get(1)
            ?: document.selectFirst("source[src*=.m3u8], video[src*=.m3u8]")?.attr("src")

        if (!m3u8Match.isNullOrEmpty()) {
            val resolvedStreamUrl = fixUrl(m3u8Match)
            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = "$name - Canlı",
                    url = resolvedStreamUrl,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = "$baseUrl/"
                    this.headers = streamHeaders
                    this.quality = Qualities.P1080.value
                }
            )
            return true
        }

        // 2. Check for embedded iframe players
        val iframeSrc = document.selectFirst("iframe[src]")?.attr("src")
        if (!iframeSrc.isNullOrEmpty()) {
            val fullIframeUrl = fixUrl(iframeSrc)
            try {
                val iframeHtml = app.get(fullIframeUrl, headers = streamHeaders).text
                val iframeStream = m3u8Regex.find(iframeHtml)?.groupValues?.get(1)
                if (!iframeStream.isNullOrEmpty()) {
                    callback.invoke(
                        newExtractorLink(
                            source = this.name,
                            name = "$name - Web Player",
                            url = fixUrl(iframeStream),
                            type = ExtractorLinkType.M3U8
                        ) {
                            this.referer = "$baseUrl/"
                            this.headers = streamHeaders
                            this.quality = Qualities.P1080.value
                        }
                    )
                    return true
                }
            } catch (e: Exception) { }
        }

        return false
    }
}
