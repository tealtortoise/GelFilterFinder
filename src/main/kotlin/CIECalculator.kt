package org.example
import java.io.File
import kotlin.math.atan2
import kotlin.math.pow

val cmf1931path = "data/1931CMF.csv"
val d65path = "data/CIE_std_illum_D65.csv"

const val startWavelength5nm = 400
const val endWavelength5nm = 700
const val wavelengthCount5nm = (endWavelength5nm - startWavelength5nm) / 5 + 1


open class ThreeVector(val e1: Double,val  e2: Double, val e3:Double) {
    open operator fun get(i: Int): Double {
        return when (i) {
            0 -> e1
            1 -> e2
            2 -> e3
            else -> throw Exception("Invalid index '$i'")
        }
    }
}
//
class CieXYZ_(val X: Double, val Y: Double, val Z:Double, val ref: Illuminant) : ThreeVector(X, Y, Z) {

}

class CieXYZ(val X: Double, val Y: Double, val Z:Double, val ref:Illuminant? = D65): ThreeVector(X, Y, Z) {
}

data class CiexyY(val x_: Double, val y_: Double, val Y:Double)

data class CieLab(val L: Double, val a: Double, val b:Double)

class Illuminant(val spectrum: List<Double>, val name: String = "") {
    public val xyz by lazy {
        calc.spectrum5nmToXYZ(this.spectrum, illuminant = nullIlluminant)
    }
}
val D65: Illuminant by lazy {
    val csvData = File(d65path).readText()
    val startWavelength = 300
    val wavelengthInc = 1
    val lines = csvData.split("\n")
    val out = (0..wavelengthCount5nm - 1).map {
        lines[it * 5 + startWavelength5nm - startWavelength].split(",")[1].toDouble()
    }
    val total = out.sum()
    Illuminant(out.map { it / total / 0.36296688171995447})
}

val E: Illuminant by lazy {
    Illuminant((1..wavelengthCount5nm).map { 1.0 / 21.3609212}, "E")
}

val nullIlluminant by lazy {
    Illuminant((1..wavelengthCount5nm).map { 1.0 }, "null")
}

fun lineToList(line: String): List<Double> {
    return line.split(",").map { it.toDouble() }
}
class CIECalculator {
    public var wavelengthData5nm: MutableList<Double> = mutableListOf()
    private var cieXData: List<Double>
    private var cieYData: List<Double>
    private var cieZData: List<Double>
    private var cieXData5nm: MutableList<Double> = mutableListOf()
    private var cieYData5nm: MutableList<Double> = mutableListOf()
    private var cieZData5nm: MutableList<Double> = mutableListOf()
    private var wavelengthData: List<Double>
    public var indexRange: IntRange

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

    public fun xyzToLab(xyz: CieXYZ): CieLab {
        val refIllum = xyz.ref ?: D65
        val xr = xyz.X / refIllum.xyz.X
        val yr = xyz.Y / refIllum.xyz.Y
        val zr = xyz.Z / refIllum.xyz.Z
        val e = 0.008856
        val k = 903.3
        val fx = if (xr > e) xr.pow(1.0/3.0) else (k*xr + 16) / 116
        val fy = if (yr > e) yr.pow(1.0/3.0) else (k*yr + 16) / 116
        val fz = if (yr > e) zr.pow(1.0/3.0) else (k*zr + 16) / 116
        return CieLab(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }
    public fun spectrum5nmToXYZ(spectrum: List<Double>, illuminant: Illuminant=D65): CieXYZ {
        var outX = 0.0
        var outY = 0.0
        var outZ = 0.0
        for (idx in this.indexRange){
            val illum = illuminant.spectrum[idx]
            outX += spectrum[idx] * this.cieXData5nm[idx] * illum
            outY += spectrum[idx] * this.cieYData5nm[idx] * illum
            outZ += spectrum[idx] * this.cieZData5nm[idx] * illum
        }
        return CieXYZ(outX, outY, outZ, illuminant)
    }
}

val calc = CIECalculator()

class GelFilter(public var name: String, var spectrum: List<Double>) {

    public val xyz by lazy {
        calc.spectrum5nmToXYZ(this.spectrum)
    }
    public val xyY by lazy {
        val xyz = this.xyz
        val x = xyz.X / (xyz.X + xyz.Y + xyz.Z)
        val y = xyz.Y / (xyz.X + xyz.Y + xyz.Z)
        CiexyY(x, y, xyz.Y)
    }

    public val lab by lazy {
        calc.xyzToLab(this.xyz)
    }

    public val hue by lazy {
        atan2(this.lab.b, this.lab.a)
    }
    public val sat by lazy {
        (this.lab.b * this.lab.b + this.lab.a * this.lab.a).pow(0.5)
    }

    public fun dilute(strength: Double): GelFilter {
        val newSpec = this.spectrum.map { 1.0 - (1.0 - it) * strength }
        return GelFilter(this.name + " %,.2fapp".format(strength), newSpec)
    }
    override fun toString(): String {
        return "Gel Filter \"${this.name}\" x:${this.xyY.x_} y:${this.xyY.y_} Y:${this.xyY.Y}"
    }
}


val clearFilter = GelFilter("Null (Clear) filter", calc.indexRange.map { 1.0 })