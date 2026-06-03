package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

// Calculation Item Schema
data class CalculationItem(
    val id: String,
    val title: String,
    val formula: String,
    val exampleUse: String,
    val parameters: List<CalcParam>
)

data class CalcParam(
    val id: String,
    val label: String,
    val unit: String,
    val helperText: String,
    val minVal: Double? = null,
    val maxVal: Double? = null,
    val defaultVal: String
)

// UI Categories definition
sealed class AppCategory(val id: Int, val name: String, val icon: String) {
    object Basico : AppCategory(0, "Cálculos Básicos", "⚡")
    object Consumo : AppCategory(1, "Medições & Consumo", "📊")
    object Dimensionamento : AppCategory(2, "Dimensionamento NBR", "📐")
    object GuiaNBR : AppCategory(3, "Guia Rápido NBR", "📖")
}

// State representing dynamic changes
data class UIState(
    val selectedCategory: AppCategory = AppCategory.Basico,
    val activeCalcId: String = "lei_ohm_tensao",
    // Map of calculation_id -> parameter_id -> current string representation
    val paramValues: Map<String, Map<String, String>> = initialParamValues(),
    // Custom states for options
    val conductorMaterialCobre: Boolean = true, // true = copper (58), false = aluminum (35.5)
    val condutivityCobre: Double = 58.0,
    val condutivityAlu: Double = 35.5,
    val isPhaseMonofasico: Boolean = true // true = single-phase (k=2), false = three-phase (k=sqrt(3))
)

fun initialParamValues(): Map<String, Map<String, String>> {
    return mapOf(
        "lei_ohm_tensao" to mapOf("R" to "10", "I" to "2"),
        "lei_ohm_corrente" to mapOf("V" to "127", "R" to "20"),
        "lei_ohm_resistencia" to mapOf("V" to "220", "I" to "10"),
        "potencia_ativa_monofasica" to mapOf("V" to "220", "I" to "20", "FP" to "1.0"),
        "potencia_ativa_trifasica" to mapOf("V" to "380", "I" to "10", "FP" to "0.85"),
        "potencia_aparente_monofasica" to mapOf("V" to "220", "I" to "5"),
        "potencia_aparente_trifasica" to mapOf("V" to "380", "I" to "15"),
        "fator_potencia" to mapOf("P" to "1000", "S" to "1200"),
        "consumo_energia_kwh" to mapOf("P" to "1200", "Horas" to "8", "Dias" to "30", "Tarifa" to "0.85"),
        "corrente_projeto_monofasica" to mapOf("P" to "1500", "V" to "127", "FP" to "0.9"),
        "corrente_projeto_trifasica" to mapOf("P" to "5000", "V" to "380", "FP" to "0.85"),
        "dimensionamento_condutor_queda_tensao" to mapOf("I" to "20", "L" to "30", "V" to "220", "Queda" to "4.0"),
        "dimensionamento_disjuntor" to mapOf("Iz" to "25"),
        "calculo_carga_instalada" to mapOf("Ilum" to "500", "Tugs" to "1000", "Tues" to "5500", "Outras" to "0")
    )
}

class CalculatorViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UIState())
    val uiState: StateFlow<UIState> = _uiState.asStateFlow()

    fun setCategory(category: AppCategory) {
        val defaultCalcId = when (category) {
            AppCategory.Basico -> "lei_ohm_tensao"
            AppCategory.Consumo -> "consumo_energia_kwh"
            AppCategory.Dimensionamento -> "corrente_projeto_monofasica"
            AppCategory.GuiaNBR -> _uiState.value.activeCalcId
        }
        _uiState.update { 
            it.copy(
                selectedCategory = category,
                activeCalcId = defaultCalcId
            ) 
        }
    }

    fun selectCalculation(calcId: String) {
        _uiState.update { it.copy(activeCalcId = calcId) }
    }

    fun updateParamValue(calcId: String, paramId: String, value: String) {
        _uiState.update { current ->
            val innerMap = current.paramValues[calcId]?.toMutableMap() ?: mutableMapOf()
            // Clean values if helpful
            innerMap[paramId] = value
            val newParamValues = current.paramValues.toMutableMap()
            newParamValues[calcId] = innerMap
            current.copy(paramValues = newParamValues)
        }
    }

    fun setConductorMaterial(isCobre: Boolean) {
        _uiState.update { it.copy(conductorMaterialCobre = isCobre) }
    }

    fun setPhaseMonofasico(isMono: Boolean) {
        _uiState.update { it.copy(isPhaseMonofasico = isMono) }
    }

    fun resetInputs(calcId: String) {
        val initialValues = initialParamValues()[calcId] ?: emptyMap()
        _uiState.update { current ->
            val newParamValues = current.paramValues.toMutableMap()
            newParamValues[calcId] = initialValues
            current.copy(paramValues = newParamValues)
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize().testTag("app_scaffold"),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    ElectricCalculatorAppScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElectricCalculatorAppScreen(
    modifier: Modifier = Modifier,
    viewModel: CalculatorViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    val calculationItems = remember {
        listOf(
            // CÁLCULOS BÁSICOS
            CalculationItem(
                id = "lei_ohm_tensao",
                title = "Lei de Ohm (Tensão)",
                formula = "V = R * I",
                exampleUse = "Calcula a tensão (V) sob uma dada resistência e corrente.",
                parameters = listOf(
                    CalcParam("R", "Resistência (R)", "Ω", "Resistência ôhmica do condutor ou receptor", 0.01, null, "10"),
                    CalcParam("I", "Corrente (I)", "A", "Corrente circulante no resistor", 0.0, null, "2")
                )
            ),
            CalculationItem(
                id = "lei_ohm_corrente",
                title = "Lei de Ohm (Corrente)",
                formula = "I = V / R",
                exampleUse = "Calcula a intensidade da corrente elétrica (I) de um resistor.",
                parameters = listOf(
                    CalcParam("V", "Tensão (V)", "V", "Diferença de potencial aplicada", 0.1, null, "127"),
                    CalcParam("R", "Resistência (R)", "Ω", "Resistência elétrica do dispositivo", 0.01, null, "20")
                )
            ),
            CalculationItem(
                id = "lei_ohm_resistencia",
                title = "Lei de Ohm (Resistência)",
                formula = "R = V / I",
                exampleUse = "Calcula a resistência (R) necessária para limitar ou drenar corrente.",
                parameters = listOf(
                    CalcParam("V", "Tensão (V)", "V", "Diferença de potencial aplicada", 0.1, null, "220"),
                    CalcParam("I", "Corrente (I)", "A", "Fluxo de elétrons demandado pela carga", 0.01, null, "10")
                )
            ),
            CalculationItem(
                id = "potencia_ativa_monofasica",
                title = "Potência Ativa (Monofásica)",
                formula = "P = V * I * FP",
                exampleUse = "Mede a potência consumida que realiza trabalho real em corrente alternada.",
                parameters = listOf(
                    CalcParam("V", "Tensão (V)", "V", "Tensão de fase monofásica (ou fase-neutro)", 1.0, null, "220"),
                    CalcParam("I", "Corrente (I)", "A", "Intensidade da corrente circulante", 0.0, null, "20"),
                    CalcParam("FP", "Fator de Potência (FP)", "", "Fator de defasamento angular (0 a 1.0)", 0.0, 1.0, "1.0")
                )
            ),
            CalculationItem(
                id = "potencia_ativa_trifasica",
                title = "Potência Ativa (Trifásica)",
                formula = "P = √3 * V * I * FP",
                exampleUse = "Mede a potência de trabalho real para cargas elétricas trifásicas.",
                parameters = listOf(
                    CalcParam("V", "Tensão (V)", "V", "Tensão de linha trifásica (fase-fase)", 1.0, null, "380"),
                    CalcParam("I", "Corrente (I)", "A", "Corrente que passa por condutor de fase", 0.0, null, "10"),
                    CalcParam("FP", "Fator de Potência (FP)", "", "Fator de proporcionalidade (0 a 1.0)", 0.0, 1.0, "0.85")
                )
            ),
            CalculationItem(
                id = "potencia_aparente_monofasica",
                title = "Potência Aparente (Monofásica)",
                formula = "S = V * I",
                exampleUse = "Potência total transmitida, muito usada para dimensionamento de transformadores.",
                parameters = listOf(
                    CalcParam("V", "Tensão (V)", "V", "Diferença de potencial da fase", 1.0, null, "220"),
                    CalcParam("I", "Corrente (I)", "A", "Corrente RMS no condutor", 0.0, null, "5")
                )
            ),
            CalculationItem(
                id = "potencia_aparente_trifasica",
                title = "Potência Aparente (Trifásica)",
                formula = "S = √3 * V * I",
                exampleUse = "Potência total em rede trifásica balanceada.",
                parameters = listOf(
                    CalcParam("V", "Tensão (V)", "V", "Tensão nominal entre fases", 1.0, null, "380"),
                    CalcParam("I", "Corrente (I)", "A", "Corrente nominal de fase", 0.0, null, "15")
                )
            ),
            CalculationItem(
                id = "fator_potencia",
                title = "Fator de Potência (FP)",
                formula = "FP = P / S",
                exampleUse = "Razão entre energia consumida (P) e energia entregue (S) na rede.",
                parameters = listOf(
                    CalcParam("P", "Potência Ativa (P)", "W", "Soma das potências ativas em Watts", 0.0, null, "1000"),
                    CalcParam("S", "Potência Aparente (S)", "VA", "Soma das potências aparentes em VA", 0.1, null, "1200")
                )
            ),

            // CONSUMO E MEDIDORES
            CalculationItem(
                id = "consumo_energia_kwh",
                title = "Consumo Mensal de Energia",
                formula = "Consumo = (P * Horas * Dias) / 1000",
                exampleUse = "Estima o consumo elétrico de um aparelho e o respectivo custo financeiro.",
                parameters = listOf(
                    CalcParam("P", "Potência (P)", "W", "Demanda nominal do equipamento", 1.0, null, "1200"),
                    CalcParam("Horas", "Horas por Dia", "h", "Tempo acumulado diário de funcionamento", 0.1, 24.0, "8"),
                    CalcParam("Dias", "Dias de Uso por Mês", "dias", "Dias médios no mês que o aparelho opera", 1.0, 31.0, "30"),
                    CalcParam("Tarifa", "Tarifa de Energia local", "R$/kWh", "Preço médio do kWh com impostos inclusos", 0.05, 5.0, "0.85")
                )
            ),

            // DIMENSIONAMENTOS NBR DIRECT
            CalculationItem(
                id = "corrente_projeto_monofasica",
                title = "Corrente de Projeto (Monofásica)",
                formula = "I = P / (V * FP)",
                exampleUse = "Calcula a corrente de linha real para dimensionar disjuntores monofásicos.",
                parameters = listOf(
                    CalcParam("P", "Potência Ativa (P)", "W", "Potência de trabalho real da carga", 1.0, null, "1500"),
                    CalcParam("V", "Tensão (V)", "V", "Tensão nominal de fornecimento", 50.0, 1000.0, "127"),
                    CalcParam("FP", "Fator de Potência (FP)", "", "Defasagem elétrica FP", 0.1, 1.0, "0.9")
                )
            ),
            CalculationItem(
                id = "corrente_projeto_trifasica",
                title = "Corrente de Projeto (Trifásica)",
                formula = "I = P / (√3 * V * FP)",
                exampleUse = "Determina a corrente de carga por fase para motores e equipamentos industriais.",
                parameters = listOf(
                    CalcParam("P", "Potência Ativa (P)", "W", "Potência real trifásica solicitada", 1.0, null, "5000"),
                    CalcParam("V", "Tensão (V)", "V", "Tensão nominal entre fases (ex: 380V ou 220V)", 50.0, 1000.0, "380"),
                    CalcParam("FP", "Fator de Potência (FP)", "", "Fator de eficiência do equipamento", 0.1, 1.0, "0.85")
                )
            ),
            CalculationItem(
                id = "dimensionamento_condutor_queda_tensao",
                title = "Condutores por Queda de Tensão",
                formula = "S = (k * L * I) / (C * V * %QMax)",
                exampleUse = "Avaliação da seção do fio ideal por queda de tensão admissível conforme NBR 5410.",
                parameters = listOf(
                    CalcParam("I", "Corrente de Projeto (I)", "A", "Corrente nominal de projeto circulando", 0.1, 500.0, "20"),
                    CalcParam("L", "Distância linear (L)", "m", "Distância da fonte até o ponto terminal", 1.0, 1000.0, "30"),
                    CalcParam("V", "Tensão do Circuito (V)", "V", "De acordo com os terminais (ex: 127V ou 220V)", 12.0, 1000.0, "220"),
                    CalcParam("Queda", "Queda Máxima (%Q)", "%", "Limite NBR 5410 (ex: 4% circuitos terminais)", 0.1, 7.0, "4.0")
                )
            ),
            CalculationItem(
                id = "dimensionamento_disjuntor",
                title = "Dimensionamento de Disjuntor",
                formula = "In >= Iz",
                exampleUse = "Proposição e avaliação da corrente nominal sugerida para proteção.",
                parameters = listOf(
                    CalcParam("Iz", "Corrente de Projeto (Iz)", "A", "Corrente calculada demandada pela carga", 0.1, 500.0, "25")
                )
            ),
            CalculationItem(
                id = "calculo_carga_instalada",
                title = "Carga Total Instalada",
                formula = "Soma (Iluminação + TUG + TUE + Outros)",
                exampleUse = "Soma as potências indicadas para estimar a carga total a instalar em VA/W.",
                parameters = listOf(
                    CalcParam("Ilum", "Iluminação Total", "W", "Soma de lâmpadas fixadas e estimadas", 0.0, null, "500"),
                    CalcParam("Tugs", "Tomadas Uso Geral", "W", "Tomadas comuns de 100W ou 600W", 0.0, null, "1000"),
                    CalcParam("Tues", "Tomadas Uso Específico", "W", "Ar condicionados, chuveiro, panelas de alta potência", 0.0, null, "5500"),
                    CalcParam("Outras", "Outras Cargas", "W", "Motores, portão de correr, etc", 0.0, null, "0")
                )
            )
        )
    }

    // Determine currently displayed calculations list
    val filteredCalculations = remember(state.selectedCategory) {
        when (state.selectedCategory) {
            AppCategory.Basico -> calculationItems.filter { it.id in listOf("lei_ohm_tensao", "lei_ohm_corrente", "lei_ohm_resistencia", "potencia_ativa_monofasica", "potencia_ativa_trifasica", "potencia_aparente_monofasica", "potencia_aparente_trifasica", "fator_potencia") }
            AppCategory.Consumo -> calculationItems.filter { it.id == "consumo_energia_kwh" }
            AppCategory.Dimensionamento -> calculationItems.filter { it.id in listOf("corrente_projeto_monofasica", "corrente_projeto_trifasica", "dimensionamento_condutor_queda_tensao", "dimensionamento_disjuntor", "calculo_carga_instalada") }
            AppCategory.GuiaNBR -> emptyList() // The guide UI is displayed as a statically tailored component instead!
        }
    }

    val activeCalc = remember(state.activeCalcId) {
        calculationItems.find { it.id == state.activeCalcId } ?: calculationItems.first()
    }

    // Calculation results derived state
    val computedResult = remember(state.paramValues, state.activeCalcId, state.conductorMaterialCobre, state.isPhaseMonofasico) {
        val currentVals = state.paramValues[state.activeCalcId] ?: emptyMap()
        
        fun parse(id: String): Double {
            return currentVals[id]?.toDoubleOrNull() ?: 0.0
        }

        val parsedMap = mutableMapOf<String, Double>()
        activeCalc.parameters.forEach { param ->
            parsedMap[param.id] = parse(param.id)
        }

        when (state.activeCalcId) {
            "lei_ohm_tensao" -> {
                val r = parsedMap["R"] ?: 0.0
                val i = parsedMap["I"] ?: 0.0
                val res = ElectricalCalculator.calculateLeiOhmTensao(r, i)
                String.format(Locale.US, "%.2f V", res)
            }
            "lei_ohm_corrente" -> {
                val v = parsedMap["V"] ?: 0.0
                val r = parsedMap["R"] ?: 1.0
                val res = ElectricalCalculator.calculateLeiOhmCorrente(v, r)
                String.format(Locale.US, "%.2f A", res)
            }
            "lei_ohm_resistencia" -> {
                val v = parsedMap["V"] ?: 0.0
                val i = parsedMap["I"] ?: 1.0
                val res = ElectricalCalculator.calculateLeiOhmResistencia(v, i)
                String.format(Locale.US, "%.2f Ω", res)
            }
            "potencia_ativa_monofasica" -> {
                val v = parsedMap["V"] ?: 0.0
                val i = parsedMap["I"] ?: 0.0
                val fp = parsedMap["FP"] ?: 1.0
                val res = ElectricalCalculator.calculatePotenciaAtivaMonofasica(v, i, fp)
                String.format(Locale.US, "%.2f W", res)
            }
            "potencia_ativa_trifasica" -> {
                val v = parsedMap["V"] ?: 0.0
                val i = parsedMap["I"] ?: 0.0
                val fp = parsedMap["FP"] ?: 0.85
                val res = ElectricalCalculator.calculatePotenciaAtivaTrifasica(v, i, fp)
                String.format(Locale.US, "%.2f W", res)
            }
            "potencia_aparente_monofasica" -> {
                val v = parsedMap["V"] ?: 0.0
                val i = parsedMap["I"] ?: 0.0
                val res = ElectricalCalculator.calculatePotenciaAparenteMonofasica(v, i)
                String.format(Locale.US, "%.2f VA", res)
            }
            "potencia_aparente_trifasica" -> {
                val v = parsedMap["V"] ?: 0.0
                val i = parsedMap["I"] ?: 0.0
                val res = ElectricalCalculator.calculatePotenciaAparenteTrifasica(v, i)
                String.format(Locale.US, "%.2f VA", res)
            }
            "fator_potencia" -> {
                val p = parsedMap["P"] ?: 0.0
                val s = parsedMap["S"] ?: 1.0
                val res = ElectricalCalculator.calculateFatorPotencia(p, s)
                if (res > 1.0) "Anômalo (> 1.0)" else String.format(Locale.US, "%.3f", res)
            }
            "consumo_energia_kwh" -> {
                val p = parsedMap["P"] ?: 0.0
                val coreHour = parsedMap["Horas"] ?: 0.0
                val coreDays = parsedMap["Dias"] ?: 0.0
                val rate = parsedMap["Tarifa"] ?: 0.85
                val consum = ElectricalCalculator.calculateConsumoEnergiaKwh(p, coreHour, coreDays)
                val cost = consum * rate
                String.format(Locale.US, "%.1f kWh (Custo: R$ %.2f)", consum, cost)
            }
            "corrente_projeto_monofasica" -> {
                val p = parsedMap["P"] ?: 0.0
                val v = parsedMap["V"] ?: 1.0
                val fp = parsedMap["FP"] ?: 1.0
                val res = ElectricalCalculator.calculateCorrenteProjetoMonofasica(p, v, fp)
                String.format(Locale.US, "%.2f A", res)
            }
            "corrente_projeto_trifasica" -> {
                val p = parsedMap["P"] ?: 0.0
                val v = parsedMap["V"] ?: 1.0
                val fp = parsedMap["FP"] ?: 1.0
                val res = ElectricalCalculator.calculateCorrenteProjetoTrifasica(p, v, fp)
                String.format(Locale.US, "%.2f A", res)
            }
            "dimensionamento_condutor_queda_tensao" -> {
                val i = parsedMap["I"] ?: 0.0
                val l = parsedMap["L"] ?: 0.0
                val v = parsedMap["V"] ?: 1.0
                val Q = parsedMap["Queda"] ?: 4.0
                val details = ElectricalCalculator.calculateQuedaTensao(
                    current = i,
                    distance = l,
                    voltage = v,
                    maxQuedaPercent = Q,
                    isCobre = state.conductorMaterialCobre,
                    isMonofasico = state.isPhaseMonofasico
                )
                if (details.suggestedGauge != null) {
                    "${details.suggestedGauge} mm² (Fração: ${String.format(Locale.US, "%.3f", details.calculatedSection)} mm²)"
                } else {
                    "Excede standard (>300 mm²)"
                }
            }
            "dimensionamento_disjuntor" -> {
                val iz = parsedMap["Iz"] ?: 0.0
                val sugg = ElectricalCalculator.suggestBreaker(iz)
                if (sugg != null) {
                    "${sugg.toInt()} A (Modelo standard de mercado)"
                } else {
                    "Excede standard (>125A)"
                }
            }
            "calculo_carga_instalada" -> {
                val ilum = parsedMap["Ilum"] ?: 0.0
                val tugs = parsedMap["Tugs"] ?: 0.0
                val tues = parsedMap["Tues"] ?: 0.0
                val outras = parsedMap["Outras"] ?: 0.0
                val res = ElectricalCalculator.calculateCargaInstalada(ilum, tugs, tues, outras)
                String.format(Locale.US, "%.1f W/VA", res)
            }
            else -> "0.00"
        }
    }

    // Main Column Layout with Multimeter Theme
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // App Header styled with rugged black metal details and indicator LEDs representing standard equipment
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Cálculos Elétricos",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("app_title")
                )
                Text(
                    text = "NBR 5410 • CONSOLE DE ENGENHARIA",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Glowing multimeter power LED
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFF39FF14), shape = CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.5f), shape = CircleShape)
                )
                Text(
                    text = "SYS ON",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = Color(0xFF39FF14)
                )
            }
        }

        // 1. DIGITAL LCD MULTIMETER SCREEN DISPLAY
        MultimeterLcdScreen(
            title = activeCalc.title,
            formula = activeCalc.formula,
            result = computedResult,
            example = activeCalc.exampleUse,
            selectedCalcId = state.activeCalcId,
            paramVals = state.paramValues[state.activeCalcId] ?: emptyMap(),
            conductorMaterialCobre = state.conductorMaterialCobre,
            isPhaseMonofasico = state.isPhaseMonofasico
        )

        // 2. CATEGORIES SELECTOR TABS
        HorizontalCategorySelector(
            selectedCategory = state.selectedCategory,
            onSelect = { viewModel.setCategory(it) }
        )

        // Main content switcher
        if (state.selectedCategory == AppCategory.GuiaNBR) {
            // Renders static comprehensive NBR lookup guide
            Box(modifier = Modifier.weight(1f)) {
                NbrInteractiveGuideView()
            }
        } else {
            // Split layout containing calculation sub-selection and the parameters input area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Calculation selector row within category
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 4.dp)
                ) {
                    items(filteredCalculations) { calc ->
                        val isSelected = calc.id == state.activeCalcId
                        val selectBg = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                        val selectText = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        val borderMod = if (isSelected) {
                            Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                        } else {
                            Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        }

                        Card(
                            onClick = { viewModel.selectCalculation(calc.id) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(52.dp)
                                .testTag("select_${calc.id}")
                                .then(borderMod),
                            colors = CardDefaults.cardColors(containerColor = selectBg)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = calc.title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = selectText
                                    )
                                    Text(
                                        text = calc.formula,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = selectText.copy(alpha = 0.7f),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // Dynamic workspace container displaying input fields and specialized guidelines
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    // Title section for the selected workspace with reset action button
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Parâmetros do Circuito",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Button(
                                onClick = { viewModel.resetInputs(state.activeCalcId) },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                modifier = Modifier.height(34.dp).testTag("btn_reset_params")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Restaurar Padrão",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Padrões", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    // Specialized Switches if Queda de Tensão dimensioning is active
                    if (state.activeCalcId == "dimensionamento_condutor_queda_tensao") {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().testTag("special_options_card"),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Configurações Adicionais da NBR 5410",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                    
                                    // 1. Material Selector Switch
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "Material do Condutor: " + if (state.conductorMaterialCobre) "Cobre (Cu)" else "Alumínio (Al)",
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                            Text(
                                                text = if (state.conductorMaterialCobre) "Condutividade: 58 MS/m (Padrão NBR)" else "Condutividade: 35.5 MS/m",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            FilterChip(
                                                selected = state.conductorMaterialCobre,
                                                onClick = { viewModel.setConductorMaterial(true) },
                                                label = { Text("Cobre") }
                                            )
                                            FilterChip(
                                                selected = !state.conductorMaterialCobre,
                                                onClick = { viewModel.setConductorMaterial(false) },
                                                label = { Text("Alumínio") }
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // 2. Phase layout Selector Switch
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "Tipo de Instalação: " + if (state.isPhaseMonofasico) "Monofásico" else "Trifásico",
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                            Text(
                                                text = if (state.isPhaseMonofasico) "Carga Bifilar (Fator k = 2.0)" else "Carga Trifásica Equilib. (Fator k = √3)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            FilterChip(
                                                selected = state.isPhaseMonofasico,
                                                onClick = { viewModel.setPhaseMonofasico(true) },
                                                label = { Text("Mono") }
                                            )
                                            FilterChip(
                                                selected = !state.isPhaseMonofasico,
                                                onClick = { viewModel.setPhaseMonofasico(false) },
                                                label = { Text("Tri") }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Loop through required parameters to generate precise user input fields
                    val inputVals = state.paramValues[state.activeCalcId] ?: emptyMap()
                    
                    items(activeCalc.parameters) { param ->
                        val currentTextValue = inputVals[param.id] ?: ""
                        
                        // Perform live bounds verification
                        val isOutOfRange = remember(currentTextValue) {
                            val numericalText = currentTextValue.toDoubleOrNull()
                            if (numericalText == null) {
                                true
                            } else {
                                val minConstraint = param.minVal
                                val maxConstraint = param.maxVal
                                val minViolated = minConstraint != null && numericalText < minConstraint
                                val maxViolated = maxConstraint != null && numericalText > maxConstraint
                                minViolated || maxViolated
                            }
                        }

                        OutlinedTextField(
                            value = currentTextValue,
                            onValueChange = { cleanInput ->
                                // Only allow numeric inputs, dots and commas, preventing crashes
                                val sanitized = cleanInput.filter { it.isDigit() || it == '.' || it == '-' }
                                viewModel.updateParamValue(state.activeCalcId, param.id, sanitized)
                            },
                            label = { Text(param.label) },
                            supportingText = {
                                if (isOutOfRange) {
                                    val rangeInfo = when {
                                        param.minVal != null && param.maxVal != null -> "Válido entre ${param.minVal} e ${param.maxVal}"
                                        param.minVal != null -> "Valor deve ser maior ou igual a ${param.minVal}"
                                        param.maxVal != null -> "Valor deve ser menor ou igual a ${param.maxVal}"
                                        else -> "Insira um número válido"
                                    }
                                    Text(text = "⚠️ $rangeInfo", color = MaterialTheme.colorScheme.error)
                                } else {
                                    Text(text = param.helperText)
                                }
                            },
                            isError = isOutOfRange,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            trailingIcon = {
                                if (param.unit.isNotEmpty()) {
                                    Text(
                                        text = param.unit,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_${param.id}"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        )
                    }

                    // Specialized engineering context feedback displays
                    item {
                        InteractiveGuidelineCard(
                            calcId = state.activeCalcId,
                            paramVals = inputVals,
                            isCobre = state.conductorMaterialCobre,
                            isMono = state.isPhaseMonofasico
                        )
                    }
                }
            }
        }
    }
}

// Beautiful Custom LCD Multimeter Screen
@Composable
fun MultimeterLcdScreen(
    title: String,
    formula: String,
    result: String,
    example: String,
    selectedCalcId: String,
    paramVals: Map<String, String>,
    conductorMaterialCobre: Boolean,
    isPhaseMonofasico: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("multimeter_lcd_screen"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141517)), // Solid carbon dark chassis
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // LCD header bar resembling a high-tech terminal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color(0xFFFFD54F), shape = CircleShape)
                    )
                    Text(
                        text = "VIRTUAL INSTRUMENT x64",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                        color = Color(0xFF94A3B8)
                    )
                }
                
                Text(
                    text = "AUTO RANGE",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold),
                    color = Color(0xFFFFB300)
                )
            }

            // Real glowing LCD display container (Liquid Crystal retro green panel style)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161E16), shape = RoundedCornerShape(10.dp)) // Deep LCD Green background
                    .border(1.dp, Color(0xFF2E3D2E), shape = RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title.uppercase(Locale.getDefault()),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color(0xFF64FFDA).copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "[NBR 5410 COMPLIANT]",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color(0xFF64FFDA).copy(alpha = 0.5f)
                        )
                    }

                    // Display actual resolved values cleanly
                    Text(
                        text = result,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 32.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        ),
                        color = Color(0xFF64FFDA), // Glowing digital green cyan text
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .testTag("lcd_main_result")
                    )

                    Divider(color = Color(0xFF2E3D2E))

                    // Formula view and live dynamic variables calculation dump
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "FÓRMULA: $formula",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            ),
                            color = Color(0xFF94A3B8)
                        )
                        
                        // Active inputs feedback dump
                        val inputsDump = paramVals.entries.joinToString(separator = ", ") { "${it.key}=${it.value}" }
                        Text(
                            text = "ENTRADAS: ($inputsDump)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp
                            ),
                            color = Color(0xFF64FFDA).copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }

            // Exemplo de uso display showing target helpful guidance standard behavior
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Ajuda",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp).padding(top = 1.dp)
                )
                Text(
                    text = "Dica Prática: $example",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// Tabbed categories selection utilizing nice rounded modern indicators
@Composable
fun HorizontalCategorySelector(
    selectedCategory: AppCategory,
    onSelect: (AppCategory) -> Unit
) {
    val categories = remember {
        listOf(
            AppCategory.Basico,
            AppCategory.Consumo,
            AppCategory.Dimensionamento,
            AppCategory.GuiaNBR
        )
    }

    LazyRow(
        modifier = Modifier.fillMaxWidth().testTag("horizontal_category_selector"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
    ) {
        items(categories) { cat ->
            val isSelected = cat == selectedCategory
            val activeColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(cat) }
                    .testTag("tag_tab_${cat.id}"),
                color = activeColor,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(cat.icon, fontSize = 14.sp)
                    Text(
                        text = cat.name,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                        ),
                        color = textColor
                    )
                }
            }
        }
    }
}

