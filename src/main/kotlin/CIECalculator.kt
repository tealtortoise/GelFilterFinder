package org.example

import java.io.File

val filename = "data/1931CMF.csv"

fun lineToList(line: String): List<Double> {
    return line.split(",").map { it.toDouble() }
}
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
    }
    public fun wavelengthToIndex(nm: Double): Int {
        return (nm - 360.0).toInt()
    }
}

fun main() {
    val calculator = CIECalculator()
}