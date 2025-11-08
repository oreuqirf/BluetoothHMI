package com.example.bluetoothhmi.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import kotlin.math.roundToInt

/**
 * Un Composable que dibuja un gráfico de línea simple para datos en tiempo real.
 */
@Composable
fun RealTimeLineChart(
    data: List<Float>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) {
        // No dibujar nada si no hay datos
        return
    }

    val density = LocalDensity.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)

    // --- Configuración de pintura de texto para los ejes ---
    val textPaint = remember(density) {
        Paint().apply {
            color = AndroidColor.BLACK
            textAlign = Paint.Align.CENTER
            textSize = density.run { 12.sp.toPx() }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val padding = 16.dp.toPx() // Padding para las etiquetas

        // --- Calcular rangos de datos ---
        val minData = data.minOrNull() ?: 0f
        val maxData = data.maxOrNull() ?: 100f
        // Asegurarse de que el rango no sea cero
        val (minGrid, maxGrid) = getNiceGridBounds(minData, maxData)
        val gridRange = (maxGrid - minGrid).coerceAtLeast(1f)


        // --- Dibujar líneas de la cuadrícula (ejes Y) ---
        val yAxisLabelCount = 5

        (0..yAxisLabelCount).forEach { i ->
            val y = height - padding - (i.toFloat() / yAxisLabelCount) * (height - 2 * padding)
            val labelValue = minGrid + (i.toFloat() / yAxisLabelCount) * gridRange

            // Dibuja la línea de la cuadrícula
            drawLine(
                color = gridColor,
                start = androidx.compose.ui.geometry.Offset(padding, y),
                end = androidx.compose.ui.geometry.Offset(width, y),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round
            )
            // Dibuja la etiqueta de texto
            drawContext.canvas.nativeCanvas.drawText(
                "%.1f".format(labelValue),
                padding / 2, // Posición X de la etiqueta
                y + textPaint.textSize / 3, // Centrado vertical
                textPaint
            )
        }

        // --- Preparar el Path de la curva ---
        val linePath = Path()
        val gradientPath = Path()

        // Mapear puntos de datos a coordenadas del canvas
        val points = data.mapIndexed { index, value ->
            // Asegurarse de que el divisor no sea cero si solo hay un punto
            val denominator = (data.size - 1).coerceAtLeast(1)
            val x = padding + (index.toFloat() / denominator) * (width - 2 * padding)
            // Mapea el valor 'y', asegurándose de que no se invierta si el rango es 0
            val y = height - padding - ((value - minGrid) / gridRange.coerceAtLeast(1f)) * (height - 2 * padding)
            x to y
        }

        if (points.isNotEmpty()) {
            linePath.moveTo(points.first().first, points.first().second)
            gradientPath.moveTo(points.first().first, height - padding)
            gradientPath.lineTo(points.first().first, points.first().second)

            // Crear una curva suave (spline)
            for (i in 0 until points.size - 1) {
                val p1 = points[i]
                val p2 = points[i + 1]
                val midPointX = (p1.first + p2.first) / 2
                // Curva cuadrática de Bézier
                linePath.quadraticBezierTo(p1.first, p1.second, midPointX, (p1.second + p2.second) / 2)
                gradientPath.quadraticBezierTo(p1.first, p1.second, midPointX, (p1.second + p2.second) / 2)
            }
            // Añadir el último punto
            val lastPoint = points.last()
            linePath.lineTo(lastPoint.first, lastPoint.second)
            gradientPath.lineTo(lastPoint.first, lastPoint.second)

            // Cerrar el path del gradiente en la parte inferior
            gradientPath.lineTo(lastPoint.first, height - padding)
            gradientPath.close()
        }

        // --- Dibujar el gradiente debajo de la línea ---
        drawPath(
            path = gradientPath,
            brush = Brush.verticalGradient(
                colors = listOf(primaryColor.copy(alpha = 0.3f), Color.Transparent),
                startY = 0f,
                endY = height - padding
            )
        )

        // --- Dibujar la línea de la curva ---
        drawPath(
            path = linePath,
            color = primaryColor,
            style = Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        )
    }
}

/**
 * Calcula límites "agradables" (ej: 0-100 en lugar de 3.7-98.2) para el eje Y.
 */
private fun getNiceGridBounds(minData: Float, maxData: Float): Pair<Float, Float> {
    if (minData == maxData) {
        // Si los datos son planos (ej: todos son 50), crea un rango
        return Pair(minData - 5f, maxData + 5f)
    }
    // Lógica simple: redondear hacia abajo y hacia arriba al múltiplo de 10 más cercano
    val minGrid = (minData / 10).toInt() * 10f
    val maxGrid = ((maxData / 10).toInt() + 1) * 10f

    // Asegurarse de que maxGrid sea al menos un poco mayor que minGrid
    return Pair(minGrid, (maxGrid).coerceAtLeast(minGrid + 1f))
}
