package org.example
import java.io.File
import java.text.DecimalFormat
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log
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
        return "${filter.name}:\nScore: $score, CCT: ${cctResult.cct}, " +
                "Duv: ${cctResult.duv}, Rt: ${theaCRIResult.rt}, Y: ${resultingIlluminant.xyz.Y}"
    }
}


fun main() {
    val filterDataLines = File("filters_by_row.txt").readText().split("\n")
    //val baseIlluminant = readArgyllIlluminant("data/NewSoraa_60d_1_Lee400Cold.sp")
//    val baseIlluminant = readArgyllIlluminant("/mnt/argyll/Illuminants/6000kcheapled.sp")
    val baseIlluminant = readArgyllIlluminant("/mnt/argyll/Illuminants/HiLine_6.5w_4000k_36d_HotLee400.sp")
//    val baseIlluminant = readArgyllIlluminant("data/Soraa_5000k_Twosnapped_Lee400Hot.sp")
    println(cricalc.calculateRt(baseIlluminant))
    val target_cct: CCT = 5900.0
    val idealIlluminant = spectrumGenerator.getDaylightSpectrumFromCCT(target_cct)
    val baseIlluminantCCT = baseIlluminant.cct
    val target_duv = idealIlluminant.cct.duv
    val idealYuv = idealIlluminant.yuv
    val idealCCTduv = 0.0010//idealIlluminant.cct.duv
    val yuvTolerance = 0.01
    val cctTolerance = 100
    val duvTolerance = 0.0011
    val outResults = mutableListOf<FilterTestResult>()
    val filters = filterDataLines
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
            it.name != "name"
        }
//        .take(50)
        .map {gel ->
            val dilutes = (0..20).map { 2.0.pow(-it / 2.5) }
            dilutes.map { gel.dilute(it) }
        }
        .flatten()
        .filter {
            val duv = calcUVColourDifference(it.d65xyz, D65.xyz)
            abs(duv) in 0.0011..0.1
//            true
        }
        .filter {
            it.getXYZ(baseIlluminant).Y > 0.42
        }
    println(filters.count())
//        .take(200)

    for (a in 0..<filters.count()){
        val count = outResults.count()
        if (a % 100 == 0) println("$a (found $count)")
        for (b in a..<filters.count()) {
            if (a == b) continue
            val filter_a = filters[a]
            val filter_b = filters[b]
            val compositeFilter = filter_b + filter_a
            val filteredSpectrum = compositeFilter.getFilteredSpectrum(baseIlluminant)
            val illum = Illuminant(filteredSpectrum, "Base filtered by ${compositeFilter.name}")

            if (illum.xyz.Y < 0.45) continue

            if (abs(cieCalculator.uvColourDifference(idealYuv, illum.yuv)) > yuvTolerance) continue

            val cctresult = illum.cct
            if (abs(cctresult.duv - target_duv) > duvTolerance) continue
            if (abs(cctresult.cct - target_cct) > cctTolerance) continue

//            if (cricalc.calculateRt(illum, idealIlluminant).rt < 83) continue

            val rt = cricalc.calculateRt(illum)
            if (rt.rt < 90) continue

            if (abs(cctresult.duv - idealCCTduv) > yuvTolerance) continue
            val weight_Rt = 2.0
            val weight_Y = 1.0
            val miredShift = 1e6 / cctresult.cct - 1e6 / baseIlluminantCCT.cct
            val miredComp = 2.0.pow(-abs(miredShift) / 170.0)
            val score = (1.0 - illum.xyz.Y).pow(2) * 95 * weight_Y +
                    ((100 - rt.rt) * 0.5).pow(2) * weight_Rt
            compositeFilter.score = score * miredComp
            val result = FilterTestResult(compositeFilter, illum, compositeFilter.score, cctresult, rt)
            outResults.addLast(result)
            }
        }
    val sortedFilters = outResults.sortedBy { -(it as FilterTestResult).score }
    sortedFilters.takeLast(10).forEach {
        println(it)
    }
    writeTSV(sortedFilters.takeLast(8).map { (it as FilterTestResult).filter })
    println("Found ${filters.count()} filters")
    println(clearFilter)
    println(D65.xyz)
    println(E.xyz)

}
