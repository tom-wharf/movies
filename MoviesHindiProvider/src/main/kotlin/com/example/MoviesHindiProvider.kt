package com.example

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.getCaptchaToken
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addDuration
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
//import com.lagradost.cloudstream3.animeproviders.ZoroProvider
import com.lagradost.cloudstream3.mvvm.suspendSafeApiCall
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.nicehttp.NiceResponse
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.system.measureTimeMillis



class MoviesHindiProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://111.90.159.132/" 
    override var name = "Movies Hindi"
    override val supportedTypes = setOf(TvType.Movie)

    override var lang = "en"

    // enable this when your provider has a main page
    override val hasMainPage = true

    // this function gets called when you search for something
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val html = app.get(url).text
        val document = Jsoup.parse(html)

        return document.select("article.item").map {
            val title = it.select("p.entry-title").text()
            val href = fixUrl(it.select("div.gmr-watch-movie > a").attr("href"))
            // val year = it.select("span.fdi-item").text().toIntOrNull()
            val image = it.select("img").attr("src")

            MovieSearchResponse(
                title,
                href,
                this.name,
                TvType.Movie,
                image,
            )
        }
    }


    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val details = document.select("div.gmr-movie-data")
        val img = details.select("img.attachment-thumbnail")
        val posterUrl = img.attr("src")
        val title = details.select("entry-title").text() ?: throw ErrorLoadingException("No Title")

        /*
        val year = Regex("""[Rr]eleased:\s*(\d{4})""").find(
            document.select("div.elements").text()
        )?.groupValues?.get(1)?.toIntOrNull()
        val duration = Regex("""[Dd]uration:\s*(\d*)""").find(
            document.select("div.elements").text()
        )?.groupValues?.get(1)?.trim()?.plus(" min")*/
        var duration: String? = null// details.select(".gmr-movie-runtime").text().split(":")[1]
        var year: Int? = null
        var tags: List<String>? = null
        var cast: List<String>? = null
        val youtubeTrailer = document.selectFirst("iframe.video__media")?.attr("data-src")
        val rating = document.selectFirst(".gmr-meta-rating > span[itemprop=ratingValue]")?.text().toRatingInt()


        details.select(".gmr-movie-innermeta").forEach { element -> 
            element.select("[class^=gmr-movie]").forEach { t -> 
                var txt: String = t.text()
                
                if(txt.contains("Year")) {
                    year = txt.split(":")[1].toIntOrNull()
                }
                if(txt.contains("Genre")) {
                    tags = t.select("a").mapNotNull { it.text() }
                }
                if(txt.contains("Duration")) {
                    duration = txt.split(":")[1]
                }
            }
        }
        
        details.select("gmr-moviedata").forEach { element -> 
            if(element.text().contains("Cast")) {
                cast = element.select("a").mapNotNull { it.text() }
            }
        }



        val plot = document.select("div.entry-content.entry-content-single > p").text()
        val sourceUrl = document.select("video > source").attr("src")

        return newMovieLoadResponse(title, sourceUrl, TvType.Movie, sourceUrl) {
            this.year = year
            this.posterUrl = posterUrl
            this.plot = plot
            addDuration(duration)
            addActors(cast)
            this.tags = tags
            addTrailer(youtubeTrailer)
            this.rating = rating
        }
    }



    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // println("loadling link $data")
        //-H 'referer: https://111.90.159.132/'

        val authHeader = mapOf("referer" to "$mainUrl")  // refresh crendentials

        callback.invoke (
            ExtractorLink(
                name,
                name,
                data,
                "",  // referer not needed
                Qualities.Unknown.value,
                false,
                authHeader,
            )
        )
        return true
    }



}