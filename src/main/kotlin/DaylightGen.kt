package org.example

import java.io.File
import kotlin.math.exp
import kotlin.math.pow

class SpectrumGenerator() {
    val s0List: MutableArray<Float> = mutableListOf()
    val s1List: MutableArray<Float> = mutableListOf()
    val s2List: MutableArray<Float> = mutableListOf()

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

    fun getBlackbodySpectrum(cct: CCT, normalise: Boolean = true): Illuminant {

        val h = 6.626e-34
        val c = 3.0e+8
        val k = 1.38e-23

        val a = 2.0*h*c.pow(2)
        val spec = cieCalculator.wavelengthData5nm.map { nm ->
            val m = nm * 1e-9
            val b = h * c / (m * k * cct)
            a / ( (m.pow(5)) * (exp(b) - 1.0) )
        }
        val ill = Illuminant(spec, "%,.0f Blackbody".format(cct))
        if (normalise) {
            val mult = 1.0  / ill.xyz.Y
            return Illuminant(spec.map { it * mult }, name=ill.name)
        }
        return ill
    }
}

fun main() {


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