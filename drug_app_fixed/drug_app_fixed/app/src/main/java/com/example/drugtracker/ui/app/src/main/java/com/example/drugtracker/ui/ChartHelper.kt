package com.example.drugtracker.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import com.example.drugtracker.data.DrugInfo
import com.example.drugtracker.data.MedicationRecord
import com.example.drugtracker.logic.DrugCalculator
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.SimpleDateFormat
import java.util.*

object ChartHelper {

    private val drugColors = mapOf(
        "草酸艾司西酞普兰"     to Color.parseColor("#FF6B6B"),
        "拉莫三嗪"             to Color.parseColor("#4ECDC4"),
        "丁螺环酮"             to Color.parseColor("#45B7D1"),
        "优甲乐（左甲状腺素）"  to Color.parseColor("#FF1744"),
        "加巴喷丁"             to Color.parseColor("#96CEB4"),
        "劳拉西泮"             to Color.parseColor("#FFD93D"),
        "酒石酸唑吡坦"         to Color.parseColor("#DDA0DD"),
        "右佐匹克隆"           to Color.parseColor("#98D8C8"),
        "布洛芬"               to Color.parseColor("#F7DC6F"),
        "对乙酰氨基酚"         to Color.parseColor("#BB8FCE"),
        "托莫西汀"             to Color.parseColor("#85C1E9"),
        "哌甲酯"               to Color.parseColor("#F8C471"),
        "咖啡因"               to Color.parseColor("#82E0AA"),
        "茶苯海明"             to Color.parseColor("#F1948A"),
        "褪黑素"               to Color.parseColor("#A569BD"),
        "茶氨酸"               to Color.parseColor("#5DADE2"),
        "苏糖酸镁"             to Color.parseColor("#58D68D"),
        "茴拉西坦"             to Color.parseColor("#EC7063"),
        "长春西汀"             to Color.parseColor("#5499C7")
    )

    private val fallbackColors = listOf(
        Color.parseColor("#E74C3C"), Color.parseColor("#3498DB"),
        Color.parseColor("#2ECC71"), Color.parseColor("#E67E22"),
        Color.parseColor("#9B59B6"), Color.parseColor("#1ABC9C")
    )

    fun getDrugColor(drugName: String, index: Int = 0): Int =
        drugColors[drugName] ?: fallbackColors[index % fallbackColors.size]

    // 判断当前是否为深色模式
    private fun isNightMode(context: Context): Boolean {
        val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightMode == Configuration.UI_MODE_NIGHT_YES
    }

