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
    private val rawLogoBase = "https://raw.githubusercontent.com/nftdisk-cmyk/TestPlugins/master/logos"

    private val browserHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer"    to "$mainUrl/",
        "Origin"     to mainUrl,
        "Accept"     to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
    )

    /**
     * Maps channel names to custom high-definition crystal clear logos.
     * Falls back to the website thumbnail if no custom HD logo is matched.
     */
    private fun getHighResLogo(title: String, defaultImg: String): String {
        val lower = title.lowercase()
        return when {
            // 1. BNT family
            lower.contains("bnt 1") -> "$rawLogoBase/bnt1.png"
            lower.contains("bnt 2") -> "$rawLogoBase/bnt2.png"
            lower.contains("bnt 3") -> "$rawLogoBase/bnt3.png"
            lower.contains("bnt 4") -> "$rawLogoBase/bnt4.png"

            // 2. bTV family
            lower.contains("btv action") -> "$rawLogoBase/btv_action.png"
            lower.contains("btv cinema") -> "$rawLogoBase/btv_cinema.png"
            lower.contains("btv comedy") -> "$rawLogoBase/btv_comedy.png"
            lower.contains("btv story") -> "$rawLogoBase/btv_story.png"
            lower.contains("btv") -> "$rawLogoBase/btvhd.png"

            // 3. Nova family
            lower.contains("nova news") -> "$rawLogoBase/nova_news.png"
            lower.contains("nova sport") -> "$rawLogoBase/nova_sport.png"
            lower.contains("kino nova") -> "$rawLogoBase/kino_nova.png"
            lower.contains("nova") -> "$rawLogoBase/nova.png"

            // 4. Diema family
            lower.contains("diema sport 3") -> "$rawLogoBase/diema_sport_3.png"
            lower.contains("diema sport 2") -> "$rawLogoBase/diema_sport_2.png"
            lower.contains("diema sport") -> "$rawLogoBase/diema_sport.png"
            lower.contains("diema family") -> "$rawLogoBase/diema_family.png"
            lower.contains("diema") -> "$rawLogoBase/diema.png"

            // 5. Max Sport family
            lower.contains("max sport 1") -> "$rawLogoBase/MAX_SPORT1_HD.png"
            lower.contains("max sport 2") -> "$rawLogoBase/MAX_SPORT2_HD.png"
            lower.contains("max sport 3") -> "$rawLogoBase/MAX_SPORT3_HD.png"
            lower.contains("max sport 4") -> "$rawLogoBase/MAX_SPORT4_HD.png"
            lower.contains("max one") -> "$rawLogoBase/MAX_ONE_HD.png"

            // 6. Sports international
            lower.contains("eurosport 1") -> "$rawLogoBase/eurosport1.png"
            lower.contains("eurosport 2") -> "$rawLogoBase/eurosport2.png"
            lower.contains("ring") -> "$rawLogoBase/RING_BG_HD.png"

            // 7. Star & AXN & Epic Drama
            lower.contains("star crime") -> "$rawLogoBase/STAR_CRIME_HD.png"
            lower.contains("star life") -> "$rawLogoBase/STAR_LIFE.png"
            lower.contains("star channel") || lower.contains("star") -> "$rawLogoBase/star_channel.png"
            lower.contains("axn black") -> "$rawLogoBase/axn_black.png"
            lower.contains("axn white") -> "$rawLogoBase/axn_white.png"
            lower.contains("axn") -> "$rawLogoBase/axn.png"
            lower.contains("epic drama") -> "$rawLogoBase/EPICDRAMAHD.png"

            // 8. Documentary & Lifestyle
            lower.contains("nat geo wild") -> "$rawLogoBase/nat_geo_wild.png"
            lower.contains("nat geo") -> "$rawLogoBase/nat_geo.png"
            lower.contains("discovery") -> "$rawLogoBase/discovery.png"
            lower.contains("viasat") -> "$rawLogoBase/viasat_explore.png"
            lower.contains("id xtra") -> "$rawLogoBase/ID_XTRA_HD.png"
            lower.contains("travel channel") -> "$rawLogoBase/travel_channel.png"
            lower.contains("travel tv") -> "$rawLogoBase/TRAVEL_TV.png"
            lower.contains("24 kitchen") || lower.contains("food network") -> "$rawLogoBase/food_network.png"
            lower.contains("tlc") -> "$rawLogoBase/tlc.png"
            lower.contains("code fashion") -> "$rawLogoBase/codefashiontv.png"

            // 9. Kids
            lower.contains("disney") -> "$rawLogoBase/disney_channel.png"
            lower.contains("cartoon") -> "$rawLogoBase/cartoon_network.png"
            lower.contains("nick jr") -> "$rawLogoBase/nick_jr.png"
            lower.contains("nicktoons") -> "$rawLogoBase/nicktoons.png"
            lower.contains("nickelodeon") -> "$rawLogoBase/nickelodeon.png"
            lower.contains("e kids") || lower.contains("ekids") -> "$rawLogoBase/EKIDS.png"

            // 10. News & Business
            lower.contains("bloomberg") -> "$rawLogoBase/bloomberg.png"
            lower.contains("euronews") -> "$rawLogoBase/euronews.png"

            // 11. Bulgarian National & Regional
            lower.contains("bulgaria on air") -> "$rawLogoBase/bulgaria_on_air.png"
            lower.contains("kanal 3") -> "$rawLogoBase/kanal3.png"
            lower.contains("78 tv") || lower.contains("7/8 tv") -> "$rawLogoBase/78tv.png"
            lower.contains("skat") -> "$rawLogoBase/skat.png"
            lower.contains("vtk") -> "$rawLogoBase/vtk.png"
            lower.contains("evrokom") -> "$rawLogoBase/evrokom.png"

            // 12. Music & Folk
            lower.contains("city") -> "$rawLogoBase/citytv.png"
            lower.contains("the voice") -> "$rawLogoBase/THE_VOICE.png"
            lower.contains("planeta") -> "$rawLogoBase/PLANETA_HD.png"
            lower.contains("folklor") -> "$rawLogoBase/RODINA_TV.png"
            lower.contains("tiankov") -> "$rawLogoBase/TIANKOV_TV.png"
            lower.contains("rodina") -> "$rawLogoBase/RODINA_TV.png"
            lower.contains("dstv") -> "$rawLogoBase/dstv.png"

            else -> if (defaultImg.isNotEmpty()) fixUrl(defaultImg) else defaultPoster
        }
    }

    // 1. MAIN PAGE: List all live channels with categories and 256x256 HD logos
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

    // 2. SEARCH: Search across all channels with 256x256 HD logos
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
