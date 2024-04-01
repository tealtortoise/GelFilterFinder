package org.example

import java.io.File

data class ColourSample(val name: String, val spectrum: List<Double>)

class TheaCRICalculator() {

    init {
        val ccsgData = File("CCSG.ti3").readText()
        val lines = ccsgData.split("\n")
        val startLineIdx = 19
        val sampleLines = lines.subList(startLineIdx, lines.count() - 2)
        val samples = sampleLines.map { line ->
            val split = line.split(" ")
            val name = split[0]
            val spectrum = split.subList(8, split.count() - 10).map { it.toDouble() }
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
        println(samples.joinToString("\n"))
    }
}

fun main() {
    val calc = TheaCRICalculator()
}