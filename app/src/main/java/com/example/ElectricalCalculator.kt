package com.example

import kotlin.math.sqrt

// NBR 5410 voltage drop limit data class
data class NbrLimit(val point: String, val limitPercent: Double)

// Queda de Tensão result details data class
data class QuedaTensaoResult(
    val calculatedSection: Double,
    val suggestedGauge: Double?,
    val formulaUsed: String,
    val isExecutable: Boolean,
    val voltageDropVolts: Double,
    val maxAllowedDropVolts: Double
)

object ElectricalCalculator {

    val nbrVoltageDropLimits = listOf(
        NbrLimit("Circuitos terminais (uso geral)", 4.0),
        NbrLimit("Terminal de entrega (concessionária) até utilização", 5.0),
        NbrLimit("Terminal de saída do transformador MT/BT até utilização", 7.0),
        NbrLimit("Saída do gerador próprio até o ponto de utilização", 7.0)
    )

    val commercialGaugesMm2 = listOf(
        1.5, 2.5, 4.0, 6.0, 10.0, 16.0, 25.0, 35.0, 50.0, 70.0, 95.0, 120.0, 150.0, 185.0, 240.0, 300.0
    )

    val standardBreakerRatingsAmperes = listOf(
        6.0, 10.0, 13.0, 16.0, 20.0, 25.0, 32.0, 40.0, 50.0, 63.0, 70.0, 80.0, 100.0, 125.0
    )

    // 1. Lei de Ohm (Tensão)
    fun calculateLeiOhmTensao(resistance: Double, current: Double): Double {
        return resistance * current
    }

    // 2. Lei de Ohm (Corrente)
    fun calculateLeiOhmCorrente(voltage: Double, resistance: Double): Double {
        if (resistance == 0.0) return 0.0
        return voltage / resistance
    }

    // 3. Lei de Ohm (Resistência)
    fun calculateLeiOhmResistencia(voltage: Double, current: Double): Double {
        if (current == 0.0) return 0.0
        return voltage / current
    }

    // 4. Potência Ativa (Monofásica)
    fun calculatePotenciaAtivaMonofasica(voltage: Double, current: Double, powerFactor: Double): Double {
        return voltage * current * powerFactor
    }

    // 5. Potência Ativa (Trifásica)
    fun calculatePotenciaAtivaTrifasica(voltage: Double, current: Double, powerFactor: Double): Double {
        return sqrt(3.0) * voltage * current * powerFactor
    }

    // 6. Potência Aparente (Monofásica)
    fun calculatePotenciaAparenteMonofasica(voltage: Double, current: Double): Double {
        return voltage * current
    }

    // 7. Potência Aparente (Trifásica)
    fun calculatePotenciaAparenteTrifasica(voltage: Double, current: Double): Double {
        return sqrt(3.0) * voltage * current
    }

    // 8. Fator de Potência
    fun calculateFatorPotencia(activePower: Double, apparentPower: Double): Double {
        if (apparentPower == 0.0) return 0.0
        return activePower / apparentPower
    }

    // 9. Consumo de Energia (kWh)
    fun calculateConsumoEnergiaKwh(watts: Double, hours: Double, days: Double): Double {
        return (watts * hours * days) / 1000.0
    }

    // 10. Corrente de Projeto (Monofásica)
    fun calculateCorrenteProjetoMonofasica(activePower: Double, voltage: Double, powerFactor: Double): Double {
        val denom = voltage * powerFactor
        if (denom == 0.0) return 0.0
        return activePower / denom
    }

    // 11. Corrente de Projeto (Trifásica)
    fun calculateCorrenteProjetoTrifasica(activePower: Double, voltage: Double, powerFactor: Double): Double {
        val denom = sqrt(3.0) * voltage * powerFactor
        if (denom == 0.0) return 0.0
        return activePower / denom
    }

    // 12. Dimensionamento de Condutor por Queda de Tensão (NBR 5410)
    // S = (k * L * I) / (C * V * %QuedaMax)
    fun calculateQuedaTensao(
        current: Double,
        distance: Double,
        voltage: Double,
        maxQuedaPercent: Double,
        isCobre: Boolean,
        isMonofasico: Boolean
    ): QuedaTensaoResult {
        val k = if (isMonofasico) 2.0 else sqrt(3.0)
        val c = if (isCobre) 58.0 else 35.5
        
        // Let's protect against division by zero
        if (c == 0.0 || voltage == 0.0 || maxQuedaPercent == 0.0) {
            return QuedaTensaoResult(0.0, null, "S = (k * L * I) / (C * V * %QuedaMax)", false, 0.0, 0.0)
        }

        // Queda máxima permitida em volts
        val maxAllowedDropVolts = (maxQuedaPercent / 100.0) * voltage
        
        // S = (k * L * I) / (C * dV)
        val calculatedSection = (k * distance * current) / (c * maxAllowedDropVolts)
        
        // Find suggested gauge immediately superior
        val suggestedGauge = commercialGaugesMm2.firstOrNull { it >= calculatedSection }
        
        // Real absolute voltage drop if we use the calculated minimum section
        val voltageDropVolts = if (calculatedSection > 0) {
            (k * distance * current) / (c * calculatedSection)
        } else {
            0.0
        }

        val formulaStr = if (isMonofasico) {
            "S = (2 * $distance * $current) / ($c * $voltage * ($maxQuedaPercent / 100))"
        } else {
            "S = (√3 * $distance * $current) / ($c * $voltage * ($maxQuedaPercent / 100))"
        }

        return QuedaTensaoResult(
            calculatedSection = calculatedSection,
            suggestedGauge = suggestedGauge,
            formulaUsed = formulaStr,
            isExecutable = true,
            voltageDropVolts = voltageDropVolts,
            maxAllowedDropVolts = maxAllowedDropVolts
        )
    }

    // 13. Dimensionamento de Disjuntor
    fun suggestBreaker(iz: Double): Double? {
        return standardBreakerRatingsAmperes.firstOrNull { it >= iz }
    }

    // 14. Cálculo de Carga Instalada
    fun calculateCargaInstalada(
        iluminacao: Double,
        tugs: Double,
        tues: Double,
        outras: Double
    ): Double {
        return iluminacao + tugs + tues + outras
    }
}