// Renders deep dynamic insights corresponding immediately with NBR standards rules based on active calculators
@Composable
fun InteractiveGuidelineCard(
    calcId: String,
    paramVals: Map<String, String>,
    isCobre: Boolean,
    isMono: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("interactive_guideline_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (calcId) {
                "dimensionamento_condutor_queda_tensao" -> {
                    val currentVal = paramVals["I"]?.toDoubleOrNull() ?: 20.0
                    val distVal = paramVals["L"]?.toDoubleOrNull() ?: 30.0
                    val voltVal = paramVals["V"]?.toDoubleOrNull() ?: 220.0
                    val maxQueda = paramVals["Queda"]?.toDoubleOrNull() ?: 4.0

                    val calcDetails = ElectricalCalculator.calculateQuedaTensao(
                        current = currentVal, distance = distVal, voltage = voltVal,
                        maxQuedaPercent = maxQueda, isCobre = isCobre, isMonofasico = isMono
                    )

                    Text(
                        text = "Análise Prática (Bitolas NBR 5410)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Draw a visual representing copper cable cross sections
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Seção transversal calculada necessária: " + String.format(Locale.US, "%.4f mm²", calcDetails.calculatedSection),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Limite em Volts admisso: " + String.format(Locale.US, "%.2f V", calcDetails.maxAllowedDropVolts),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Comparativo de Bitolas Comerciais:",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Renders a horizontal slider showing sizes, highlighting the chosen entry
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            items(ElectricalCalculator.commercialGaugesMm2) { gauge ->
                                val isValidGauge = gauge >= calcDetails.calculatedSection
                                val isBestSuggested = gauge == calcDetails.suggestedGauge
                                val surfaceBg = when {
                                    isBestSuggested -> MaterialTheme.colorScheme.primary
                                    isValidGauge -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                    else -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                }
                                val textCol = when {
                                    isBestSuggested -> MaterialTheme.colorScheme.onPrimary
                                    isValidGauge -> MaterialTheme.colorScheme.onSurface
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                }

                                Box(
                                    modifier = Modifier
                                        .background(surfaceBg, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "$gauge",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = if (isBestSuggested) FontWeight.ExtraBold else FontWeight.Normal,
                                                fontFamily = FontFamily.Monospace
                                            ),
                                            color = textCol
                                        )
                                        Text(
                                            text = if (isBestSuggested) "IDEAL" else if (isValidGauge) "OK" else "MIN",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, fontWeight = FontWeight.Bold),
                                            color = textCol.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                "dimensionamento_disjuntor" -> {
                    val iz = paramVals["Iz"]?.toDoubleOrNull() ?: 25.0
                    val suggested = ElectricalCalculator.suggestBreaker(iz)

                    Text(
                        text = "Diretrizes NBR 5410 de Sobrecarga",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "A corrente nominal do disjuntor (In) deve satisfazer: In >= Iz (Corrente de Projeto).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Correntes de Disjuntores Comuns (Mercado):",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        items(ElectricalCalculator.standardBreakerRatingsAmperes) { rating ->
                            val isChosen = rating == suggested
                            val isSufficient = rating >= iz
                            val backCol = when {
                                isChosen -> MaterialTheme.colorScheme.primary
                                isSufficient -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                else -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                            }
                            val textCol = when {
                                isChosen -> MaterialTheme.colorScheme.onPrimary
                                isSufficient -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                            }

                            Box(
                                modifier = Modifier
                                    .background(backCol, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${rating.toInt()}A",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = textCol
                                    )
                                    Text(
                                        text = if (isChosen) "SUGERIDO" else if (isSufficient) "OK" else "FRADO",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, fontWeight = FontWeight.Bold),
                                        color = textCol.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Curvas",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Lembre-se: Use Curva B para chuveiros/resistores, Curva C para tomadas de uso geral e pequenos motores, e Curva D para grandes motores industriais.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                "consumo_energia_kwh" -> {
                    val tariff = paramVals["Tarifa"]?.toDoubleOrNull() ?: 0.85
                    val watts = paramVals["P"]?.toDoubleOrNull() ?: 1200.0
                    val hours = paramVals["Horas"]?.toDoubleOrNull() ?: 8.0
                    val days = paramVals["Dias"]?.toDoubleOrNull() ?: 30.0

                    val consum = ElectricalCalculator.calculateConsumoEnergiaKwh(watts, hours, days)

                    Text(
                        text = "Projeção De Consumo Mensal",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Com base em $days dias operacionais por mês, e tarifa de R$ $tariff por kWh:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Consumo Anual Estimado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(String.format(Locale.US, "%.1f kWh", consum * 12), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        }
                        Column {
                            Text("Custo Anual Estimado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(String.format(Locale.US, "R$ %.2f", (consum * tariff) * 12), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                "calculo_carga_instalada" -> {
                    Text(
                        text = "Diretrizes NBR 5410 de Cargas Mínimas",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "• Iluminação: Mínimo 100VA para primeiros 6m², acrescido de 60VA por 4m² completos.\n" +
                               "• TUGs: Mínimo de 3 tomadas de 100VA para cozinha/área até 15m² ou 1 tomada por 5m.\n" +
                               "• TUEs: Atribuir a potência nominal direta de placas de aquecimento, chuveiros, etc.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    Text(
                        text = "Princípios Elétricos Básicos",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "Certifique-se de que as grandezas estão inseridas nas unidades correspondentes de engenharia para evitar desvios no projeto ou queima de receptores.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// Interactive lookup tables NBR 5410
@Composable
fun NbrInteractiveGuideView() {
    val dropLimits = remember { ElectricalCalculator.nbrVoltageDropLimits }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("nbr_interactive_guide"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "📖 NBR 5410 Guia Prático Rápido",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Compilação de parâmetros regulamentares essenciais recomendados para dimensionamento, planejamento e vistorias de circuitos prediais de baixa tensão.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // Section: Voltage Drop Limits (Tabela de Limites de Queda de Tensão)
        item {
            Column {
                Text(
                    text = "Quedas de Tensão Máximas Admissíveis",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        dropLimits.forEachIndexed { idx, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.point,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.weight(0.7f)
                                )
                                Box(
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                        .weight(0.3f),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Text(
                                        text = "${item.limitPercent}%",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Black
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (idx < dropLimits.size - 1) {
                                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            }
                        }
                    }
                }
            }
        }

        // Section: Conductivities (Condutividade Elétrica)
        item {
            Column {
                Text(
                    text = "Condutividade e Resistividade dos Metais (C=20°C)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Metal Condutor", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text("Condutividade (MS/m)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text("Resistividade (Ω·mm²/m)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        Divider(modifier = Modifier.padding(vertical = 6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Cobre (Cu)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            Text("58.0", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            Text("0.0172", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                        Divider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Alumínio (Al)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            Text("35.5", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            Text("0.0282", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        // Section: Commercial Gauges list
        item {
            Column {
                Text(
                    text = "Bitolas de Condutores Comerciais",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Bitolas padrões do mercado nacional em mm²:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        FlowLayoutRow(horizontalGap = 8.dp, verticalGap = 8.dp) {
                            val gaugesMm2 = ElectricalCalculator.commercialGaugesMm2
                            gaugesMm2.forEach { gauge ->
                                Box(
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "$gauge mm²",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Simple custom Flow Row implementation for rendering gauge tags neatly
@Composable
fun FlowLayoutRow(
    horizontalGap: androidx.compose.ui.unit.Dp,
    verticalGap: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(content = content) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        val layoutWidth = constraints.maxWidth
        
        var currentX = 0
        var currentY = 0
        var rowHeight = 0
        
        val itemPositions = mutableListOf<Triple<androidx.compose.ui.layout.Placeable, Int, Int>>()
        
        val hGapPx = horizontalGap.roundToPx()
        val vGapPx = verticalGap.roundToPx()
        
        for (placeable in placeables) {
            if (currentX + placeable.width > layoutWidth) {
                currentX = 0
                currentY += rowHeight + vGapPx
                rowHeight = 0
            }
            itemPositions.add(Triple(placeable, currentX, currentY))
            currentX += placeable.width + hGapPx
            rowHeight = maxOf(rowHeight, placeable.height)
        }
        
        val totalHeight = currentY + rowHeight
        
        layout(layoutWidth, totalHeight) {
            for ((placeable, x, y) in itemPositions) {
                placeable.placeRelative(x, y)
            }
        }
    }
}
