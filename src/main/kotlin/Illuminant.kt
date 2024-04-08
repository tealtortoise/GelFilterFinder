package org.example

import java.io.File
import kotlin.math.ceil
import kotlin.math.floor

class Illuminant(val spectrum: IlluminantSpectrum, val name: String = "") {
    val xyz by lazy {
        cieCalculator.spectrum5nmToXYZ(this.spectrum)
    }
    val cctonly: CCT by lazy {
        this.xyz.cct
    }
    val cct: CCTResult by lazy {
        cieCalculator.xyzToCCT(this.xyz)
    }
    val yuv: Yuv by lazy {
        cieCalculator.xyzToYuv(this.xyz)
    }
}

fun mixIlluminants(inputs: List<Pair<Illuminant, Double>>): Illuminant {
    val spectrum = inputs.map { (ill, prop) ->
        ill.spectrum.map { it * prop }
    }.reduce { spec_a, spec_b ->
        spec_a.zip(spec_b).map { (a, b) -> a + b }
    }
    return Illuminant(spectrum, "Mix")
}

fun readIlluminant(pathn: String, normalise: Boolean = true): Illuminant {
    val csvData = File(pathn).readText()
    val startWavelength = 300
    val lines = csvData.split("\n")
    val out = (0..<wavelengthCount5nm).map {
            lines[it * 5 + startWavelength5nm - startWavelength].split(",")[1].toDouble()
    }
    val ill = Illuminant(out)
    if (normalise) {
        val xyz = cieCalculator.legacySpectrum5nmToXYZ(ill.spectrum, nullIlluminant)
        return Illuminant(ill.spectrum.map { it / xyz.Y })
    }
    return ill
}

fun readArgyllIlluminant(pathn: String, normalise: Boolean = true): Illuminant {
    val csvData = File(pathn).readText()
    val lines = csvData.split("\n")
    val beginLine = lines.indexOf("BEGIN_DATA")

    val rawspec = lines[beginLine + 1].trim().split(" ").map { it.toDouble() }

    val spec = if (rawspec.count() == 36) {
        cieCalculator.indexRange.map { i ->
            if (i % 2 == 0) {
                rawspec[i / 2 + 2]
            } else {
                (rawspec[i / 2 + 2] + rawspec[i / 2 + 3]) / 2.0
            }
        }
    } else if (rawspec.count() == 109) {
        cieCalculator.indexRange.map { i ->
            val argyllIndex = i * 1.5 + 9
            if (argyllIndex == floor(argyllIndex)) {
                rawspec[argyllIndex.toInt()]
            } else {
                val lower = rawspec[floor(argyllIndex).toInt()]
                val higher = rawspec[ceil(argyllIndex).toInt()]
                (lower + higher) / 2.0
            }
        }
    } else {
        throw Exception("Unknown Argyll format")
    }
    val illName = pathn.split("/").last()
    if (normalise) {
        val y = Illuminant(spec).xyz.Y
        return Illuminant(spec.map { it / y }, illName)
    }
    return Illuminant(spec, name = illName)
}

val D65: Illuminant by lazy {
    readIlluminant("data/CIE_std_illum_D65.csv", normalise = true)
}
val D50: Illuminant by lazy {
    readIlluminant("data/CIE_std_illum_D50.csv", normalise = true)
}
val E: Illuminant by lazy {
    Illuminant((1..wavelengthCount5nm).map { 1.0 / 21.3609212 }, "E")
}
val nullIlluminant by lazy {
    Illuminant((1..wavelengthCount5nm).map { 1.0 }, "null")
}