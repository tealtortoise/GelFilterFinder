package org.example
import org.example.CIECalculator
import org.example.CieXYZ
import org.example.GelFilter
import org.example.clearFilter
import java.io.File

fun main() {
    val filterDataLines = File("filters_by_row.txt").readText().split("\n")
    val filters = filterDataLines.takeLast(filterDataLines.count()-1)
        .filter {
            it.count() > 1
        }
        .map { line ->
            val split = line.split("\t")
            val gel = GelFilter(split[0],
                split.subList(1, split.count()).map { it.toDouble() })
            println(gel)
            gel
        }
    println(clearFilter.xyz)
    println(clearFilter.xyY)

}
