package org.example

import java.io.File
import kotlin.math.pow

class DaylightGenerator() {
    val s0List: MutableList<Double> = mutableListOf()
    val s1List: MutableList<Double> = mutableListOf()
    val s2List: MutableList<Double> = mutableListOf()

    init {
        val csvData = File("data/DIlluminants.csv").readText()
        val startWavelength = 380
        val wavelengthInc = 5
        val lines = csvData.split("\n")
        val out = (0..<wavelengthCount5nm).map {
            val split = lines[it + (- startWavelength + startWavelength5nm)/5 + 1]
                .split(",")
                .map { it.toDouble() }
            s0List.addLast(split[1])
            s1List.addLast(split[2])
            s2List.addLast(split[3])
        }
    }
    fun getDaylightSpectrumFromCCT(cct: CCT, normalise: Boolean = true): Illuminant {
        val x = if (cct < 4000) {
            throw Exception("Daylight CCT must be >= 4000K")
        } else if (cct in 4000.0..7000.0) {
            -4.607e9 / cct.pow(3) +
                        2.9678e6 / cct.pow(2) +
                        0.09911e3 / cct +
                        0.244063
        } else if (cct in 7000.0.. 25000.0) {
            -2.0064e9 / cct.pow(3) +
                        1.9018e6 / cct.pow(2) +
                        0.24748e3 / cct +
                        0.23704
        } else {
            throw Exception("Daylight CCT must be <= 25000K")
        }

        val y = -3 * x.pow(2) + 2.87 * x - 0.275
        val m = 0.0241 + 0.2562 * x - 0.7341 * y
        val m1 = (-1.3515 - 1.7703 * x + 5.9114 * y) / m
        val m2 = (0.03 - 31.4424 * x + 30.0717 * y) / m

        val out = cieCalculator.indexRange.map {i ->
            s0List[i]  + s1List[i] * m1 + s2List[i] * m2
        }
        val ill = Illuminant(out as IlluminantSpectrum, "DaylightGen")
        if (normalise) {
            val y = ill.xyz.Y
            return Illuminant(ill.spectrum.map { it / y }, ill.name)
        }
        return ill
    }

}

fun main() {
    val d = DaylightGenerator()
    val basepath = "/mnt/argyll/Illuminants/"
//    val soraa = readArgyllIlluminant("data/Soraa_5000k_Twosnapped_Lee400Hot.sp")
//    val soraa = readArgyllIlluminant(basepath +"Philips_5.5w_Ra97_400lm_cool.sp")
    val soraa = readArgyllIlluminant("data/Soraa_New60d_1_202gel_Lee400Hot.sp")
//    val soraa = readArgyllIlluminant("data/NewSoraa_60d_1_Lee400Cold.sp")
//    val soraa = d.getDaylightSpectrumFromCCT(5000.0)
//    val soraa = readArgyllIlluminant(basepath + "6000kcheapled.sp")
    println(soraa.cct)
    val ds = d.getDaylightSpectrumFromCCT(soraa.cct)
    val cam = getCATMatrix(soraa, ds)
    println(ds.cct)
    println(soraa.xyz.xyY)
    println(ds.xyz.xyY)
    val subset = cricalc.samples.filter {
        if (it.name[0] == 'A' || it.name[0] == 'N'){
            false
        } else  if (it.name.takeLast(2).toInt() !in 2..9) {
            false
        } else {
            true
        }
    }
    subset.forEach {sample ->
        val refCol = sample.render(ds)
        val soraaCol = sample.render(soraa)
        val adaptedSoraa = sample.renderAndAdapt(soraa, cam)
        sample.colourDifference = abColourDifference(refCol, adaptedSoraa)
    }
    val sortedSamples = subset.sortedByDescending { it.colourDifference }
    sortedSamples.forEach {
        println(it.name + " " + it.colourDifference)
    }
    val rms = sortedSamples.map {
        (it.colourDifference as ColourDifference).pow(2)
    }.average().pow(0.5)

    println("RMS Error %,.2f dE".format(rms))
    println("TRI Rt %,.1f".format(100 - rms * 4.0))
//    val sample = cricalc.samples[16]
////    val sample = neutralSample
//    val refCol = sample.render(ds)
//    val soraaCol = sample.render(soraa)
//    val adaptedSoraa = sample.renderAndAdapt(soraa, cam)
//    sample.reflectanceSpectrum.forEach { println(it) }
//    println(sample.name)
//    println(refCol.lab)
//    println(adaptedSoraa.lab)
//    println(soraaCol.lab)
//    println(abColourDifference(refCol, adaptedSoraa))
}