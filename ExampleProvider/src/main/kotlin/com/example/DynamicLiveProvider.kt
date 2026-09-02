package com.example

import android.net.Uri
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.Jsoup
import java.net.URI

class DynamicLiveProvider : MainAPI() {
    override var name = "Inat Live"
    override var mainUrl = FALLBACK_URL
    override var lang = "tr"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Live)

    override val mainPage = mainPageOf(
        Pair("all", "Tüm Kanallar & Maçlar"),
        Pair("24-7", "7/24 Canlı TV Kanalları"),
        Pair("football", "Futbol Maçları"),
        Pair("basketball", "Basketbol Maçları")
    )

    companion object {
        private const val CONFIG_CSV_URL =
            "https://docs.google.com/spreadsheets/d/1IHYlgjzhLCX3MKhewg7FGTf_oIkNlXzl2ogYXkSRjFM/export?format=csv"
        private const val FALLBACK_URL = "https://www.google.com"

        private const val ANDROID_WEBVIEW_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7 Build/TQ3A.230901.001; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/120.0.6099.210 Mobile Safari/537.36"

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

        fun getOrigin(url: String): String {
            return try {
                val uri = URI(url)
                val portStr = if (uri.port != -1 && uri.port != 80 && uri.port != 443) ":" else ""
                "://"
            } catch (e: Exception) {
                val prefix = if (url.contains("://")) url.substringBefore("://") + "://" else "https://"
                val hostPart = url.substringAfter("://").substringBefore("/")
                ""
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
        val baseOrigin = getOrigin(baseUrl)
        val headers = mapOf(
            "User-Agent" to ANDROID_WEBVIEW_UA,
            "Referer" to "/",
            "Origin" to baseOrigin,
            "X-Requested-With" to "com.inattv.app",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        )

        val html = try {
            app.get(baseUrl, headers = headers).text
        } catch (e: Exception) {
            return newHomePageResponse(request, emptyList())
        }

        val document = Jsoup.parse(html)
        val channelList = mutableListOf<SearchResponse>()
        val channelElements = document.select("a.channel-item")

        channelElements.forEach { element ->
            val nameEl = element.selectFirst(".channel-name")
            val rawName = nameEl?.text()?.trim() ?: element.text().trim()
            val status = element.selectFirst(".channel-status")?.text()?.trim().orEmpty()
            val href = element.attr("href").trim()
            val category = element.attr("data-category").trim().lowercase()

            if (rawName.isNotEmpty() && href.isNotEmpty() && href != "#" && !href.startsWith("javascript:")) {
                val title = if (status.isNotEmpty() && status != "7/24") " ()" else rawName
                val fullUrl = if (href.startsWith("http://") || href.startsWith("https://")) {
                    href
                } else {
                    "/"
                }

                val matchesCategory = when (request.data) {
                    "football"   -> category == "football"
                    "basketball" -> category == "basketball"
                    "24-7"       -> status == "7/24" || category.isEmpty()
                    else         -> true
                }

                if (matchesCategory) {
                    channelList.add(
                        newLiveSearchResponse(
                            name = title,
                            url = fullUrl,
                            type = TvType.Live
                        )
                    )
                }
            }
        }

        return newHomePageResponse(request, channelList)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val baseUrl = syncMainUrl()
        val headers = mapOf(
            "User-Agent" to ANDROID_WEBVIEW_UA,
            "Referer" to "/"
        )

        val html = try {
            app.get(baseUrl, headers = headers).text
        } catch (e: Exception) {
            return emptyList()
        }

        val document = Jsoup.parse(html)
        val searchList = mutableListOf<SearchResponse>()
        val q = query.lowercase().trim()

        document.select("a.channel-item").forEach { element ->
            val rawName = element.selectFirst(".channel-name")?.text()?.trim() ?: element.text().trim()
            val status = element.selectFirst(".channel-status")?.text()?.trim().orEmpty()
            val href = element.attr("href").trim()

            if (rawName.isNotEmpty() && href.isNotEmpty() && href != "#") {
                if (rawName.lowercase().contains(q)) {
                    val title = if (status.isNotEmpty() && status != "7/24") " ()" else rawName
                    val fullUrl = if (href.startsWith("http://") || href.startsWith("https://")) {
                        href
                    } else {
                        "/"
                    }

                    searchList.add(
                        newLiveSearchResponse(
                            name = title,
                            url = fullUrl,
                            type = TvType.Live
                        )
                    )
                }
            }
        }

        return searchList
    }

    override suspend fun load(url: String): LoadResponse {
        val baseUrl = syncMainUrl()
        val headers = mapOf(
            "User-Agent" to ANDROID_WEBVIEW_UA,
            "Referer" to "/"
        )

        val html = try {
            app.get(url, headers = headers).text
        } catch (e: Exception) {
            ""
        }

        val document = Jsoup.parse(html)
        val title = document.selectFirst(".channel-title, h1, title")?.text()?.trim()
            ?.replace("Video Player", "")?.trim()?.ifEmpty { "Canlı Yayın" }
            ?: "Canlı Yayın"

        return newLiveStreamLoadResponse(
            name = title,
            url = url,
            dataUrl = url
        ) {
            this.plot = "İnat TV Canlı Yayın Akışı"
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val baseUrl = syncMainUrl()
        val baseOrigin = getOrigin(baseUrl)
        val parentHeaders = mapOf(
            "User-Agent" to ANDROID_WEBVIEW_UA,
            "Referer" to "/",
            "Origin" to baseOrigin,
            "X-Requested-With" to "com.inattv.app",
            "Accept" to "*/*"
        )

        val html = try {
            app.get(data, headers = parentHeaders).text
        } catch (e: Exception) {
            return false
        }

        val uri = Uri.parse(data)
        val channelId = uri.getQueryParameter("id")
        var foundStream = false

        // 1. If channel id is a direct .m3u8 link (e.g. ?id=https://.../701.m3u8)
        if (!channelId.isNullOrEmpty() && (channelId.startsWith("http://") || channelId.startsWith("https://"))) {
            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = " - Canlı Yayın",
                    url = channelId,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = "/"
                    this.headers = mapOf(
                        "User-Agent" to ANDROID_WEBVIEW_UA,
                        "Referer" to "/",
                        "Origin" to baseOrigin
                    )
                    this.quality = Qualities.P1080.value
                }
            )
            foundStream = true
        }

        // 2. Extract CONFIG.baseUrl and construct dynamic mono.m3u8 link
        val configBaseMatch = Regex("""baseUrl\s*:\s*['"](https?://[^'"]+)['"]""").find(html)
        val configBaseUrl = configBaseMatch?.groupValues?.get(1)

        if (!configBaseUrl.isNullOrEmpty() && !channelId.isNullOrEmpty() && !channelId.startsWith("http")) {
            val constructedStreamUrl = "//mono.m3u8"
            val streamOrigin = getOrigin(constructedStreamUrl)

            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = " - Ana Sunucu (HLS)",
                    url = constructedStreamUrl,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = data
                    this.headers = mapOf(
                        "User-Agent" to ANDROID_WEBVIEW_UA,
                        "Referer" to data,
                        "Origin" to baseOrigin,
                        "X-Requested-With" to "com.inattv.app"
                    )
                    this.quality = Qualities.P1080.value
                }
            )
            foundStream = true
        }

        // 3. Check for any direct m3u8 in page scripts or iframes
        val m3u8Regex = Regex("""(?:"|')(https?://[^"'\s]+\.m3u8[^"'\s]*)(?:"|')""")
        m3u8Regex.findAll(html).forEach { match ->
            val streamUrl = match.groupValues[1]
            if (!streamUrl.contains("video.bsky.app") && !streamUrl.contains("preroll") && !streamUrl.contains("ad")) {
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = " - Canlı Akış",
                        url = streamUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = data
                        this.headers = parentHeaders
                        this.quality = Qualities.P1080.value
                    }
                )
                foundStream = true
            }
        }

        return foundStream
    }
}
