package com.example

import android.net.Uri
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.Jsoup

class ExampleProvider : MainAPI() {
    override var mainUrl = "https://inattv1321.xyz"
    override var name = "İnat TV"
    override val supportedTypes = setOf(TvType.Live)
    override var lang = "tr"
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        Pair("7-24", "7/24 Canlı TV")
    )

    private val requestHeaders = mapOf(
        "User-Agent"      to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer"         to "$mainUrl/",
        "X-Requested-With" to "XMLHttpRequest",
        "Accept"          to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.9,tr;q=0.8"
    )

    // 1. MAIN PAGE: List all live channels from the 7/24 category
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val channelList = mutableListOf<SearchResponse>()
        val htmlResponse = app.get("$mainUrl/kategori/${request.data}", headers = requestHeaders).text
        val document = Jsoup.parse(htmlResponse)

        document.select("div.channel-card, a.channel-item").forEach { element ->
            val title = element.select(".channel-name").text().trim()
            val channelUrl = element.attr("href")
            val poster = element.select("img").attr("src")

            if (title.isNotEmpty() && channelUrl.isNotEmpty()) {
                channelList.add(
                    newLiveSearchResponse(
                        name = title,
                        url = fixUrl(channelUrl),
                        type = TvType.Live
                    ) {
                        this.posterUrl = fixUrl(poster)
                    }
                )
            }
        }
        return newHomePageResponse(request, channelList)
    }

    // 2. SEARCH: Handle search queries for channels
    override suspend fun search(query: String): List<SearchResponse> {
        val searchList = mutableListOf<SearchResponse>()
        val htmlResponse = app.get("$mainUrl/?s=${query.trim()}", headers = requestHeaders).text
        val document = Jsoup.parse(htmlResponse)

        document.select("div.channel-card, a.channel-item").forEach { element ->
            val title = element.select(".channel-name").text().trim()
            val channelUrl = element.attr("href")
            val poster = element.select("img").attr("src")

            if (title.isNotEmpty() && title.lowercase().contains(query.lowercase())) {
                searchList.add(
                    newLiveSearchResponse(
                        name = title,
                        url = fixUrl(channelUrl),
                        type = TvType.Live
                    ) {
                        this.posterUrl = fixUrl(poster)
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
        val poster = document.select("meta[property=og:image]").attr("content")

        return newLiveStreamLoadResponse(
            name = title,
            url = url,
            dataUrl = url
        ) {
            this.posterUrl = poster.ifEmpty { null }
            this.plot = "$title – 7/24 Kesintisiz Canlı TV yayını."
        }
    }

    // 4. LOAD LINKS: Extract the .m3u8 stream URL for playback
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val pageHtml = app.get(data, headers = requestHeaders).text

        // Primary: find a raw .m3u8 URL in the page source
        val m3u8Regex = Regex("""https?://[^\s"'<>]+?\.m3u8[^\s"'<>]*""")
        val matchedUrl = m3u8Regex.find(pageHtml)?.value
        if (!matchedUrl.isNullOrEmpty()) {
            callback.invoke(
                ExtractorLink(
                    source = name,
                    name = name,
                    url = matchedUrl,
                    referer = mainUrl,
                    quality = Qualities.P1080.value,
                    isM3u8 = true
                )
            )
            return true
        }

        // Fallback: construct the stream URL from the channel ID query parameter
        val channelId = Uri.parse(data).getQueryParameter("id")
        if (!channelId.isNullOrEmpty()) {
            val fallbackUrl = "https://d72577a9dd0ec71.cfd/$channelId/mono.m3u8"
            callback.invoke(
                ExtractorLink(
                    source = name,
                    name = name,
                    url = fallbackUrl,
                    referer = mainUrl,
                    quality = Qualities.P1080.value,
                    isM3u8 = true
                )
            )
            return true
        }

        return false
    }
}
