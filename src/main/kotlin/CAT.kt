package org.example

val zeroTwoDMatrix = listOf(mutableListOf(0.0, 0.0, 0.0), mutableListOf(0.0,0.0,0.0), mutableListOf(0.0,0.0,0.0))

open class TwoDimMatrix(inp: List<Array<Float>> = zeroTwoDMatrix) {
    public val matrix: List<Array<Float>>

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

class CATMatrix(inp: List<Array<Float>>, val sourceIlluminant: Illuminant, val targetIlluminant: Illuminant): TwoDimMatrix(inp)

val cat16  = TwoDimMatrix(listOf(listOf(0.401288, -0.250268, -0.002079),
    listOf(0.650173,  1.204414,  0.048952), listOf(-0.051461,  0.045854,  0.953127)))

val cat16inv = TwoDimMatrix(listOf(listOf( 1.86206786,  0.38752654, -0.0158415),
    listOf( -1.01125463,  0.62144744, -0.03412294),
    listOf( 0.14918678, -0.00897399,  1.04996444)))

val bradford = TwoDimMatrix(listOf(listOf(  0.8951, -0.7502,  0.0389),
    listOf(0.2664,  1.7135, -0.0685),
    listOf( -0.1614,  0.0367,  1.0296)))

val bradfordinv = TwoDimMatrix(listOf(listOf( 0.98699291,  0.43230527, -0.00852866),
    listOf(-0.14705426,  0.51836027,  0.04004282),
            listOf(0.15996265,  0.04929123,  0.9684867)))

val scaling = TwoDimMatrix(listOf(listOf(1.0, 0.0, 0.0), listOf(0.0, 1.0, 0.0), listOf(0.0, 0.0, 1.0)))
val scalinginv = TwoDimMatrix(scaling.matrix.toList())

fun getCATMatrix(inWhite: Illuminant, outWhite: Illuminant): CATMatrix {
    val m = bradford
    val m1 = bradfordinv
//    val m = cat16
//    val m1 = cat16inv
//    val m = scaling
//    val m1 = scalinginv

    val lmsInWhite = matmul(inWhite.xyz, m)
    val lmsOutWhite = matmul(outWhite.xyz, m)
    val lRatio = lmsOutWhite[0] / lmsInWhite [0]
    val mRatio = lmsOutWhite[1] / lmsInWhite [1]
    val sRatio = lmsOutWhite[2] / lmsInWhite [2]
    val scaledMatrix = m.matrix.map {
        listOf(it[0] * lRatio, it[1] * mRatio, it[2] * sRatio)
    }
    val outMatrix = zeroTwoDMatrix.toMutableList()
    for (r in 0..2) {
        for (c in 0..2) {
            (outMatrix[c])[r] = scaledMatrix[c][0] * m1[0,r] +
                    scaledMatrix[c][1] * m1[1,r] +
                    scaledMatrix[c][2] * m1[2,r]
        }
    }
    return CATMatrix(outMatrix, inWhite, outWhite)
}

fun matmul(vec: ThreeVector, mat: TwoDimMatrix): ThreeVector {
    val out = (0..2).map { i ->
//        vec[0] * mat[i, 0] + vec[1] * mat[i, 1] + vec[2] * mat [i, 2]
        vec[0] * mat[0, i] + vec[1] * mat[1, i] + vec[2] * mat [2, i]
    }
    return ThreeVector(out[0], out[1], out[2])
}

fun applyMatrix(input: CieXYZ, matrix: CATMatrix): CieXYZ {
    val out = matmul(input, matrix)
    return CieXYZ(out.e1, out.e2, out.e3, matrix.targetIlluminant)
}

fun main() {
    val calc = CIECalculator()
    val cam = getCATMatrix(D65, D50)
    println(cam)
//    val cam1 = getCATMatrix(CieXYZ(0.964220, 1.0, 0.825210), CieXYZ(0.950470, 1.0, 1.088830))
//    println(cam1)
    println(D50.xyz)
    println(applyMatrix(CieXYZ(0.2,0.3,0.4, D65), cam))
//    println(applyMatrix(D50.xyz, cam))
//    println(applyMatrix(CieXYZ(1.0,2.0,3.0, D50), cam))
//    println(getCATMatrix(CieXYZ(0.964220, 1.0, 0.825210), CieXYZ(0.950470, 1.0, 1.088830)))


}