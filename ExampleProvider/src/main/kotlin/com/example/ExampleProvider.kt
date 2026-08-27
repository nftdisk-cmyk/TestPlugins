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
    override var name = "Bushido TV"
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
    private val rawLogoBase = "https://raw.githubusercontent.com/nftdisk-cmyk/TestPlugins/master/logos_hd"

    private val browserHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer"    to "$mainUrl/",
        "Origin"     to mainUrl,
        "Accept"     to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
    )

    /**
     * Maps channel names to custom high-definition crystal clear logos.
     * Uses cache-busting (?v=13) to ensure Android TV downloads fresh images immediately.
     */
    private fun getHighResLogo(title: String, defaultImg: String): String {
        val lower = title.lowercase()
        val logoFile = when {
            // 1. BNT family
            lower.contains("bnt 1") -> "bnt1.png"
            lower.contains("bnt 2") -> "bnt2.png"
            lower.contains("bnt 3") -> "bnt3.png"
            lower.contains("bnt 4") -> "bnt4.png"

            // 2. bTV family
            lower.contains("btv action") -> "btv_action.png"
            lower.contains("btv cinema") -> "btv_cinema.png"
            lower.contains("btv comedy") -> "btv_comedy.png"
            lower.contains("btv story") -> "btv_story.png"
            lower.contains("btv") -> "btvhd.png"

            // 3. Nova family
            lower.contains("nova news") -> "nova_news.png"
            lower.contains("nova sport") -> "nova_sport.png"
            lower.contains("kino nova") -> "kino_nova.png"
            lower.contains("nova") -> "nova.png"

            // 4. Diema family
            lower.contains("diema sport 3") -> "diema_sport_3.png"
            lower.contains("diema sport 2") -> "diema_sport_2.png"
            lower.contains("diema sport") -> "diema_sport.png"
            lower.contains("diema family") -> "diema_family.png"
            lower.contains("diema") -> "diema.png"

            // 5. Max Sport family
            lower.contains("max sport 1") -> "MAX_SPORT1_HD.png"
            lower.contains("max sport 2") -> "MAX_SPORT2_HD.png"
            lower.contains("max sport 3") -> "MAX_SPORT3_HD.png"
            lower.contains("max sport 4") -> "MAX_SPORT4_HD.png"
            lower.contains("max one") -> "MAX_ONE_HD.png"

            // 6. Sports international
            lower.contains("eurosport 1") -> "eurosport1.png"
            lower.contains("eurosport 2") -> "eurosport2.png"
            lower.contains("ring") -> "RING_BG_HD.png"

            // 7. Star & AXN & Epic Drama
            lower.contains("star crime") -> "STAR_CRIME_HD.png"
            lower.contains("star life") -> "STAR_LIFE.png"
            lower.contains("star channel") || lower.contains("star") -> "star_channel.png"
            lower.contains("axn black") -> "axn_black.png"
            lower.contains("axn white") -> "axn_white.png"
            lower.contains("axn") -> "axn.png"
            lower.contains("epic drama") -> "EPICDRAMAHD.png"

            // 8. Documentary & Lifestyle
            lower.contains("nat geo wild") -> "nat_geo_wild.png"
            lower.contains("nat geo") -> "nat_geo.png"
            lower.contains("discovery") -> "discovery.png"
            lower.contains("viasat") -> "viasat_explore.png"
            lower.contains("id xtra") -> "ID_XTRA_HD.png"
            lower.contains("travel channel") -> "travel_channel.png"
            lower.contains("travel tv") -> "TRAVEL_TV.png"
            lower.contains("24 kitchen") || lower.contains("food network") -> "food_network.png"
            lower.contains("tlc") -> "tlc.png"
            lower.contains("code fashion") -> "codefashiontv.png"

            // 9. Kids
            lower.contains("disney") -> "disney_channel.png"
            lower.contains("cartoon") -> "cartoon_network.png"
            lower.contains("nick jr") -> "nick_jr.png"
            lower.contains("nicktoons") -> "nicktoons.png"
            lower.contains("nickelodeon") -> "nickelodeon.png"
            lower.contains("e kids") || lower.contains("ekids") -> "EKIDS.png"

            // 10. News & Business
            lower.contains("bloomberg") -> "bloomberg.png"
            lower.contains("euronews") -> "euronews.png"

            // 11. Bulgarian National & Regional
            lower.contains("bulgaria on air") -> "bulgaria_on_air.png"
            lower.contains("kanal 3") -> "kanal3.png"
            lower.contains("78 tv") || lower.contains("7/8 tv") -> "78tv.png"
            lower.contains("skat") -> "skat.png"
            lower.contains("vtk") -> "vtk.png"
            lower.contains("evrokom") -> "evrokom.png"

            // 12. Music & Folk
            lower.contains("city") -> "citytv.png"
            lower.contains("the voice") -> "THE_VOICE.png"
            lower.contains("planeta") -> "PLANETA_HD.png"
            lower.contains("folklor") -> "RODINA_TV.png"
            lower.contains("tiankov") -> "TIANKOV_TV.png"
            lower.contains("rodina") -> "RODINA_TV.png"
            lower.contains("dstv") -> "dstv.png"

            else -> null
        }

        return if (logoFile != null) {
            "$rawLogoBase/$logoFile?v=14"
        } else {
            if (defaultImg.isNotEmpty()) fixUrl(defaultImg) else defaultPoster
        }
    }

    // 1. MAIN PAGE: List all live channels with categories and crystal clear HD logos
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
            "Bushido TV Canlı Akışı."
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
