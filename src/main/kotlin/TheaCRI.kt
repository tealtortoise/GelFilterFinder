package org.example

import java.io.File

class ColourSample(val name: String, val reflectanceSpectrum: List<Double>) {
    public fun getXYZ(illuminant: Illuminant = D65): CieXYZ {
        return cieCalculator.spectrum5nmToXYZ(reflectanceSpectrum, illuminant=illuminant)
    }

    override fun toString(): String {
        val xyz = getXYZ()
        val xyY = xyz.xyY
        val lab = cieCalculator.xyzToLab(xyz)
        return "%,.3f, %,.3f, %,.3f | %,.3f, %,.3f, %,.3f".format(xyY.x_, xyY.y_, xyY.Y,
            lab.L, lab.a, lab.b)
    }
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
    println(cricalc.samples[0].getXYZ(D50))
}