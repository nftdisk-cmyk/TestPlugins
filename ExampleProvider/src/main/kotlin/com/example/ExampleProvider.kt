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

    /**
     * Maps channel names to crystal clear high-resolution HD logos.
     * Falls back to the website thumbnail if no custom HD logo is matched.
     */
    private fun getHighResLogo(title: String, defaultImg: String): String {
        val lower = title.lowercase()
        return when {
            lower.contains("bnt 1") -> "https://i.imgur.com/7JU9b5j.png"
            lower.contains("bnt 2") -> "https://i.imgur.com/FyTUr9Q.png"
            lower.contains("bnt 3") -> "https://i.imgur.com/pPpSJ4u.png"
            lower.contains("bnt 4") -> "https://i.imgur.com/Lw8b3yu.png"
            lower.contains("btv action") -> "https://i.imgur.com/hGUXHuT.png"
            lower.contains("btv cinema") -> "https://i.imgur.com/uV81rS4.png"
            lower.contains("btv comedy") -> "https://i.imgur.com/1c6A5dO.png"
            lower.contains("btv story") || lower.contains("btv lady") -> "https://i.imgur.com/rXBPJ1e.png"
            lower.contains("btv") -> "https://i.imgur.com/l47z7gw.png"
            lower.contains("kino nova") -> "https://i.imgur.com/hMqEY0J.png"
            lower.contains("nova news") -> "https://i.imgur.com/s6IryS3.png"
            lower.contains("nova sport") -> "https://i.imgur.com/WCsPSLX.png"
            lower.contains("nova") -> "https://i.imgur.com/WCsPSLX.png"
            lower.contains("diema family") -> "https://i.imgur.com/SgNVY4d.png"
            lower.contains("diema sport 3") -> "https://i.imgur.com/SgNVY4d.png"
            lower.contains("diema sport 2") -> "https://i.imgur.com/SgNVY4d.png"
            lower.contains("diema sport") -> "https://i.imgur.com/SgNVY4d.png"
            lower.contains("diema") -> "https://i.imgur.com/SgNVY4d.png"
            lower.contains("max sport 1") -> "https://i.imgur.com/0ohnpql.png"
            lower.contains("max sport 2") -> "https://i.imgur.com/33m1wPN.png"
            lower.contains("max sport 3") -> "https://i.imgur.com/eGiGgZ2.png"
            lower.contains("max sport 4") -> "https://i.imgur.com/7IxXX6f.png"
            lower.contains("max one") -> "https://i.imgur.com/0ohnpql.png"
            lower.contains("nat geo wild") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/2/27/National_Geographic_Wild_logo.svg/960px-National_Geographic_Wild_logo.svg.png"
            lower.contains("nat geo") || lower.contains("national geographic") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/f/fc/Natgeologo.svg/960px-Natgeologo.svg.png"
            lower.contains("disney") -> "https://i.imgur.com/UxrAiAe.png"
            lower.contains("nickelodeon") -> "https://i.imgur.com/E84jnP8.png"
            lower.contains("nick jr") -> "https://i.imgur.com/E84jnP8.png"
            lower.contains("nicktoons") -> "https://i.imgur.com/E84jnP8.png"
            lower.contains("star channel") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/8/89/Star_Channel_2020.svg/960px-Star_Channel_2020.svg.png"
            lower.contains("star crime") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/4/40/Star_Crime_2023.svg/960px-Star_Crime_2023.svg.png"
            lower.contains("star life") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f9/Star_Life_2021.svg/960px-Star_Life_2021.svg.png"
            lower.contains("axn black") -> "https://i.imgur.com/Peo1QiZ.png"
            lower.contains("axn white") -> "https://i.imgur.com/47IKxmt.png"
            lower.contains("axn") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/5/52/AXN_logo_%282015%29.svg/960px-AXN_logo_%282015%29.svg.png"
            lower.contains("bloomberg") -> "https://i.imgur.com/cHwOVqk.png"
            lower.contains("bulgaria on air") -> "https://i.imgur.com/mvShl7F.png"
            lower.contains("the voice") -> "https://i.imgur.com/OoJSmoj.png"
            lower.contains("city tv") -> "https://i.imgur.com/mFL452f.png"
            lower.contains("code fashion") -> "https://i.imgur.com/mVc2g64.png"
            lower.contains("dstv") -> "https://i.imgur.com/YMgzzkf.png"
            lower.contains("euronews") -> "https://i.imgur.com/RrQVoOg.png"
            lower.contains("78 tv") || lower.contains("7/8 tv") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1c/Seven-Eight-TV.png/960px-Seven-Eight-TV.png"
            lower.contains("tiankov") -> "https://i.imgur.com/VKY4q64.png"
            lower.contains("travel tv") -> "https://i.imgur.com/5xllfed.png"
            else -> if (defaultImg.isNotEmpty()) fixUrl(defaultImg) else defaultPoster
        }
    }

    // 1. MAIN PAGE: List all live channels with categories and HD logos
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val channelList = mutableListOf<SearchResponse>()
        val htmlResponse = app.get("$mainUrl/", headers = browserHeaders).text
        val document = Jsoup.parse(htmlResponse)

        val channelElements = document.select("#channels li a")

        channelElements.forEach { element ->
            val title = element.text().trim()
            val href = element.attr("href")
            val img = element.select("img").attr("src")
            val posterUrl = getHighResLogo(title, img)

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

    // 2. SEARCH: Search across all channels with HD logos
    override suspend fun search(query: String): List<SearchResponse> {
        val searchList = mutableListOf<SearchResponse>()
        val htmlResponse = app.get("$mainUrl/", headers = browserHeaders).text
        val document = Jsoup.parse(htmlResponse)

        document.select("#channels li a").forEach { element ->
            val title = element.text().trim()
            val href = element.attr("href")
            val img = element.select("img").attr("src")
            val posterUrl = getHighResLogo(title, img)

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
        val poster = getHighResLogo(title, "")
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

    // 4. LOAD LINKS: Extract live HLS stream URLs from all available players (Preserved 100% exactly)
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