    fun setupChart(chart: LineChart, context: Context, isFullscreen: Boolean = false) {
        // 根据模式决定文字颜色：夜间白色，日间黑色
        val textColor = if (isNightMode(context)) Color.WHITE else Color.BLACK

        chart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            legend.isEnabled = true
            legend.textSize = if (isFullscreen) 13f else 10f
            legend.textColor = textColor
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                labelRotationAngle = -30f
                setDrawGridLines(true)
                granularity = 60f
                textSize = 9f
                textColor = textColor
            }
            axisLeft.apply {
                setDrawGridLines(true)
                axisMinimum = 0f
                textSize = 10f
                textColor = textColor
            }
            axisRight.isEnabled = false
            setNoDataText("暂无药物记录")
            setNoDataTextColor(textColor)
        }
    }

    fun updateChartData(
        chart: LineChart,
        records: List<MedicationRecord>,
        drugs: List<DrugInfo>,
        weightKg: Double,
        bodyFatPercent: Double,
        startTimeMs: Long,
        endTimeMs: Long,
        nowMs: Long = System.currentTimeMillis(),
        therapyLow: Float = 0f,
        therapyHigh: Float = 0f
    ) {
        val dataSets = mutableListOf<LineDataSet>()
        val timePoints = generateTimePoints(startTimeMs, endTimeMs)

        drugs.forEachIndexed { index, drug ->
            if (records.none { it.drugName == drug.name }) return@forEachIndexed

            val entries = timePoints.map { timeMs ->
                val pct = DrugCalculator.totalConcentrationPercent(
                    records, drug, weightKg, bodyFatPercent, timeMs
                ).toFloat()
                Entry((timeMs / (1000 * 60)).toFloat(), pct)
            }

            if (entries.any { it.y > 0.5f }) {
                val color = getDrugColor(drug.name, index)
                dataSets.add(LineDataSet(entries, drug.name).apply {
                    this.color = color
                    lineWidth = 2.5f
                    setDrawCircles(false)
                    setDrawValues(false)
                    setDrawFilled(true)
                    fillColor = color
                    fillAlpha = 40
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                })
            }
        }

        val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        }
        chart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String =
                sdf.format(Date(value.toLong() * 60 * 1000))
        }

        chart.xAxis.removeAllLimitLines()
        chart.axisLeft.removeAllLimitLines()

        chart.xAxis.addLimitLine(LimitLine((nowMs / (1000 * 60)).toFloat(), "现在").apply {
            lineColor = Color.RED
            lineWidth = 1.5f
            enableDashedLine(10f, 5f, 0f)
            textColor = Color.RED
            textSize = 10f
        })

        if (therapyLow > 0) {
            chart.axisLeft.addLimitLine(LimitLine(therapyLow, "治疗窗下限").apply {
                lineColor = Color.parseColor("#00AA00")
                lineWidth = 1f
                enableDashedLine(8f, 4f, 0f)
                textColor = Color.parseColor("#00AA00")
                textSize = 9f
                labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
            })
        }
        if (therapyHigh > 0) {
            chart.axisLeft.addLimitLine(LimitLine(therapyHigh, "治疗窗上限").apply {
                lineColor = Color.parseColor("#00AA00")
                lineWidth = 1f
                enableDashedLine(8f, 4f, 0f)
                textColor = Color.parseColor("#00AA00")
                textSize = 9f
                labelPosition = LimitLine.LimitLabelPosition.RIGHT_BOTTOM
            })
        }

        chart.axisLeft.addLimitLine(LimitLine(100f, "1个剂量").apply {
            lineColor = Color.parseColor("#888888")
            lineWidth = 1f
            enableDashedLine(8f, 4f, 0f)
            textColor = Color.parseColor("#888888")
            textSize = 9f
            labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
        })

        if (dataSets.isEmpty()) {
            chart.clear()
            chart.setNoDataText("该分类暂无记录")
        } else {
            val lineData = LineData()
            dataSets.forEach { lineData.addDataSet(it) }
            chart.data = lineData
        }
        chart.invalidate()
    }

    fun generateTimePoints(startMs: Long, endMs: Long, stepMinutes: Int = 15): List<Long> {
        val points = mutableListOf<Long>()
        var current = startMs
        val stepMs = stepMinutes * 60 * 1000L
        while (current <= endMs) {
            points.add(current)
            current += stepMs
        }
        return points
    }

    fun focusOnDrug(chart: LineChart, focusDrugName: String) {
        chart.data?.dataSets?.forEachIndexed { index, dataSet ->
            (dataSet as? LineDataSet)?.let { ds ->
                if (ds.label == focusDrugName) {
                    ds.lineWidth = 4f
                    ds.fillAlpha = 80
                } else {
                    ds.lineWidth = 1f
                    ds.color = Color.argb(50, 180, 180, 180)
                    ds.fillAlpha = 15
                }
            }
        }
        chart.invalidate()
    }

    fun resetFocus(chart: LineChart) {
        chart.data?.dataSets?.forEachIndexed { index, dataSet ->
            (dataSet as? LineDataSet)?.let { ds ->
                val color = getDrugColor(ds.label ?: "", index)
                ds.lineWidth = 2.5f
                ds.color = color
                ds.fillColor = color
                ds.fillAlpha = 40
            }
        }
        chart.invalidate()
    }
}
