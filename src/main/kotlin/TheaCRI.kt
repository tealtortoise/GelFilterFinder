package org.example

import java.io.File
import kotlin.math.pow

typealias ColourDifference = Double

class ColourSample(val name: String, val reflectanceSpectrum: List<Double>) {
    public var colourDifference: ColourDifference? = null

    public fun render(illuminant: Illuminant): CieXYZ {
        val xyz_ = cieCalculator.spectrum5nmToXYZ(reflectanceSpectrum, illuminant=illuminant)
        return xyz_
    }

    public fun renderAndAdapt(sourceIlluminant: Illuminant, matrix: CATMatrix): CieXYZ {
        val adapted = applyMatrix(this.render(sourceIlluminant), matrix)
        return adapted
    }

    override fun toString(): String {
        val xyz = render(D65)
        val xyY = xyz.xyY
        val lab = cieCalculator.xyzToLab(xyz)
        return "%,.3f, %,.3f, %,.3f | %,.3f, %,.3f, %,.3f".format(xyY.x_, xyY.y_, xyY.Y,
            lab.L, lab.a, lab.b)
    }
}

val neutralSample = ColourSample("Neutral Reference Sample", cieCalculator.indexRange.map { 0.5 })

fun abColourDifference(in1: CieXYZ, in2: CieXYZ): ColourDifference {
    if (in1.refIlluminant.xyz != in2.refIlluminant.xyz) {
        throw Exception("Reference illuminants do not match")
    }

    return ((in1.lab.a - in2.lab.a).pow(2) + (in1.lab.b - in2.lab.b).pow(2)).pow(0.5)
}

class TheaCRICalculator() {

    public val samples: List<ColourSample>

    init {
        val ccsgData = File("CCSG.ti3").readText()
        val lines = ccsgData.split("\n")
        val startLineIdx = 19
        val sampleLines = lines.subList(startLineIdx, lines.count() - 2)
        val samples = sampleLines.map { line ->
            val split = line.split(" ")
            val name = split[0]
            val spectrum = split.subList(8, split.count() - 10).map { it.toDouble() * 0.01 }
            val spectrum5nm = (0..<wavelengthCount5nm).map { outIdx ->
                if (outIdx % 2 == 0){
                    spectrum[outIdx / 2]
                }
                else {
                    (spectrum[outIdx / 2] + spectrum[outIdx /2 + 1]) / 2.0
                }
            }
            ColourSample(name, spectrum5nm)
        }
        this.samples = samples
    }
}

val cricalc = TheaCRICalculator()
fun main() {

    println(cricalc.samples.joinToString("\n"))
    println(D65.xyz.Y)
    println(D50.xyz.Y)
    println(D65.cct)
    println(D50.cct)
    println(cricalc.samples[0].reflectanceSpectrum)
    println(cricalc.samples[0].render(D50))
}