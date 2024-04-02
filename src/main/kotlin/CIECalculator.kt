package org.example
import java.io.File
import kotlin.math.atan2
import kotlin.math.pow

val cmf1931path = "data/1931CMF.csv"
val d65path = "data/CIE_std_illum_D65.csv"

const val startWavelength5nm = 400
const val endWavelength5nm = 700
const val wavelengthCount5nm = (endWavelength5nm - startWavelength5nm) / 5 + 1

typealias ReflectanceSpectrum = List<Double>
typealias TransmissionSpectrum = List<Double>
typealias TransmittedSpectrum = List<Double>
typealias IlluminantSpectrum = List<Double>
typealias CMF = List<Double>
typealias MutableCMF = MutableList<Double>
typealias CCT = Double

open class ThreeVector(val e1: Double,val  e2: Double, val e3:Double) {
    open operator fun get(i: Int): Double {
        return when (i) {
            0 -> e1
            1 -> e2
            2 -> e3
            else -> throw Exception("Invalid index '$i'")
        }
    }

    override fun toString(): String {
        return "%,.6f, %,.6f, %,.6f".format(e1, e2, e3)
    }
}
//
class CieXYZ_(val X: Double, val Y: Double, val Z:Double, val ref: Illuminant) : ThreeVector(X, Y, Z) {

}

class CieXYZ(val X: Double, val Y: Double, val Z:Double, val refIlluminant: Illuminant): ThreeVector(X, Y, Z) {

    public val xyY by lazy {
        val x = X / (X + Y + Z)
        val y = Y / (X + Y + Z)
        CiexyY(x, y, Y)
    }

    public val cct by lazy {
        cieCalculator.xyzToCCT(this)
    }
    public val lab by lazy {
        cieCalculator.xyzToLab( this)
    }

}

data class CiexyY(val x_: Double, val y_: Double, val Y:Double)

data class CieLab(val L: Double, val a: Double, val b:Double)

class Illuminant(val spectrum: IlluminantSpectrum, val name: String = "") {
    public val xyz by lazy {
        cieCalculator.spectrum5nmToXYZ(this.spectrum, illuminant = nullIlluminant)
    }
    public val cct by lazy {
        this.xyz.cct
    }
}

val cieCalculator = CIECalculator()

fun readIlluminant(pathn: String, normalise: Boolean = true): Illuminant {
    val csvData = File(pathn).readText()
    val startWavelength = 300
    val wavelengthInc = 1
    val lines = csvData.split("\n")
    val out = (0..wavelengthCount5nm - 1).map {
        (0..0).map {offset ->
            lines[it * 5 + startWavelength5nm - startWavelength + offset].split(",")[1].toDouble()
        }.sum()
    }
    val ill = Illuminant(out)
    if (normalise) {
        val xyz = cieCalculator.spectrum5nmToXYZ(ill.spectrum, nullIlluminant)
        return Illuminant(ill.spectrum.map { it / xyz.Y })
    }
    return ill
}

fun readArgyllIlluminant(pathn: String, normalise: Boolean = true): Illuminant {
    val csvData = File(pathn).readText()
    val lines = csvData.split("\n")
    val beginLine = lines.indexOf("BEGIN_DATA")

    val rawspec = lines[beginLine + 1].trim().split(" ").map { it.toDouble() }
    val spec = cieCalculator.indexRange.map {i ->
        if (i % 2 == 0) {
            rawspec[i /2 + 2]
        } else {
            (rawspec[i/2 + 2] + rawspec[i/2 + 3]) / 2.0
        }
    }
    val illName = pathn.split("/").last()
    if (normalise) {
        val y = Illuminant(spec).xyz.Y
        return Illuminant(spec.map { it / y }, illName)
    }
    return Illuminant(spec, name=illName)
}

val D65: Illuminant by lazy {
    readIlluminant("data/CIE_std_illum_D65.csv", normalise = true)
}
val D50: Illuminant by lazy {
    readIlluminant("data/CIE_std_illum_D50.csv", normalise = true)
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
    private var cieXData: CMF
    private var cieYData: CMF
    private var cieZData: CMF
    private var cieXData5nm: MutableCMF = mutableListOf()
    private var cieYData5nm: MutableCMF = mutableListOf()
    private var cieZData5nm: MutableCMF = mutableListOf()
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
        }
    }
    public fun wavelengthTo5nmIndex(nm: Double): Int {
        return ((nm - 400.0) / 5.0 + 0.5).toInt()
    }

    public fun xyzToLab(xyz: CieXYZ): CieLab {
        val refIllum = xyz.refIlluminant
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

    public fun xyzToCCT(xyz: CieXYZ): Double {
        val xyY = xyz.xyY
        val xe = 0.3320
        val ye = 0.1858
        val n = (xyY.x_ - xe) / (xyY.y_ - ye)
        val cct = -449 * n.pow(3) + 3525 * n * n - 6823.3 * n + 5520.33
        return cct
    }
    public fun xyzToxyY(xyz: CieXYZ): CiexyY {
        val x = xyz.X / (xyz.X + xyz.Y + xyz.Z)
        val y = xyz.Y / (xyz.X + xyz.Y + xyz.Z)
        return CiexyY(x, y, xyz.Y)
    }
}

class GelFilter(public var name: String, var spectrum: ReflectanceSpectrum) {

    public val xyz by lazy {
        cieCalculator.spectrum5nmToXYZ(this.spectrum)
    }
    public val xyY by lazy {
        cieCalculator.xyzToxyY(this.xyz)
    }

    public val lab by lazy {
        cieCalculator.xyzToLab(this.xyz)
    }

    public val hue by lazy {
        atan2(this.lab.b, this.lab.a)
    }
    public val sat by lazy {
        (this.lab.b * this.lab.b + this.lab.a * this.lab.a).pow(0.5)
    }

    public fun dilute(strength: Double): GelFilter {
        val newSpec: ReflectanceSpectrum = this.spectrum.map { 1.0 - (1.0 - it) * strength }
        return GelFilter(this.name + " %,.2fapp".format(strength), newSpec)
    }
    override fun toString(): String {
        return "Gel Filter \"${this.name}\" x:${this.xyY.x_} y:${this.xyY.y_} Y:${this.xyY.Y}"
    }
}


val clearFilter = GelFilter("Null (Clear) filter", cieCalculator.indexRange.map { 1.0 })