package org.example

import java.io.File

val filename = "data/1931CMF.csv"

const val startWavelength10nm = 400
const val endWavelength10nm = 700
const val wavelengthCount10nm = (endWavelength10nm - startWavelength10nm) / 10 + 1

fun lineToList(line: String): List<Double> {
    return line.split(",").map { it.toDouble() }
}
class CIECalculator {
    private var wavelengthData10nm: MutableList<Double> = mutableListOf()
    private var cieXData: List<Double>
    private var cieYData: List<Double>
    private var cieZData: List<Double>
    private var cieXData10nm: MutableList<Double> = mutableListOf()
    private var cieYData10nm: MutableList<Double> = mutableListOf()
    private var cieZData10nm: MutableList<Double> = mutableListOf()
    private var wavelengthData: List<Double>

    init {
        val csvData = File(filename).readText()
        val lines = csvData.split("\n")
        this.wavelengthData = lineToList(lines[0])
        this.cieXData = lineToList(lines[1])
        this.cieYData = lineToList(lines[2])
        this.cieZData = lineToList(lines[3])
        for (idx in 0..wavelengthCount10nm - 1){
            val wavelength = (startWavelength10nm + 10 * idx).toDouble()
            val index1nm = idx * 10 + 40
            this.wavelengthData10nm.addLast(wavelength)
            if (this.wavelengthData.get(index1nm) != wavelength) throw Exception("Wavelengths don't match: ${this.wavelengthData.get(index1nm)} and $wavelength")

        }
    }
    public fun wavelengthToIndex(nm: Double): Int {
        return (nm - 360.0).toInt()
    }
}

fun main() {
    val calculator = CIECalculator()
}