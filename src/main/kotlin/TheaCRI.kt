package org.example

import java.io.File
import kotlin.math.pow
import kotlin.math.abs

typealias ABColourDifference = Double
typealias UVColourDifference = Double

class ColourSample(val name: String, val reflectanceSpectrum: List<Double>) {
    public var colourDifference: ABColourDifference? = null

    public fun render(illuminant: Illuminant): CieXYZ {
        val xyz_ = cieCalculator.legacySpectrum5nmToXYZ(reflectanceSpectrum, illuminant=illuminant)
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

class TheaCRIResult(val rt: Double, val cct: CCT, val refIllum: Illuminant,
                    val duv: UVColourDifference, val sampleScores: List<Pair<String, Double>>, val resulttype: RtSampleSet) {
    override fun toString(): String {
        return when (resulttype) {
                RtSampleSet.TM30 -> "Colour rendering Rt %.1f, CCT %.0fK Duv %.4f, 14 %,.1f".format(
                    rt, cct, duv, sampleScores[14].second)
                RtSampleSet.CRI -> "Colour rendering Rt %.1f, CCT %.0fK Duv %.4f, R9 %,.1f".format(
                    rt, cct, duv, sampleScores[8].second)
                RtSampleSet.CCSG -> "Colour rendering Rt %.1f, CCT %.0fK Duv %.4f".format(rt, cct, duv)
        }
//        return "Colour rendering Rt %.1f, CCT %.0fK Duv %.4f".format(rt, cct, duv)
    }
}

fun abColourDifference(in1: CieXYZ, in2: CieXYZ): ABColourDifference {
    if (in1.refIlluminant.xyz != in2.refIlluminant.xyz) {
        throw Exception("Reference illuminants do not match")
    }

    return ((in1.lab.a - in2.lab.a).pow(2) + (in1.lab.b - in2.lab.b).pow(2)).pow(0.5)
}

val spectrumGenerator = SpectrumGenerator()

enum class RtSampleSet {
    CCSG,
    TM30,
    CRI
}
class TheaCRICalculator(set: RtSampleSet) {
    public val samples: List<ColourSample>
    public val sampleSetType: RtSampleSet

    init {
        this.samples = when (set) {
            RtSampleSet.CCSG -> readCCSGSamples()
            RtSampleSet.CRI -> readCRISamples()
            RtSampleSet.TM30 -> readTM30Samples()
        }
        this.sampleSetType = set
    }

    private fun readCCSGSamples(): List<ColourSample> {
        val ccsgData = File("CCSG.ti3").readText()
        val lines = ccsgData.split("\n")
        val startLineIdx = 19
        val sampleLines = lines.subList(startLineIdx, lines.count() - 2)
        val samples = sampleLines.map { line ->
            val split = line.split(" ")
            val name = split[0]
            val spectrum = split.subList(8, split.count() - 10).map { it.toDouble() * 0.01 }
            val spectrum5nm = (0..<wavelengthCount5nm).map { outIdx ->
                if (outIdx % 2 == 0) {
                    spectrum[outIdx / 2]
                } else {
                    (spectrum[outIdx / 2] + spectrum[outIdx / 2 + 1]) / 2.0
                }
            }
            ColourSample(name, spectrum5nm)
        }
        return samples
    }

    private fun readTM30Samples(): List<ColourSample> {
        val csvData = File("data/tm30_samples.csv").readText()

        val lines = csvData.split("\n").map{
            line -> line.split(",").map {
                cell -> cell.toDouble() }}
        val samples = mutableListOf<ColourSample>()
        for (rowIdx in 1..99) {
            samples.addLast(ColourSample("TM30 $rowIdx", lines[rowIdx].slice(8..68)))
        }
        return samples
    }

    private fun readCRISamples(): List<ColourSample> {
        val csvData = File("data/cri.csv").readText()

        val lines = csvData.split("\n").map{
                line -> line.split("\t").drop(1).map {
                cell -> cell.toDouble() }}
        val samples = mutableListOf<ColourSample>()
        for (rowIdx in 1..14) {
            samples.addLast(ColourSample("TCS$rowIdx", lines[rowIdx].slice(4..64)))
        }
        return samples
    }

    fun calculateRt(testIllum: Illuminant, refIllum: Illuminant? = null, quick: Boolean = false): TheaCRIResult {
        val cct = testIllum.cctonly
        val refLight = refIllum ?:
            if (cct < 4000.0) {
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

        val subset = if (this.sampleSetType == RtSampleSet.CCSG) {
            if (quick) {
                val subsetNames = listOf("E04", "F04", "G04", "H04", "I04", "J04")
                cricalc.samples.filter { it.name in subsetNames }
            } else {
                cricalc.samples.filter {
                    if (it.name[0] == 'A' || it.name[0] == 'N') {
                        false
                    } else if (it.name.takeLast(2).toInt() !in 2..9) {
                        false
                    } else {
                        true
                    }
                }
            }
        } else {
            this.samples
        }
        subset.forEach {sample ->
            val refCol = sample.render(refLight)
            val adaptedTest = sample.renderAndAdapt(testIllum, cam)
            sample.colourDifference = abColourDifference(refCol, adaptedTest) as ABColourDifference
        }
//        val sortedSamples = subset.sortedByDescending { it.colourDifference }
//        sortedSamples.forEach {
//            println(it.name + " " + it.colourDifference)
//        }
        val rms = subset.map {
//            (it.colourDifference as ABColourDifference).pow(2)
            abs(it.colourDifference as ABColourDifference)
        }.average()//.pow(0.5)
        val indivividualScores = subset.map {
            Pair(it.name, 100.0 - it.colourDifference as ABColourDifference * 4.0)
        }
        val rt = 100 - rms * 4.0
        return TheaCRIResult(rt, cct, refLight, calcUVColourDifference(refLight.xyz, testIllum.xyz), indivividualScores,
            this.sampleSetType)

    }
}

val cricalc = TheaCRICalculator(RtSampleSet.CRI)
val tm30calc = TheaCRICalculator(RtSampleSet.TM30)
val ccsgcalc = TheaCRICalculator(RtSampleSet.CCSG)

fun getTRIAllArgyllIlluminants() {

    val basepath = "/mnt/argyll/Illuminants/"

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
}

fun main() {
    print("Graag")
    val tm30calc = TheaCRICalculator(RtSampleSet.TM30)
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