package org.example
import org.example.CieXYZ
import kotlin.reflect.typeOf

val zeroTwoDMatrix = listOf(listOf(0.0, 0.0, 0.0), listOf(0.0,0.0,0.0), listOf(0.0,0.0,0.0))

class twoDMatrix(inp: List<List<Double>> = zeroTwoDMatrix) {
    private val matrix: List<List<Double>>

    init {
        this.matrix = inp.toList()
    }
    operator fun get(a: Int, b:Int): Double {
        return this.matrix.get(a).get(b)
    }
}

//class LMS(public val l: Double,public val m: Double,public val s: Double): ThreeVector {
//    operator fun get(i: Int): Double {
//        return when (i) {
//            0 -> l
//            1 -> m
//            2 -> s
//            else -> throw Exception("Invalid index '$i'")
//        }
//    }
//}

val cat16  = twoDMatrix(listOf(listOf(0.401288, 0.650173, -0.051461),
    listOf(-0.250268, 1.204414, 0.045854),
    listOf(-0.002079, 0.048952, 0.953127)))

fun getCATMatrix(inWhite: CieXYZ, outWhite: CieXYZ): twoDMatrix {
    return cat16
}

fun matmul(vec: ThreeVector, mat: twoDMatrix): List<Double> {
    val out = listOf(0.0, 0.0, 0.0)
    return (0..2).mapIndexed {i, v ->
        0.0
    }
}

fun main() {
//    val mat = twoDMatrix(cat16)
//    println(mat[1,0])
}