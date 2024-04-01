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
    fun getDaylightSpectrumFromCCT(cct: CCT): Illuminant {
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
        return Illuminant(out as IlluminantSpectrum, "DaylightGen")
    }

}

fun main() {
    val d = DaylightGenerator()
    val soraa = readArgyllIlluminant("data/Soraa_5000k_Twosnapped_Lee400Hot.sp")
    val ds = d.getDaylightSpectrumFromCCT(soraa.cct)
    val sample = cricalc.samples[10]
    print(sample.getXYZ(ds).lab)
    print(sample.getXYZ(soraa).lab)
//    print(soraa.xyz.xyY)
//    print(soraa.spectrum)
}