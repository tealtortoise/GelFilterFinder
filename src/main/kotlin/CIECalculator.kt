package org.example
import java.io.File
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.max

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
typealias Duv = Double


data class CCTResult(val cct: CCT, val duv: Duv)

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

class Yuv(val Y: Double, val u: Double, val v: Double): ThreeVector(Y, u, v) {}

class CieXYZ(val X: Double, val Y: Double, val Z:Double, val refIlluminant: Illuminant): ThreeVector(X, Y, Z) {

    public val xyY by lazy {
        val x = X / (X + Y + Z)
        val y = Y / (X + Y + Z)
        CiexyY(x, y, Y)
    }

    public val cct by lazy {
        cieCalculator.xyzToSimpleCCT(this)
    }
    public val lab by lazy {
        cieCalculator.xyzToLab( this)
    }

}

data class CiexyY(val x_: Double, val y_: Double, val Y:Double)

data class CieLab(val L: Double, val a: Double, val b:Double)

class Illuminant(val spectrum: IlluminantSpectrum, val name: String = "") {
    public val xyz by lazy {
        cieCalculator.spectrum5nmToXYZ(this.spectrum)
    }
    public val cctonly: CCT by lazy {
        this.xyz.cct
    }
    public val cct: CCTResult by lazy {
        cieCalculator.xyzToCCT(this.xyz)
    }
    public val yuv: Yuv by lazy {
        cieCalculator.xyzToYuv(this.xyz)
    }
}

fun mixIlluminants(inputs: List<Pair<Illuminant, Double>>): Illuminant {
    val spectrum = inputs.map { (ill, prop) ->
        ill.spectrum.map { it * prop }
    }.reduce { spec_a, spec_b ->
        spec_a.zip(spec_b).map {(a, b) -> a + b}
    }
    return Illuminant(spectrum, "Mix")
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
        val xyz = cieCalculator.legacySpectrum5nmToXYZ(ill.spectrum, nullIlluminant)
        return Illuminant(ill.spectrum.map { it / xyz.Y })
    }
    return ill
}

