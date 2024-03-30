package org.example

import java.io.File

const val filename = "data/1931CMF.csv"

fun lineToList(line: String): List<Double> {
    return line.split(",").map { it.toDouble() }
}

data class XYZ(val X: Double, val Y: Double, val Z: Double)

class CIECalculator {
    private var cieXData: List<Double>
    private var cieYData: List<Double>
    private var cieZData: List<Double>
    private var wavelengthData: List<Double>

    init {
        val csvData = File(filename).readText()
        val lines = csvData.split("\n")
        this.wavelengthData = lineToList(lines[0])
        this.cieXData = lineToList(lines[1])
        this.cieYData = lineToList(lines[2])
        this.cieZData = lineToList(lines[3])
        this.leeCIEXData = this.cieXData.
        this.leeWavelengths = 400..700 step 5
    }
    public fun wavelengthToIndex(nm: Double): Int {
        return (nm - 360.0).toInt()
    }
    public fun spectrumToXYZ(transmissionSpectrum: List<Double>): XYZ {
        val zipList = (400.0..700.0 step) zip transmissionSpectrum
    }
}

fun main() {
    val calculator = CIECalculator()
}