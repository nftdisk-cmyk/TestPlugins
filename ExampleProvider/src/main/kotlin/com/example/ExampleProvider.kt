package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.Jsoup
import java.util.ArrayList

// Sınıf adını şablon yapısına uygun olarak ExampleProvider olarak koruyoruz
class ExampleProvider : MainAPI() {
    override var mainUrl = "https://inattv1321.xyz"
    override var name = "İnat TV"
    override val supportedTypes = setOf(TvType.Live)
    override var lang = "tr"
    override val hasMainPage = true

    // Ekran görüntüsündeki 7/24 TV sekmesini hedefliyoruz
    override val mainPage = mainPageOf(Pair("7-24", "7/24 Canlı TV"))

    private val requestHeaders = mapOf(
    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Referer" to "${mainUrl}/",
    "X-Requested-With" to "XMLHttpRequest",
    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
    "Accept-Language" to "en-US,en;q=0.9,tr;q=0.8"
)

    // 1. ANA SAYFA KAZIMA: 7/24 sekmesindeki tüm canlı kanalları (beIN Sports vb.) listeler
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val channelList = ArrayList<SearchResponse>()
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
        return newHomePageResponse(request.name, channelList)
    }

    // 2. ARAMA FONKSİYONU: Maç veya kanal ara kutusuna yazılan sorguları işler
    override suspend fun search(query: String): List<SearchResponse> {
        val searchList = ArrayList<SearchResponse>()
        val htmlResponse = app.get("$mainUrl/?s=${query.trim()}", headers = requestHeaders).text
        val document = Jsoup.parse(htmlResponse)

        document.select("div.channel-card, a.channel-item").forEach { element ->
            val title = element.select(".channel-name").text().trim()
            val channelUrl = element.attr("href")
            val poster = element.select("img").attr("src")

            if (title.lowercase().contains(query.lowercase())) {
                searchList.add(
                    newLiveSearchResponse(name = title, url = fixUrl(channelUrl), type = TvType.Live) {
                        this.posterUrl = fixUrl(poster)
                    }
                )
            }
        }
        return searchList
    }

    // 3. DETAY SAYFASI: Kanala tıklandığında oynatıcı arayüzünü hazırlar
    override suspend fun load(url: String): LoadResponse {
        val htmlResponse = app.get(url, headers = requestHeaders).text
        val document = Jsoup.parse(htmlResponse)
        val title = document.select("h1.entry-title, .channel-title").text().trim().ifEmpty { "Canlı Kanal" }
        val poster = document.select("meta[property=og:image]").attr("content")

        return newLiveLoadResponse(
            name = title,
            url = url,
            type = TvType.Live,
            dataUrl = url
        ) {
            this.posterUrl = poster
            this.plot = "$title 7/24 Kesintisiz Canlı TV yayını."
        }
    }

    // 4. GİZLİ VİDEO LİNKİNİ AYIKLAMA: Sayfa arkasındaki ham .m3u8 linkini yakalar
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val pageHtml = app.get(data, headers = requestHeaders).text
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
        // If no direct .m3u8 link is found, construct it from the channel ID
        if (matchedUrl.isNullOrEmpty()) {
            // Extract the channel ID from the URL query parameter "id"
            val uri = android.net.Uri.parse(data)
            val channelId = uri.getQueryParameter("id")
            if (!channelId.isNullOrEmpty()) {
                val fallbackUrl = "https://d72577a9dd0ec71.cfd/${channelId}/mono.m3u8"
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
        }
    }
}
