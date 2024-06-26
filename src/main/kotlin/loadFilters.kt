package org.example
import java.io.File
import java.text.DecimalFormat
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

class FilterTestResult(val filter: GelFilter, val resultingIlluminant: Illuminant,
                       val score: Double, val cctResult: CCTResult,
                       val theaCRIResult: TheaCRIResult, val criResult: TheaCRIResult){
    override fun toString(): String {
        return "${filter.name}:\nScore: $score, CCT: ${cctResult.cct}, " +
                "Duv: ${cctResult.duv}, Rt: ${theaCRIResult.rt}," +
                "r9: ${criResult.sampleScores[8].second} Y: ${resultingIlluminant.xyz.Y}"
    }
}

class SmallFilterTestResult(val filterName: String, val Y: Double,
                       val score: Double, val cctResult: CCTResult,
                       val theaCRIResult: TheaCRIResult){
    override fun toString(): String {
        return "${filterName}:\nScore: $score, CCT: ${cctResult.cct}, " +
                "Duv: ${cctResult.duv}, Rt: ${theaCRIResult.rt}, Y: ${Y}"
    }
}


fun main() {
    val filterDataLines = File("filters_by_row.txt").readText().split("\n")
    //val baseIlluminant = readArgyllIlluminant("data/NewSoraa_60d_1_Lee400Cold.sp")
//    val baseIlluminant = readArgyllIlluminant("/mnt/argyll/Illuminants/6000kcheapled.sp")
//    val baseIlluminant = readArgyllIlluminant("/mnt/argyll/Illuminants/6000kcheapled.sp")
//    val coldIlluminant = readArgyllIlluminant("/mnt/argyll/Illuminants/Philips_6.2W_6500k_1_Lee400ColfdHR.sp")
//    val hotIlluminant = readArgyllIlluminant("/mnt/argyll/Illuminants/Philips_6.2w_6500K_1_Lee400HotHR.sp")
//    val coldIlluminant = readArgyllIlluminant("/mnt/argyll/Illuminants/NewSoraa_36d_1_Lee400Cold.sp")
//    val hotIlluminant = readArgyllIlluminant("/mnt/argyll/Illuminants/NewSoraa_36d_1d_Lee400Hotish.sp")
    val baseIlluminant = readArgyllIlluminant("/mnt/argyll/Illuminants/Soraa_18.5W_4000k_Par30S_Lee400Hot.sp")
    val hotIlluminant = baseIlluminant
    val coldIlluminant = baseIlluminant
//    baseIlluminant.spectrum.take(5).forEach { println(it) }
//    val first: Pair<Illuminant, Double> = (baseIlluminant, 0.0)
//    val baseIlluminant = mixIlluminants(listOf(Pair(hotIlluminant, 0.5), Pair(coldIlluminant, 0.5)))

//    val baseIlluminant = readArgyllIlluminant("/mnt/argyll/Illuminants/Kosnic_7w_5000k_Lee400ColdHR.sp")
//    val baseIlluminant = readArgyllIlluminant("/mnt/argyll/Illuminants/HiLine_6.5w_4000k_36d_HotLee400.sp")
//    val baseIlluminant = readArgyllIlluminant("data/Soraa_5000k_Twosnapped_Lee400Hot.sp")

    val baseTm30Result = tm30calc.calculateRt(baseIlluminant)
    println(baseTm30Result)
    baseTm30Result.sampleScores.forEach {
        println(it)
    }
    println(cricalc.calculateRt(baseIlluminant).sampleScores[8])

    val target_cct: CCT = 4100.0
    val idealIlluminant = spectrumGenerator.getBlackbodySpectrum(target_cct)
    val target_duv = idealIlluminant.cct.duv
    val idealYuv = idealIlluminant.yuv
    val yuvTolerance = 0.016
    val cctTolerance = 60
    val duvTolerance = 0.0005
    val dilutes = (4..15).map { 2.0.pow(-it / 4.0) }
    val singleFilterYFloor = 0.79
    val stackYFloor = 0.755
    val weight_Rt = 0.50
    val weight_r9 = 0.0
    val weight_38 = 0.0
    val weight_Y = 2.5
    val stackThreeFilters = false
//    val stackThreeFilters = false
    println("Dilutes:" + dilutes.joinToString { "%.3f".format(it)})
    var outResults = mutableListOf<FilterTestResult>()
    val filters = filterDataLines
        .filter {
            it.count() > 1
        }
        .toSet().toList()
//        .take(200)
        .map { line ->
            val split = line.split("\t")
            val gel = GelFilter(split[0],
                split.subList(1, split.count()).map { it.toDouble() })
            gel
        }
        .filter {
            it.name != "name"
        }
        .map {gel ->
            dilutes.map { amt -> if (amt < 0.99) gel.dilute(amt) else gel }
        }
        .flatten()
        .filter {
            val duv = calcUVColourDifference(it.d65xyz, D65.xyz)
            abs(duv) in 0.001..0.1
        }
        .filter {
            it.getXYZ(baseIlluminant).Y > singleFilterYFloor
        }
    println(filters.count())

    val uVFilter = filters.find { it.name.startsWith("226") } ?: GelFilter("", filters[0].spectrum)
//        .take(200)
    var gotToStart = 0
    var gotToComp = 0
    var gotToYCheck = 0
    var gotToYuvTol = 0
    var gotToCCT = 0
    var gotToDuv = 0
    var gotToQuickCRI = 0
    var gotToLongCRI = 0
    var gotToEnd = 0
    for (z in 0..filters.count()) {
        for (a in z..<filters.count()) {
            if (z == a) continue
            if (!stackThreeFilters) if (a % 100 == 0) {
                println("$a... (${gotToEnd})")
                println("Percentages through: Comp %,.0f | YCheck %,.0f | YuvTol %,.0f | CCT %,.0f | Duv %,.0f | QuickCRI %,.0f| LongCRI %,.0f".format(
                    gotToYCheck*100.0/gotToComp,
                    gotToYuvTol*100.0/gotToYCheck,
                    gotToCCT*100.0/gotToYuvTol,
                    gotToDuv*100.0/gotToCCT,
                    gotToQuickCRI*100.0/gotToDuv,
                    gotToLongCRI*100.0/gotToQuickCRI,
                    gotToEnd*100.0/gotToLongCRI
                ))
            }
//            for (b in a..<filters.count()) {
            for (b in a..<filters.size) {
                if (a == b) continue
                gotToStart++
                val filter_a = filters[a]
                val filter_b = filters[b]
//                val filter_b = uVFilter
//                if (b != a+1){continue}
                val filter_z = filters[z]
                if (stackThreeFilters) {
                    if (filter_a.d65xyz.Y * filter_b.d65xyz.Y * filter_z.d65xyz.Y < stackYFloor - 0.05) continue
                }
                else {
                    if (filter_a.d65xyz.Y * filter_b.d65xyz.Y < stackYFloor - 0.05) continue
                }
                gotToComp++
                val compositeFilter = if (stackThreeFilters) {
                    filter_b + filter_a + filter_z
                }else{
                    filter_b + filter_a
                }
//                val compositeFilter = filter_b + filter_a
                val filteredSpectrum = compositeFilter.getFilteredSpectrum(baseIlluminant)
                val illum = Illuminant(filteredSpectrum, "Base filtered by ${compositeFilter.name}")
                gotToYCheck++
                if (illum.xyz.Y < stackYFloor) continue
                gotToYuvTol++
                if (abs(cieCalculator.uvColourDifference(idealYuv, illum.yuv)) > yuvTolerance) continue

                val cctresult = illum.cct
                gotToCCT++

                if (abs(cctresult.duv - target_duv) > duvTolerance) continue
                gotToDuv++
                if (abs(cctresult.cct - target_cct) > cctTolerance) continue
                gotToQuickCRI++
                var criScore = 0.0
                var ra: TheaCRIResult? = null
                var rt: TheaCRIResult? = null
//                for (criIllum in listOf(coldIlluminant, hotIlluminant)){
                for (criIllum in listOf(hotIlluminant)){
                    val criSpectrum = compositeFilter.getFilteredSpectrum(criIllum)
                    ra = cricalc.calculateRt(Illuminant(criSpectrum), idealIlluminant)
//                    if (ra.rt < 92) continue
                    rt = tm30calc.calculateRt(illum)
                    criScore += ((100 - rt.rt) * 0.5).pow(2) * weight_Rt +
                            ((100 - ra.sampleScores[8].second) * 0.8).pow(2) * weight_r9 +
                            ((100 - rt.sampleScores[15].second) * 0.5).pow(2) * weight_38
                }
                gotToLongCRI++
                gotToEnd++
                if (abs(cctresult.duv - target_duv) > yuvTolerance) continue
                val score = (1.0 - illum.xyz.Y).pow(2) * 95 * weight_Y + criScore / 2.0
                compositeFilter.score = score
                val result = FilterTestResult(compositeFilter, illum, compositeFilter.score, cctresult, rt!!, ra!!)
                when (outResults.count()) {
                    in 0..15 -> outResults.addLast(result)
                    else -> if (result.score < outResults.map { it.score }.max()) outResults.addLast(result)
                }
//                val result = SmallFilterTestResult(compositeFilter.name, illum.xyz.Y, compositeFilter.score,
//                    cctresult, rt)
            }
            val sortedResults = outResults.sortedBy { -(it as FilterTestResult).score }
            outResults = sortedResults.takeLast(16).toMutableList()

        }

        if (!stackThreeFilters) break
        println("$z...")
    }
    outResults.forEach {
        println(it)
        println(it.theaCRIResult)
        println()
    }
    writeTSV(outResults.takeLast(10).map { (it as FilterTestResult).filter })
    println("Found ${gotToEnd} filters")

}
