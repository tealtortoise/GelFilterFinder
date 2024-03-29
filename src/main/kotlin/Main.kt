package org.example

import com.mohamedrejeb.ksoup.html.parser.KsoupHtmlHandler
import com.mohamedrejeb.ksoup.html.parser.KsoupHtmlParser
import java.io.File
import org.http4k.client.ApacheClient
import org.http4k.core.Request
import org.http4k.core.Method
import java.nio.file.Paths
import java.text.DecimalFormat
import kotlin.io.path.exists

fun httpGet(uri: String): String {
    val client = ApacheClient()
    val request = Request(Method.GET, uri)
    val body = client(request).bodyString()

    Thread.sleep(1000)
    return body
}

class SpectralPoint(private var webString: String, idx: Int) {
    var transmission: Double
    var wavelength: Double

    init {
        val coordList = webString.split(" ")
        if (coordList.count() != 2) {
            throw Exception("Unexpected number of numbers (\"$webString\" : ${coordList.count()})")
        }
        val y = coordList[1].toDouble()
        this.wavelength = 400 + idx * 5.0
        this.transmission = 1.0 - y * 0.01
    }

    override fun toString(): String {
        return "Spectral Point ${this.wavelength}nm : Transmission: ${this.transmission}%"
    }
}

data class ScrapeResult (val filterName: String, val pointList: List<SpectralPoint>)

fun fetchURIWithCache(uri: String, mock:Boolean=false): String {
    val location = uri.substringAfter(".com/colour/").substringBefore('/')
    if (mock) {
        var body = ""
        File("src/main/kotlin/leefilter.html").forEachLine { body += it }
        return body
    }
    val path = Paths.get("pagecache/$location.html")
    if (path.exists()) {
        return path.toFile().readText()
    }
//    throw Exception("Not in Cache")
    println("WARNING: Fetching $uri over network")
    val html = httpGet(uri)
    path.toFile().writeText(html)
    return html
}

fun processHTML(html: String): ScrapeResult {
    var pointList: List<SpectralPoint> = emptyList()
    var filterName: String = "Unknown"
    var ish1: Boolean = true
    val handler = KsoupHtmlHandler
        .Builder()
        .onOpenTag { tagname, attributes, isImplied ->
            if (tagname == "path" && attributes.get("stroke-width") == "2") {
                val pathString = attributes.get("d")
                if (pathString?.get(0) == 'M') {
                    val vertexList = pathString.substring(2).split(" L ")
                    pointList = vertexList.mapIndexed() { idx, str -> SpectralPoint(str, idx) }
                }
            }
            else if (tagname == "h1"){
                ish1 = true
            }
        }
        .onCloseTag { name, isImplied ->
            if (name == "h1") ish1 = false
        }
        .onText { if (ish1) filterName = it }
        .build()

    val ksoupHtmlParser = KsoupHtmlParser(handler = handler)
    ksoupHtmlParser.write(html)
    ksoupHtmlParser.end()

//    println("filtername $filterName")
    return ScrapeResult(filterName, pointList)
}

fun getFilterURIs(): List<String>{
    val filterListURI = "https://leefilters.com/lighting/colour-effect-lighting-filters/"
    val html = fetchURIWithCache(filterListURI)
    val uriList: MutableList<String> = mutableListOf()
    val handler = KsoupHtmlHandler.Builder()
        .onOpenTag { tagname, attributes, isImplied ->
            if (tagname == "a" && attributes.get("class") == "name") {
                uriList.add(attributes.getOrDefault("href", ""))
            }
        }
        .build()
    val ksoupHtmlParser = KsoupHtmlParser(handler = handler)
    ksoupHtmlParser.write(html)
    ksoupHtmlParser.end()
    println("Found ${uriList.count()} filter URIs")
    return uriList
}

fun main() {
    val uriList = getFilterURIs()

    val filters: List<ScrapeResult> = uriList.map {
        processHTML(fetchURIWithCache(it, mock=false))
    }
        .filter {it.pointList.count() > 10}

//    filters.forEach {
//        println("${it.filterName}, ${it.pointList.count()}")
//    }

    val fmt = DecimalFormat("0.###E0")
    File("filter.txt").printWriter().use { writer ->
        writer.println("nm\t" + filters.map { it.filterName }.joinToString(separator = "\t"))
        val wavelengths = filters[0].pointList.map { it.wavelength }

        wavelengths.forEachIndexed { idx, wavelength ->
            val line = "$wavelength\t" + filters.map { fmt.format(it.pointList.get(idx).transmission) }
                .joinToString(separator = "\t")
            writer.println(line)
        }

    }

    File("filters_by_row.txt").printWriter().use { writer ->
        val headers = filters.get(0).pointList.map {it.wavelength}.joinToString(separator = "\t")
        writer.println("name\t" + headers)
        filters.forEach {
            val line = it.pointList.map { fmt.format(it.transmission) }.joinToString(separator = "\t")
            writer.println(it.filterName + "\t" + line)
        }

    }
//        writer.println("nm\t${scrapeResult.filterName}")
//        scrapeResult.pointList.forEach {
//            println(it)
//            val lineStr = "%d\t%e".format((it.wavelength+0.5).toInt(), it.transmission)
//            writer.println(lineStr)
//        }
//    }

}