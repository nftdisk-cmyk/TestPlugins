package com.example

import android.net.Uri
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.Jsoup

class ExampleProvider : MainAPI() {
    override var mainUrl = "https://www.seirsanduk.online"
    override var name = "SeirSanduk TV"
    override val supportedTypes = setOf(TvType.Live)
    override var lang = "bg"
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        Pair("all", "Tüm Kanallar"),
        Pair("sports", "Spor Kanalları"),
        Pair("movies", "Film & Dizi Kanalları"),
        Pair("kids", "Çocuk Kanalları")
    )

    private val defaultPoster = "https://www.seirsanduk.online/images/logo.png"

    private val browserHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer"    to "$mainUrl/",
        "Origin"     to mainUrl,
        "Accept"     to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
    )

    // 1. MAIN PAGE: List all live channels with categories
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val channelList = mutableListOf<SearchResponse>()
        val htmlResponse = app.get("$mainUrl/", headers = browserHeaders).text
        val document = Jsoup.parse(htmlResponse)

        val channelElements = document.select("#channels li a")

        channelElements.forEach { element ->
            val title = element.text().trim()
            val href = element.attr("href")
            val img = element.select("img").attr("src")
            val posterUrl = if (img.isNotEmpty()) fixUrl(img) else defaultPoster

            if (title.isNotEmpty() && href.isNotEmpty()) {
                val fullUrl = fixUrl(href)
                
                val lowerTitle = title.lowercase()
                val isSport = lowerTitle.contains("sport") || lowerTitle.contains("diema") || lowerTitle.contains("euro") || lowerTitle.contains("ring") || lowerTitle.contains("max")
                val isMovie = lowerTitle.contains("kino") || lowerTitle.contains("cinema") || lowerTitle.contains("star") || lowerTitle.contains("drama") || lowerTitle.contains("axn") || lowerTitle.contains("action")
                val isKids = lowerTitle.contains("kid") || lowerTitle.contains("cartoon") || lowerTitle.contains("disney") || lowerTitle.contains("nick")

                val matchesCategory = when (request.data) {
                    "sports" -> isSport
                    "movies" -> isMovie
                    "kids"   -> isKids
                    else     -> true
                }

                if (matchesCategory) {
                    channelList.add(
                        newLiveSearchResponse(
                            name = title,
                            url  = fullUrl,
                            type = TvType.Live
                        ) {
                            this.posterUrl = posterUrl
                        }
                    )
                }
            }
        }

        return newHomePageResponse(request, channelList)
    }

    // 2. SEARCH: Search across all channels
    override suspend fun search(query: String): List<SearchResponse> {
        val searchList = mutableListOf<SearchResponse>()
        val htmlResponse = app.get("$mainUrl/", headers = browserHeaders).text
        val document = Jsoup.parse(htmlResponse)

        document.select("#channels li a").forEach { element ->
            val title = element.text().trim()
            val href = element.attr("href")
            val img = element.select("img").attr("src")
            val posterUrl = if (img.isNotEmpty()) fixUrl(img) else defaultPoster

            if (title.isNotEmpty() && title.lowercase().contains(query.lowercase())) {
                searchList.add(
                    newLiveSearchResponse(
                        name = title,
                        url  = fixUrl(href),
                        type = TvType.Live
                    ) {
                        this.posterUrl = posterUrl
                    }
                )
            }
        }

        return searchList
    }

    // 3. LOAD: Prepare player and channel info
    override suspend fun load(url: String): LoadResponse {
        val htmlResponse = app.get(url, headers = browserHeaders).text
        val document = Jsoup.parse(htmlResponse)

        val title = document.select("div.buttonsLeft h1 a").text().trim().ifEmpty {
            document.title().trim().ifEmpty { "Canlı Yayın" }
        }
        val poster = defaultPoster
        val description = document.select("#program .nav").text().trim().ifEmpty {
            "SeirSanduk Canlı TV Akışı."
        }

        return newLiveStreamLoadResponse(
            name    = title,
            url     = url,
            dataUrl = url
        ) {
            this.posterUrl = poster
            this.plot      = description
        }
    }

    // 4. LOAD LINKS: Extract live HLS stream URLs from all available players
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val streamHeaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Referer"    to "$mainUrl/",
            "Origin"     to mainUrl,
            "Accept"     to "*/*"
        )

        val m3u8Regex = Regex("""file:\s*['"](https?://[^'"]+\.m3u8[^'"]*)['"]""")
        var foundAny = false

        // Fetch Default Player
        try {
            val defaultHtml = app.get(data, headers = browserHeaders).text
            val defaultStream = m3u8Regex.find(defaultHtml)?.groupValues?.get(1)
            if (!defaultStream.isNullOrEmpty()) {
                callback.invoke(
                    newExtractorLink(
                        source  = name,
                        name    = "$name - Ana Sunucu (HD)",
                        url     = defaultStream,
                        type    = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "$mainUrl/"
                        this.headers = streamHeaders
                        this.quality = Qualities.P1080.value
                    }
                )
                foundAny = true
            }

            // Extract channel id for alternative servers (Player 2 & 3)
            val channelId = Uri.parse(data).getQueryParameter("id")
            if (!channelId.isNullOrEmpty()) {
                // Player 2
                try {
                    val p2Html = app.get("$mainUrl/?player=12&id=$channelId&pass=", headers = browserHeaders).text
                    val p2Stream = m3u8Regex.find(p2Html)?.groupValues?.get(1)
                    if (!p2Stream.isNullOrEmpty() && p2Stream != defaultStream) {
                        callback.invoke(
                            newExtractorLink(
                                source  = name,
                                name    = "$name - Yedek Sunucu 1",
                                url     = p2Stream,
                                type    = ExtractorLinkType.M3U8
                            ) {
                                this.referer = "$mainUrl/"
                                this.headers = streamHeaders
                                this.quality = Qualities.P1080.value
                            }
                        )
                        foundAny = true
                    }
                } catch (e: Exception) { }

                // Player 3
                try {
                    val p3Html = app.get("$mainUrl/?player=13&id=$channelId&pass=", headers = browserHeaders).text
                    val p3Stream = m3u8Regex.find(p3Html)?.groupValues?.get(1)
                    if (!p3Stream.isNullOrEmpty() && p3Stream != defaultStream) {
                        callback.invoke(
                            newExtractorLink(
                                source  = name,
                                name    = "$name - Yedek Sunucu 2",
                                url     = p3Stream,
                                type    = ExtractorLinkType.M3U8
                            ) {
                                this.referer = "$mainUrl/"
                                this.headers = streamHeaders
                                this.quality = Qualities.P1080.value
                            }
                        )
                        foundAny = true
                    }
                } catch (e: Exception) { }
            }
        } catch (e: Exception) { }

        return foundAny
    }
}
