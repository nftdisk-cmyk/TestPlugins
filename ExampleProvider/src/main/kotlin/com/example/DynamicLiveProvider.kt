package com.example

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.Jsoup

class DynamicLiveProvider : MainAPI() {
    override var name = "Netspor Live"
    override var mainUrl = "https://netspor70.top"
    override var lang = "tr"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Live)

    override val mainPage = mainPageOf(
        Pair("channels", "7/24 TV Kanallari"),
        Pair("football", "Futbol Maclari"),
        Pair("basketball", "Basketbol"),
        Pair("other", "Tenis & Diger Sporlar"),
        Pair("all", "Tum Yayinlar")
    )

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        fun decodeBase64(input: String): String {
            val clean = input.trim()
            val pad = clean.length % 4
            val padded = if (pad > 0) clean + "=".repeat(4 - pad) else clean
            return try {
                String(Base64.decode(padded, Base64.DEFAULT), Charsets.UTF_8)
            } catch (e: Throwable) {
                try {
                    String(java.util.Base64.getDecoder().decode(padded), Charsets.UTF_8)
                } catch (e2: Throwable) {
                    ""
                }
            }
        }

        fun getChannelLogo(name: String, fallback: String): String {
            val lower = name.lowercase()
            return when {
                "bein sports 1" in lower || "bein 1" in lower ->
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1a/BeIN_Sports_1_logo.svg/512px-BeIN_Sports_1_logo.svg.png"
                "bein sports 2" in lower ->
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ae/BeIN_Sports_2_logo.svg/512px-BeIN_Sports_2_logo.svg.png"
                "bein sports 3" in lower ->
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d4/BeIN_Sports_3_logo.svg/512px-BeIN_Sports_3_logo.svg.png"
                "bein sports 4" in lower ->
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/3/36/BeIN_Sports_4_logo.svg/512px-BeIN_Sports_4_logo.svg.png"
                "bein sports 5" in lower ->
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/8/87/BeIN_Sports_5_logo.svg/512px-BeIN_Sports_5_logo.svg.png"
                "bein sports max 1" in lower ->
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e5/BeIN_Sports_Max_1_logo.svg/512px-BeIN_Sports_Max_1_logo.svg.png"
                "bein sports max 2" in lower ->
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cd/BeIN_Sports_Max_2_logo.svg/512px-BeIN_Sports_Max_2_logo.svg.png"
                "s sport 2" in lower ->
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f6/S_Sport_2_logo.png/512px-S_Sport_2_logo.png"
                "s sport" in lower ->
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/2/23/S_Sport_logo.png/512px-S_Sport_logo.png"
                "trt spor" in lower ->
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/6/6f/TRT_Spor_logo.svg/512px-TRT_Spor_logo.svg.png"
                "trt 1" in lower ->
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/0/01/TRT_1_logo_%282021%29.svg/512px-TRT_1_logo_%282021%29.svg.png"
                "a spor" in lower ->
                    "https://upload.wikimedia.org/wikipedia/tr/thumb/8/82/A_Spor_logo.png/512px-A_Spor_logo.png"
                else -> fallback
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val headers = mapOf(
            "User-Agent" to USER_AGENT,
            "Referer" to "$mainUrl/"
        )

        val html = try {
            app.get(mainUrl, headers = headers).text
        } catch (e: Exception) {
            return newHomePageResponse(request, emptyList())
        }

        val doc = Jsoup.parse(html)
        val channelList = mutableListOf<SearchResponse>()
        val seen = HashSet<String>()

        val links = doc.select("a[href*=/canli-mac/]")
        for (a in links) {
            val href = a.attr("href").trim()
            val fullUrl = if (href.startsWith("http")) href else "${mainUrl.trimEnd('/')}/${href.trimStart('/')}"
            if (!seen.add(fullUrl)) continue

            val rawTitle = a.text().replace("▶", "").trim()
            if (rawTitle.isEmpty()) continue

            val img = a.selectFirst("img")?.attr("src").orEmpty()
            val lowerHref = fullUrl.lowercase()

            val isChannel = lowerHref.contains("bein") ||
                    lowerHref.contains("s-sport") ||
                    lowerHref.contains("trt-spor") ||
                    lowerHref.contains("trt-1") ||
                    lowerHref.contains("a-spor") ||
                    lowerHref.contains("tivibu") ||
                    lowerHref.contains("smart") ||
                    lowerHref.contains("tv8")

            val isFootball = lowerHref.contains("futbol")
            val isBasketball = lowerHref.contains("basketbol")
            val isOther = lowerHref.contains("tenis") || (!isChannel && !isFootball && !isBasketball)

            val matchesCategory = when (request.data) {
                "channels"   -> isChannel
                "football"   -> isFootball && !isChannel
                "basketball" -> isBasketball && !isChannel
                "other"      -> isOther && !isChannel
                else         -> true
            }

            if (matchesCategory) {
                val logo = getChannelLogo(rawTitle, img)
                channelList.add(
                    newLiveSearchResponse(
                        name = rawTitle,
                        url = fullUrl,
                        type = TvType.Live
                    ) {
                        this.posterUrl = logo
                    }
                )
            }
        }

        return newHomePageResponse(request, channelList)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val headers = mapOf(
            "User-Agent" to USER_AGENT,
            "Referer" to "$mainUrl/"
        )

        val html = try {
            app.get(mainUrl, headers = headers).text
        } catch (e: Exception) {
            return emptyList()
        }

        val doc = Jsoup.parse(html)
        val results = mutableListOf<SearchResponse>()
        val q = query.lowercase().trim()
        val seen = HashSet<String>()

        val links = doc.select("a[href*=/canli-mac/]")
        for (a in links) {
            val href = a.attr("href").trim()
            val fullUrl = if (href.startsWith("http")) href else "${mainUrl.trimEnd('/')}/${href.trimStart('/')}"
            if (!seen.add(fullUrl)) continue

            val rawTitle = a.text().replace("▶", "").trim()
            if (rawTitle.lowercase().contains(q)) {
                val img = a.selectFirst("img")?.attr("src").orEmpty()
                val logo = getChannelLogo(rawTitle, img)
                results.add(
                    newLiveSearchResponse(
                        name = rawTitle,
                        url = fullUrl,
                        type = TvType.Live
                    ) {
                        this.posterUrl = logo
                    }
                )
            }
        }

        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val headers = mapOf(
            "User-Agent" to USER_AGENT,
            "Referer" to "$mainUrl/"
        )

        val doc = try {
            app.get(url, headers = headers).document
        } catch (e: Exception) {
            null
        }

        val title = doc?.selectFirst("h1, .match-title, title")?.text()
            ?.replace("| Canlı Maç İzle", "")
            ?.replace("▶", "")
            ?.trim()
            ?: url.substringAfterLast("/").replace("-", " ").uppercase()

        return newLiveStreamLoadResponse(
            name = title,
            url = url,
            dataUrl = url
        ) {
            this.plot = "Netspor Canli Yayin"
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val baseHeaders = mutableMapOf(
            "User-Agent" to USER_AGENT,
            "Referer" to "$mainUrl/"
        )

        // 1. Fetch match page
        val mainHtml = try {
            app.get(data, headers = baseHeaders).text
        } catch (e: Exception) {
            return false
        }

        val watchPathMatch = Regex("""<iframe[^>]+src=["'](/(?:channel/watch/|loadstream)[^"'\\s>]+)""").find(mainHtml)
            ?: Regex("""<iframe[^>]+id=["']playerFrame["'][^>]+src=["']([^"'\\s>]+)""").find(mainHtml)

        val rawWatchPath = watchPathMatch?.groupValues?.get(1) ?: return false
        val watchUrl = if (rawWatchPath.startsWith("http")) rawWatchPath else "${mainUrl.trimEnd('/')}/${rawWatchPath.trimStart('/')}"

        // 2. Fetch watch page (if not directly loadstream)
        val loadUrl = if (watchUrl.contains("loadstream")) {
            watchUrl
        } else {
            baseHeaders["Referer"] = data
            val watchHtml = try {
                app.get(watchUrl, headers = baseHeaders).text
            } catch (e: Exception) {
                return false
            }

            val loadPathMatch = Regex("""<iframe[^>]+src=["'](/loadstream[^"'\\s>]+)""").find(watchHtml)
                ?: Regex("""<iframe[^>]+src=["']([^"'\\s>]*loadstream[^"'\\s>]*)""").find(watchHtml)

            val rawLoadPath = loadPathMatch?.groupValues?.get(1) ?: return false
            if (rawLoadPath.startsWith("http")) rawLoadPath else "${mainUrl.trimEnd('/')}/${rawLoadPath.trimStart('/')}"
        }

        // 3. Fetch loadstream.php
        baseHeaders["Referer"] = watchUrl
        val loadHtml = try {
            app.get(loadUrl, headers = baseHeaders).text
        } catch (e: Exception) {
            return false
        }

        // 4. Extract real m3u8
        var m3u8Url: String? = null

        // Direct check
        val directMatch = Regex("""https?://[^\s"'<>]+\.m3u8[^\s"'<>]*""").find(loadHtml)
        if (directMatch != null && !directMatch.value.contains("bsky.app") && !directMatch.value.contains("preroll")) {
            m3u8Url = directMatch.value
        }

        // Base64 check ('...|atob')
        if (m3u8Url == null) {
            val b64Match = Regex("""['"]([A-Za-z0-9+/=]{30,})\|atob""").find(loadHtml)
            if (b64Match != null) {
                val decoded = decodeBase64(b64Match.groupValues[1])
                val decodedM3u8 = Regex("""https?://[^\s"'<>]+\.m3u8[^\s"'<>]*""").find(decoded)
                if (decodedM3u8 != null) {
                    m3u8Url = decodedM3u8.value
                }
            }
        }

        // Search any base64 chunks
        if (m3u8Url == null) {
            Regex("""[A-Za-z0-9+/=]{40,}""").findAll(loadHtml).forEach { match ->
                if (m3u8Url == null) {
                    val dec = decodeBase64(match.value)
                    val m = Regex("""https?://[^\s"'<>]+\.m3u8[^\s"'<>]*""").find(dec)
                    if (m != null && !m.value.contains("bsky.app") && !m.value.contains("preroll")) {
                        m3u8Url = m.value
                    }
                }
            }
        }

        if (m3u8Url.isNullOrEmpty()) return false

        callback.invoke(
            ExtractorLink(
                source = this.name,
                name = this.name,
                url = m3u8Url,
                referer = "$mainUrl/",
                quality = Qualities.P1080.value,
                isM3u8 = true,
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to "$mainUrl/",
                    "Origin" to mainUrl
                )
            )
        )
        return true
    }
}
