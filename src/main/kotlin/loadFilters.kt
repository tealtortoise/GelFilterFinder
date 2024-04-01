package org.example
import org.example.CIECalculator
import org.example.CieXYZ
import org.example.GelFilter
import org.example.clearFilter
import org.example.E
import java.io.File
import java.text.DecimalFormat
import kotlin.math.PI

fun writeTSV(filters: List<GelFilter>) {
    val fmt = DecimalFormat("0.###E0")
    File("filtersfiltered.txt").printWriter().use { writer ->
        writer.println("nm\t" + filters.map { it.name }.joinToString(separator = "\t"))
        val wavelengths = calc.wavelengthData5nm
        wavelengths.forEachIndexed { idx, wavelength ->
            val line = "$wavelength\t" + filters.map { fmt.format(it.spectrum.get(idx)) }
                .joinToString(separator = "\t")
            writer.println(line)
        }

    }
}
fun mainb() {

    val filterDataLines = File("filters_by_row.txt").readText().split("\n")
    var outfilters: MutableList<GelFilter> = mutableListOf()
    val filters = filterDataLines.takeLast(filterDataLines.count()-1)
        .filter {
        it.count() > 1
    }
        .map { line ->
            val split = line.split("\t")
            val gel = GelFilter(split[0],
                split.subList(1, split.count()).map { it.toDouble() })
            gel
        }
        .filter {
            it.name.contains("Steel")
        }

    filters.forEach {
        println(it)
        outfilters.addLast(it)
        outfilters.addLast(it.dilute(0.5))
        outfilters.addLast(it.dilute(0.25))
        outfilters.addLast(it.dilute(0.125))
    }
    writeTSV(outfilters)
    println("Found ${filters.count()} filters")
}
fun main() {
    val filterDataLines = File("filters_by_row.txt").readText().split("\n")
    val filters = filterDataLines.takeLast(filterDataLines.count()-1)
        .filter {
            it.count() > 1
        }
        .map { line ->
            val split = line.split("\t")
            val gel = GelFilter(split[0],
                split.subList(1, split.count()).map { it.toDouble() })
            gel
        }
        .filter {
            it.hue < -0.3 * PI && it.hue > -0.7 * PI
        }
        .filter {
            it.xyz.Y > 0.1 && it.xyz.Y < 0.82
        }
        .map {

            val sat = it.sat
            if (sat < 19) {
                it
//            } else if (sat < 30) {
//                it.dilute(0.5)
//            } else if (sat < 50) {
//                it.dilute(0.33)
            } else {
                it.dilute(19.0 / it.sat)
            }
        }
    filters.forEach {
        println(it)
    }
    writeTSV(filters)
    println("Found ${filters.count()} filters")
    println(clearFilter)
    println(D65.xyz)
    println(E.xyz)

}
