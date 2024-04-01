package org.example
import org.example.CieXYZ
import kotlin.reflect.typeOf

val zeroTwoDMatrix = listOf(mutableListOf(0.0, 0.0, 0.0), mutableListOf(0.0,0.0,0.0), mutableListOf(0.0,0.0,0.0))

class twoDMatrix(inp: List<List<Double>> = zeroTwoDMatrix) {
    public val matrix: List<List<Double>>

    init {
        this.matrix = inp.toList()
    }
    operator fun get(a: Int, b:Int): Double {
        return this.matrix.get(a).get(b)
    }

    override fun toString(): String {
        return this.matrix.map { it.toString() }.joinToString("\n")
    }
}

val cat16  = twoDMatrix(listOf(listOf(0.401288, -0.250268, -0.002079),
    listOf(0.650173,  1.204414,  0.048952), listOf(-0.051461,  0.045854,  0.953127)))

val cat16inv = listOf(listOf( 1.86206786,  0.38752654, -0.0158415),
    listOf( -1.01125463,  0.62144744, -0.03412294),
    listOf( 0.14918678, -0.00897399,  1.04996444))


fun getCATMatrix(inWhite: CieXYZ, outWhite: CieXYZ): twoDMatrix {
    val lmsInWhite = matmul(inWhite, cat16)
    val lmsOutWhite = matmul(outWhite, cat16)
    val lRatio = lmsOutWhite[0] / lmsInWhite [0]
    val mRatio = lmsOutWhite[1] / lmsInWhite [1]
    val sRatio = lmsOutWhite[2] / lmsInWhite [2]
    val scaledMatrix = cat16.matrix.map {
        listOf(it[0] * lRatio, it[1] * mRatio, it[2] * sRatio)
    }
    val outMatrix = zeroTwoDMatrix.toMutableList()
    for (r in 0..2) {
        for (c in 0..2) {
            (outMatrix[c])[r] = scaledMatrix[c][0] * cat16inv[0][r] +
                    scaledMatrix[c][1] * cat16inv[1][r] +
                    scaledMatrix[c][2] * cat16inv[2][r]
        }
    }
    return twoDMatrix(outMatrix)
}

fun matmul(vec: ThreeVector, mat: twoDMatrix): ThreeVector {
    val out = (0..2).map { i ->
//        vec[0] * mat[i, 0] + vec[1] * mat[i, 1] + vec[2] * mat [i, 2]
        vec[0] * mat[0, i] + vec[1] * mat[1, i] + vec[2] * mat [2, i]
    }
    return ThreeVector(out[0], out[1], out[2])
}

fun main() {
    val calc = CIECalculator()
    val cam = getCATMatrix(CieXYZ(0.950470, 1.0, 1.088830), CieXYZ(0.964220, 1.0, 0.825210))
    println(cam)
//    val cam1 = getCATMatrix(CieXYZ(0.964220, 1.0, 0.825210), CieXYZ(0.950470, 1.0, 1.088830))
//    println(cam1)
    println(matmul(CieXYZ(0.950470, 1.0, 1.088830), cam))
    println(matmul(CieXYZ(0.964220, 1.0, 0.825210), cam))
    println(matmul(CieXYZ(1.0,2.0,3.0), cam))
//    println(getCATMatrix(CieXYZ(0.964220, 1.0, 0.825210), CieXYZ(0.950470, 1.0, 1.088830)))


}