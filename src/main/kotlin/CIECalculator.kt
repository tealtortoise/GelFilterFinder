package org.example

import java.io.File
import kotlin.math.pow

val cmf1931path = "data/1931CMF.csv"
val d65path = "data/CIE_std_illum_D65.csv"

const val startWavelength5nm = 400
const val endWavelength5nm = 700
const val wavelengthCount5nm = (endWavelength5nm - startWavelength5nm) / 5 + 1

typealias ReflectanceSpectrum = List<Double>
typealias IlluminantSpectrum = List<Double>
typealias CMF = List<Double>
typealias MutableCMF = MutableList<Double>
typealias CCT = Double
typealias Duv = Double


data class CCTResult(val cct: CCT, val duv: Duv)

open class ThreeVector(val e1: Double, val e2: Double, val e3: Double) {
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

class Yuv(val Y: Double, val u: Double, val v: Double) : ThreeVector(Y, u, v) {}

class CieXYZ(val X: Double, val Y: Double, val Z: Double, val refIlluminant: Illuminant) : ThreeVector(X, Y, Z) {

    public val xyY by lazy {
        val x = X / (X + Y + Z)
        val y = Y / (X + Y + Z)
        CiexyY(x, y, Y)
    }

    public val cct by lazy {
        cieCalculator.xyzToSimpleCCT(this)
    }
    public val lab by lazy {
        cieCalculator.xyzToLab(this)
    }

}

data class CiexyY(val x_: Double, val y_: Double, val Y: Double)

data class CieLab(val L: Double, val a: Double, val b: Double)

val cieCalculator = CIECalculator()

fun lineToList(line: String): List<Double> {
    return line.split(",").map { it.toDouble() }
}

class CIECalculator {
    var wavelengthData5nm: MutableList<Double> = mutableListOf()
    private var cieXData: CMF
    private var cieYData: CMF
    private var cieZData: CMF
    private var cieXData5nm: MutableCMF = mutableListOf()
    private var cieYData5nm: MutableCMF = mutableListOf()
    private var cieZData5nm: MutableCMF = mutableListOf()
    private var wavelengthData: List<Double>
    var indexRange: IntRange

    init {
        val csvData = File(cmf1931path).readText()
        val lines = csvData.split("\n")
        this.wavelengthData = lineToList(lines[0])
        this.cieXData = lineToList(lines[1])
        this.cieYData = lineToList(lines[2])
        this.cieZData = lineToList(lines[3])

        this.indexRange = 0..<wavelengthCount5nm

        for (idx in this.indexRange) {
            val wavelength = (startWavelength5nm + 5 * idx).toDouble()
            val index1nm = idx * 5 + 40
            this.wavelengthData5nm.addLast(wavelength)
            if (this.wavelengthData.get(index1nm) != wavelength) throw Exception(
                "Wavelengths don't match: ${
                    this.wavelengthData.get(
                        index1nm
                    )
                } and $wavelength"
            )
            this.cieXData5nm.addLast(this.cieXData.get(index1nm))
            this.cieYData5nm.addLast(this.cieYData.get(index1nm))
            this.cieZData5nm.addLast(this.cieZData.get(index1nm))
        }
    }

    fun wavelengthTo5nmIndex(nm: Double): Int {
        return ((nm - 400.0) / 5.0 + 0.5).toInt()
    }

    fun xyzToLab(xyz: CieXYZ): CieLab {
        val refIllum = xyz.refIlluminant
        val xr = xyz.X / refIllum.xyz.X
        val yr = xyz.Y / refIllum.xyz.Y
        val zr = xyz.Z / refIllum.xyz.Z
        val e = 0.008856
        val k = 903.3
        val fx = if (xr > e) xr.pow(1.0 / 3.0) else (k * xr + 16) / 116
        val fy = if (yr > e) yr.pow(1.0 / 3.0) else (k * yr + 16) / 116
        val fz = if (yr > e) zr.pow(1.0 / 3.0) else (k * zr + 16) / 116
        return CieLab(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }

    fun legacySpectrum5nmToXYZ(
        transmissionSpectrum: TransmissionSpectrum,
        illuminant: Illuminant = nullIlluminant
    ): CieXYZ {
        var outX = 0.0
        var outY = 0.0
        var outZ = 0.0
        for (idx in this.indexRange) {
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

    fun xyzToSimpleCCT(xyz: CieXYZ): CCT {
        val xyY = xyz.xyY
        val xe = 0.3320
        val ye = 0.1858
        val n = (xyY.x_ - xe) / (xyY.y_ - ye)
        val cct = -449 * n.pow(3) + 3525 * n * n - 6823.3 * n + 5520.33
        return cct
    }

    fun xyzToCCT(xyz: CieXYZ): CCTResult {
        fun xyYToCCT(xyY: CiexyY): CCT {
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


fun calcUVColourDifference(refXYZ: CieXYZ, testXYZ: CieXYZ): UVColourDifference {
    val u1 = (4 * refXYZ.xyY.x_) / (-2 * refXYZ.xyY.x_ + 12 * refXYZ.xyY.y_ + 3)
    val v1 = (6 * refXYZ.xyY.y_) / (-2 * refXYZ.xyY.x_ + 12 * refXYZ.xyY.y_ + 3)
    val u2 = (4 * testXYZ.xyY.x_) / (-2 * testXYZ.xyY.x_ + 12 * testXYZ.xyY.y_ + 3)
    val v2 = (6 * testXYZ.xyY.y_) / (-2 * testXYZ.xyY.x_ + 12 * testXYZ.xyY.y_ + 3)
    val dif = ((u1 - u2).pow(2) + (v1 - v2).pow(2)).pow(0.5)
    if (refXYZ.xyY.y_ < testXYZ.xyY.y_) {
        return dif
    }
    return -dif
}

fun main() {
}