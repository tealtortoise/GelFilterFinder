package org.example
import java.io.File
import java.text.DecimalFormat
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow

fun writeTSV(filters: List<GelFilter>) {
    val fmt = DecimalFormat("0.###E0")
    File("filtersfiltered.txt").printWriter().use { writer ->
        writer.println("nm\t" + filters.map { it.name }.joinToString(separator = "\t"))
        val wavelengths = cieCalculator.wavelengthData5nm
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

class FilterTestResult(val filter: GelFilter,val resultingIlluminant: Illuminant,
                       val score: Double, val cctResult: CCTResult,
    val theaCRIResult: TheaCRIResult){
    override fun toString(): String {
        return "${filter.name}: Score: $score, CCT: ${cctResult.cct}, " +
                "Duv: ${cctResult.duv}, Rt: ${theaCRIResult.rt}, Y: ${resultingIlluminant.xyz.Y}"
    }
}


fun main() {
    val filterDataLines = File("filters_by_row.txt").readText().split("\n")
//    val baseIlluminant = readArgyllIlluminant("data/NewSoraa_60d_1_Lee400Cold.sp")
    val baseIlluminant = readArgyllIlluminant("data/Soraa_5000k_Twosnapped_Lee400Hot.sp")
    val target_cct: CCT = 6500.0
    val target_duv: Duv = 0.0025
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
//            it.hue < -0.3 * PI && it.hue > -0.7 * PI
            true
        }
        .map {gel ->
            (1..10).map { gel.dilute(it * 0.1) }
        }
        .flatten()
        .map {
            val filteredSpecrum = it.getFilteredSpectrum(baseIlluminant)
            val illum = Illuminant(filteredSpecrum,"Base filtered by ${it.name}")

            val cctresult = illum.cct
            if (cctresult.cct in 2200.0..20000.0) {
                val rt = cricalc.calculateRt(illum)
                val weight_CCT = 0.1
                val weight_Duv = 1.0
                val weight_Rt = 1.5
                val weight_Y = 0.2
                val score = ((cctresult.cct - target_cct) / 100).pow(2) * weight_CCT +
                        ((cctresult.duv - target_duv) * 1000).pow(2) * weight_Duv +
                        ((100 - rt.rt) * 0.5).pow(2) * weight_Rt +
                        (1.0 - illum.xyz.Y) * 2 * weight_Y
                it.score = score
                FilterTestResult(it, illum, score, cctresult, rt)
            } else {
                null
            }
        }.filter {
            it != null
        }
    val sortedFilters = filters.sortedBy { (it as FilterTestResult).score }
    sortedFilters.take(50).forEach { println(it) }
    writeTSV(sortedFilters.take(50).map { (it as FilterTestResult).filter })
    println("Found ${filters.count()} filters")
    println(clearFilter)
    println(D65.xyz)
    println(E.xyz)

}