fun readArgyllIlluminant(pathn: String, normalise: Boolean = true): Illuminant {
    val csvData = File(pathn).readText()
    val lines = csvData.split("\n")
    val beginLine = lines.indexOf("BEGIN_DATA")

    val rawspec = lines[beginLine + 1].trim().split(" ").map { it.toDouble() }

    val spec = if (rawspec.count() == 36) {
        cieCalculator.indexRange.map { i ->
            if (i % 2 == 0) {
                rawspec[i / 2 + 2]
            } else {
                (rawspec[i / 2 + 2] + rawspec[i / 2 + 3]) / 2.0
            }
        }
    } else if (rawspec.count() == 109) {
        cieCalculator.indexRange.map { i ->
            val argyllIndex = i * 1.5 + 9
            if (argyllIndex == floor(argyllIndex)) {
                rawspec[argyllIndex.toInt()]
            } else {
                val lower = rawspec[floor(argyllIndex).toInt()]
                val higher = rawspec[ceil(argyllIndex).toInt()]
                (lower + higher) / 2.0
            }
        }
    } else {
        throw Exception("Unknown Argyll format")
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
    public fun legacySpectrum5nmToXYZ(transmissionSpectrum: TransmissionSpectrum, illuminant: Illuminant= nullIlluminant): CieXYZ {
        var outX = 0.0
        var outY = 0.0
        var outZ = 0.0
        for (idx in this.indexRange){
            val illum = illuminant.spectrum[idx]
            outX += transmissionSpectrum[idx] * this.cieXData5nm[idx] * illum
            outY += transmissionSpectrum[idx] * this.cieYData5nm[idx] * illum
            outZ += transmissionSpectrum[idx] * this.cieZData5nm[idx] * illum
        }
        return CieXYZ(outX, outY, outZ, illuminant)
    }
    fun spectrum5nmToXYZ(spectrum: IlluminantSpectrum, refIlluminant: Illuminant = nullIlluminant): CieXYZ {
        var outX = 0.0
        var outY = 0.0
        var outZ = 0.0
        for (idx in this.indexRange) {
            val illum = spectrum[idx]
            outX += this.cieXData5nm[idx] * illum
            outY += this.cieYData5nm[idx] * illum
            outZ += this.cieZData5nm[idx] * illum
        }
        return CieXYZ(outX, outY, outZ, refIlluminant)
    }

    public fun xyzToSimpleCCT(xyz: CieXYZ): CCT {
        val xyY = xyz.xyY
        val xe = 0.3320
        val ye = 0.1858
        val n = (xyY.x_ - xe) / (xyY.y_ - ye)
        val cct = -449 * n.pow(3) + 3525 * n * n - 6823.3 * n + 5520.33
        return cct
    }
    public fun xyzToCCT(xyz: CieXYZ): CCTResult {
        fun xyYToCCT(xyY: CiexyY): CCT {
            val xyY = xyz.xyY
            val xe = 0.3320
            val ye = 0.1858
            val n = (xyY.x_ - xe) / (xyY.y_ - ye)
            val cct = -449 * n.pow(3) + 3525 * n * n - 6823.3 * n + 5520.33
            return cct
        }
        val cct = xyYToCCT(xyz.xyY)
        val bbSpec = spectrumGenerator.getBlackbodySpectrum(cct, normalise = false)
        val duv = calcUVColourDifference(bbSpec.xyz, xyz)
        return CCTResult(cct, duv)
    }

    public fun xyzToxyY(xyz: CieXYZ): CiexyY {
        val x = xyz.X / (xyz.X + xyz.Y + xyz.Z)
        val y = xyz.Y / (xyz.X + xyz.Y + xyz.Z)
        return CiexyY(x, y, xyz.Y)
    }

    fun xyYToYuv(xyY: CiexyY): Yuv {
        val u = (4 * xyY.x_) / (-2 * xyY.x_ + 12 * xyY.y_ + 3)
        val v = (6 * xyY.y_) / (-2 * xyY.x_ + 12 * xyY.y_ + 3)
        return Yuv(xyY.Y, u, v)
    }

    fun xyzToYuv(xyz: CieXYZ): Yuv {
        return xyYToYuv(xyzToxyY(xyz))
    }

    fun uvColourDifference(refYuv: Yuv, testYuv: Yuv): Double {
        val dif = ((refYuv.u - testYuv.u).pow(2) + (refYuv.v - testYuv.v).pow(2)).pow(0.5)
        if (refYuv.v < testYuv.v) {
            return dif
        }
        return -dif
    }

}

class GelFilter(public var name: String, var spectrum: ReflectanceSpectrum,val dilutedBy: Double = 1.0) {
    public var score = 0.0

    public val d65xyz by lazy {
        cieCalculator.spectrum5nmToXYZ(getFilteredSpectrum(D65))
    }
    fun getXYZ(illuminant: Illuminant): CieXYZ {
        return cieCalculator.spectrum5nmToXYZ(getFilteredSpectrum(illuminant), refIlluminant = illuminant)
    }
    fun getFilteredSpectrum(illuminant: Illuminant): IlluminantSpectrum {
        return illuminant.spectrum.mapIndexed() { i, value -> this.spectrum[i] * value}
    }
    public val d65xyY by lazy {
        cieCalculator.xyzToxyY(this.d65xyz)
    }

    public val d65lab by lazy {
        cieCalculator.xyzToLab(this.d65xyz)
    }

    public val hue by lazy {
        atan2(this.d65lab.b, this.d65lab.a)
    }
    public val sat by lazy {
        (this.d65lab.b * this.d65lab.b + this.d65lab.a * this.d65lab.a).pow(0.5)
    }

    public fun dilute(strength: Double): GelFilter {
        val newSpec: ReflectanceSpectrum = this.spectrum.map { 1.0 - (1.0 - it) * strength }
        val g = GelFilter(this.name + " %,.3fapp".format(strength), newSpec, dilutedBy = strength)
        return  g
    }

    private fun undilutedSpec(): TransmissionSpectrum {
        return this.spectrum.map { 1.0 - (1.0 - it) / this.dilutedBy }
    }

    operator fun plus(other: GelFilter): GelFilter {
        val simplyStack: Boolean = ((this.dilutedBy + other.dilutedBy) > 1.9)
        val composteSpec = if (simplyStack) {
            // Stack filters ( multiplicative colour )
            this.spectrum.mapIndexed { index, d -> other.spectrum[index] * d }
        } else {
            // Side by side (Additive colour)
            val thisUndiluted = this.undilutedSpec()
            val otherUndilited = other.undilutedSpec()
            val leftOver = max(this.dilutedBy + other.dilutedBy - 1, 0.0)
            thisUndiluted.mapIndexed { index, d ->
                val aContrib = (1.0 - d) * (this.dilutedBy - leftOver)
                val bContrib = (1.0 - otherUndilited[index]) * (other.dilutedBy - leftOver)
                val stackedContrib = (1.0 - (d * otherUndilited[index])) * leftOver
                (1.0 - aContrib - bContrib - stackedContrib)
            }
        }
        val compositeFilter = GelFilter("${this.name} + ${other.name} ${if (simplyStack) "(Stacked)" else ""}",
            composteSpec,
            dilutedBy = this.dilutedBy + other.dilutedBy)
        return compositeFilter
    }

    override fun toString(): String {
        return "Gel Filter \"${this.name}\" x:${this.d65xyY.x_} y:${this.d65xyY.y_} Y:${this.d65xyY.Y} Score :${this.score}"
    }
}


val clearFilter = GelFilter("Null (Clear) filter", cieCalculator.indexRange.map { 1.0 })

fun calcUVColourDifference(refXYZ: CieXYZ, testXYZ: CieXYZ): UVColourDifference {
    val u1 = (4*refXYZ.xyY.x_) / (-2*refXYZ.xyY.x_ + 12*refXYZ.xyY.y_ + 3)
    val v1 = (6*refXYZ.xyY.y_) / (-2*refXYZ.xyY.x_ + 12*refXYZ.xyY.y_ + 3)
    val u2 = (4*testXYZ.xyY.x_) / (-2*testXYZ.xyY.x_ + 12*testXYZ.xyY.y_ + 3)
    val v2 = (6*testXYZ.xyY.y_) / (-2*testXYZ.xyY.x_ + 12*testXYZ.xyY.y_ + 3)
    val dif = ((u1 - u2).pow(2) + (v1 - v2).pow(2)).pow(0.5)
    if (refXYZ.xyY.y_ < testXYZ.xyY.y_) {
        return dif
    }
    return -dif
}

fun main() {
    val illum = D65
    println(D65.xyz.Y)
    println(D65.xyz.Y)
    println(D65.xyz.Y)
    println(D65.xyz.Y)
}