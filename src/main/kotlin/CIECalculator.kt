package org.example
import java.io.File

val cmf1931path = "data/1931CMF.csv"
val d65path = "data/CIE_std_illum_D65.csv"

const val startWavelength5nm = 400
const val endWavelength5nm = 700
const val wavelengthCount5nm = (endWavelength5nm - startWavelength5nm) / 5 + 1

data class CieXYZ(val X: Double, val Y: Double, val Z:Double)

data class Illuminant(val spectrum: List<Double>)

val D65: Illuminant by lazy {
    val csvData = File(d65path).readText()
    val startWavelength = 300
    val wavelengthInc = 1
    val lines = csvData.split("\n")
    val out = (0..wavelengthCount5nm - 1).map {
        lines[it * 5 + startWavelength5nm - startWavelength].split(",")[1].toDouble()
    }
    println(out)
    Illuminant(out)
}
fun lineToList(line: String): List<Double> {
    return line.split(",").map { it.toDouble() }
}
class CIECalculator {
    private var wavelengthData5nm: MutableList<Double> = mutableListOf()
    private var cieXData: List<Double>
    private var cieYData: List<Double>
    private var cieZData: List<Double>
    private var cieXData5nm: MutableList<Double> = mutableListOf()
    private var cieYData5nm: MutableList<Double> = mutableListOf()
    private var cieZData5nm: MutableList<Double> = mutableListOf()
    private var wavelengthData: List<Double>
    private var indexRange: IntRange

    init {
        val csvData = File(cmf1931path).readText()
        val lines = csvData.split("\n")
        this.wavelengthData = lineToList(lines[0])
        this.cieXData = lineToList(lines[1])
        this.cieYData = lineToList(lines[2])
        this.cieZData = lineToList(lines[3])

        this.indexRange = 0..wavelengthCount5nm-1
        for (idx in this.indexRange){
            val wavelength = (startWavelength5nm + 5 * idx).toDouble()
            val index1nm = idx * 5 + 40
            this.wavelengthData5nm.addLast(wavelength)
            if (this.wavelengthData.get(index1nm) != wavelength) throw Exception("Wavelengths don't match: ${this.wavelengthData.get(index1nm)} and $wavelength")
            this.cieXData5nm.addLast(this.cieXData.get(index1nm))
            this.cieYData5nm.addLast(this.cieYData.get(index1nm))
            this.cieZData5nm.addLast(this.cieZData.get(index1nm))
            val a = D65
        }
    }
    public fun wavelengthTo5nmIndex(nm: Double): Int {
        return ((nm - 400.0) / 5.0 + 0.5).toInt()
    }

    public fun spectrum5nmToXYZ(spectrum: List<Double>): CieXYZ {
        var outX = 0.0
        var outY = 0.0
        var outZ = 0.0
        for (idx in this.indexRange){
            val d65 = D65.spectrum[idx]
            outX += spectrum[idx] * this.cieXData5nm[idx] + d65
            outY += spectrum[idx] * this.cieYData5nm[idx]
            outZ += spectrum[idx] * this.cieZData5nm[idx]
        }
        return CieXYZ(outX, outY, outZ)
    }
}

fun main() {
    val calculator = CIECalculator()
}