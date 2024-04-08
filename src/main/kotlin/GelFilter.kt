package org.example

import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.pow

typealias TransmissionSpectrum = List<Double>
typealias TransmittedSpectrum = List<Double>

class GelFilter(var name: String, var spectrum: ReflectanceSpectrum, val dilutedBy: Double = 1.0) {
    var score = 0.0

    val d65xyz by lazy {
        cieCalculator.spectrum5nmToXYZ(getFilteredSpectrum(D65))
    }

    fun getXYZ(illuminant: Illuminant): CieXYZ {
        return cieCalculator.spectrum5nmToXYZ(getFilteredSpectrum(illuminant), refIlluminant = illuminant)
    }

    fun getFilteredSpectrum(illuminant: Illuminant): IlluminantSpectrum {
        return illuminant.spectrum.mapIndexed() { i, value -> this.spectrum[i] * value }
    }

    val d65xyY by lazy {
        cieCalculator.xyzToxyY(this.d65xyz)
    }

    val d65lab by lazy {
        cieCalculator.xyzToLab(this.d65xyz)
    }

    val hue by lazy {
        atan2(this.d65lab.b, this.d65lab.a)
    }
    val sat by lazy {
        (this.d65lab.b * this.d65lab.b + this.d65lab.a * this.d65lab.a).pow(0.5)
    }

    fun dilute(strength: Double): GelFilter {
        val newSpec: ReflectanceSpectrum = this.spectrum.map { 1.0 - (1.0 - it) * strength }
        val g = GelFilter(this.name + " %,.3fapp".format(strength), newSpec, dilutedBy = strength)
        return g
    }

    private fun undilutedSpec(): TransmissionSpectrum {
        return this.spectrum.map { 1.0 - (1.0 - it) / this.dilutedBy }
    }

    operator fun plus(other: GelFilter): GelFilter {
        val simplyStack: Boolean = ((this.dilutedBy + other.dilutedBy) > 1.9)
        val compositeSpec = if (simplyStack) {
            // Stack filters ( multiplicative colour )
            this.spectrum.mapIndexed { index, d -> other.spectrum[index] * d }
        } else {
            // Side by side (Additive colour)
            val thisUndiluted = this.undilutedSpec()
            val otherUndiluted = other.undilutedSpec()
            val leftOver = max(this.dilutedBy + other.dilutedBy - 1, 0.0)
            thisUndiluted.mapIndexed { index, d ->
                val aContrib = (1.0 - d) * (this.dilutedBy - leftOver)
                val bContrib = (1.0 - otherUndiluted[index]) * (other.dilutedBy - leftOver)
                val stackedContrib = (1.0 - (d * otherUndiluted[index])) * leftOver
                (1.0 - aContrib - bContrib - stackedContrib)
            }
        }
        val compositeFilter = GelFilter(
            "${this.name} + ${other.name} ${if (simplyStack) "(Stacked)" else ""}",
            compositeSpec,
            dilutedBy = this.dilutedBy + other.dilutedBy
        )
        return compositeFilter
    }

    override fun toString(): String {
        return "Gel Filter \"${this.name}\" x:${this.d65xyY.x_} y:${this.d65xyY.y_} Y:${this.d65xyY.Y} Score :${this.score}"
    }
}

val clearFilter = GelFilter("Null (Clear) filter", cieCalculator.indexRange.map { 1.0 })