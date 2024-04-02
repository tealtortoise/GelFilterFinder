package org.example

import java.io.File
import kotlin.math.pow
import java.lang.NumberFormatException
import kotlin.math.abs

typealias ABColourDifference = Double
typealias UVColourDifference = Double

class ColourSample(val name: String, val reflectanceSpectrum: List<Double>) {
    public var colourDifference: ABColourDifference? = null

    public fun render(illuminant: Illuminant): CieXYZ {
        val xyz_ = cieCalculator.spectrum5nmToXYZ(reflectanceSpectrum, illuminant=illuminant)
        return xyz_
    }

    public fun renderAndAdapt(sourceIlluminant: Illuminant, matrix: CATMatrix): CieXYZ {
        val adapted = applyMatrix(this.render(sourceIlluminant), matrix)
        return adapted
    }

    override fun toString(): String {
        val xyz = render(D65)
        val xyY = xyz.xyY
        val lab = cieCalculator.xyzToLab(xyz)
        return "%,.3f, %,.3f, %,.3f | %,.3f, %,.3f, %,.3f".format(xyY.x_, xyY.y_, xyY.Y,
            lab.L, lab.a, lab.b)
    }
}
val neutralSample = ColourSample("Neutral Reference Sample", cieCalculator.indexRange.map { 0.5 })

class TheaCRIResult(val rt: Double,val cct: CCT, val refIllum: Illuminant, val duv: UVColourDifference) {
    override fun toString(): String {
        return "Colour rendering Rt %.1f, CCT %.0fK Duv %.4f".format(rt, cct, duv)
    }
}

fun abColourDifference(in1: CieXYZ, in2: CieXYZ): ABColourDifference {
    if (in1.refIlluminant.xyz != in2.refIlluminant.xyz) {
        throw Exception("Reference illuminants do not match")
    }

    return ((in1.lab.a - in2.lab.a).pow(2) + (in1.lab.b - in2.lab.b).pow(2)).pow(0.5)
}

fun calcUVColourDifference(refXYZ: CieXYZ, testXYZ: CieXYZ): UVColourDifference {
    val u1 = (4*refXYZ.xyY.x_) / (-2*refXYZ.xyY.x_ + 12*refXYZ.xyY.y_ + 3)
    val v1 = (6*refXYZ.xyY.y_) / (-2*refXYZ.xyY.x_ + 12*refXYZ.xyY.y_ + 3)
    val u2 = (4*testXYZ.xyY.x_) / (-2*testXYZ.xyY.x_ + 12*testXYZ.xyY.y_ + 3)
    val v2 = (6*testXYZ.xyY.y_) / (-2*testXYZ.xyY.x_ + 12*testXYZ.xyY.y_ + 3)
    val dif = ((u1 - u2).pow(2) + (v1 - v2).pow(2)).pow(0.5)
//    println("t")
//    println(refXYZ.xyY.y_)
//    println(testXYZ.xyY.y_)
    if (refXYZ.xyY.y_ < testXYZ.xyY.y_) {
        return dif
    }
    return -dif
}
val spectrumGenerator = SpectrumGenerator()

class TheaCRICalculator() {

    public val samples: List<ColourSample>

    init {
        val ccsgData = File("CCSG.ti3").readText()
        val lines = ccsgData.split("\n")
        val startLineIdx = 19
        val sampleLines = lines.subList(startLineIdx, lines.count() - 2)
        val samples = sampleLines.map { line ->
            val split = line.split(" ")
            val name = split[0]
            val spectrum = split.subList(8, split.count() - 10).map { it.toDouble() * 0.01 }
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
        this.samples = samples
    }

    fun calculateRt(testIllum: Illuminant): TheaCRIResult {
        val cct = testIllum.cct
        var refLight = if (cct < 4000.0) {
            spectrumGenerator.getBlackbodySpectrum(cct)
        } else if (cct in 4000.0..5000.0) {
            val bb = spectrumGenerator.getBlackbodySpectrum(cct)
            val dl = spectrumGenerator.getDaylightSpectrumFromCCT(cct)
            val prop = (cct - 4000.0) / 1000.0
            val blendedSpec = cieCalculator.indexRange.map {i ->
                bb.spectrum[i] * (1 - prop) + dl.spectrum[i] * prop
            }
            Illuminant(blendedSpec)
        } else {
            spectrumGenerator.getDaylightSpectrumFromCCT(cct)
        }
        val cam = getCATMatrix(testIllum, refLight)

        val subset = cricalc.samples.filter {
            if (it.name[0] == 'A' || it.name[0] == 'N'){
                false
            } else  if (it.name.takeLast(2).toInt() !in 2..9) {
                false
            } else {
                true
            }
        }
        subset.forEach {sample ->
            val refCol = sample.render(refLight)
            val adaptedTest = sample.renderAndAdapt(testIllum, cam)
            sample.colourDifference = abColourDifference(refCol, adaptedTest)
        }
        val sortedSamples = subset.sortedByDescending { it.colourDifference }
//        sortedSamples.forEach {
//            println(it.name + " " + it.colourDifference)
//        }
        val rms = sortedSamples.map {
//            (it.colourDifference as ABColourDifference).pow(2)
            abs(it.colourDifference as ABColourDifference)
        }.average()//.pow(0.5)

        val rt = 100 - rms * 4.0
        return TheaCRIResult(rt, cct, refLight, calcUVColourDifference(refLight.xyz, testIllum.xyz))

    }
}

val cricalc = TheaCRICalculator()
fun main() {

    val basepath = "/mnt/argyll/Illuminants/"
    var filename = "Osram_9.5W_4000K_Lee400Hot.sp"
    filename = "NewSoraa_36d_1_Lee400Cold.sp"
    filename = "HiLine_6.5w_4000k_36d_HotLee400.sp"
    filename = "5000k_Ra80_CFL.sp"
    filename = "2700kCheapLED.sp"
    filename = "Hallway_Ikea_2700K_aged.sp"

    val dir = File(basepath).listFiles()
    val spFiles = dir.filter { it.path.endsWith(".sp") }
    var co = 0
    spFiles.forEach {
        if (it != null) {
//            println(it.name)
            try {
                co += 1
                val result = cricalc.calculateRt(readArgyllIlluminant(it.path))
                println(it.name)
                println(result.toString())
                println()
            }
            catch (e: java.lang.NumberFormatException) {}
        }
    }
    println(co)
//    filename = "Osram_11W_GLS_4000K_Ra90_Lee400Warm.sp"
//    filename = "Bell_Pro_6w_4000k_Lee400_Hot.sp"
//    filename = "Soraa_5000k_Twosnapped_Lee400Hot.sp"
//    val soraa = readArgyllIlluminant("data/Soraa_5000k_Twosnapped_Lee400Hot.sp")
//    val soraa = readArgyllIlluminant(basepath + filename)
//    val soraa = readArgyllIlluminant("data/Soraa_New60d_1_202gel_Lee400Hot.sp")

//    val dl = spectrumGenerator.getDaylightSpectrumFromCCT(5500.0)
//    val greenDaylight = Illuminant(dl.spectrum.mapIndexed { i, s ->
//              s * (1.0 - ((i - 28.0).pow(2) / 5000.0)) }, "Green")
//    val result = cricalc.calculateRt(soraa)
//    println(result)


//    val result2 = cricalc.calculateRt(greenDaylight)
//    println(result2)
//    println(greenDaylight.spectrum)
//    println(calcUVColourDifference(soraa.xyz, result.refIllum.xyz))
}