package com.example.prioritize.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun InteractiveGraph(
    importance: Int,          // 1 to 10
    urgency: Int,             // 1 to 10
    estimatedMinutes: Int,    // 5 to 4800+
    onValueChange: (importance: Int, urgency: Int, estimatedMinutes: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val durationSliderValue = minutesToSlider(estimatedMinutes)

    var width by remember { mutableStateOf(0f) }
    var height by remember { mutableStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF151522))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "3D Prioritization Cube",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Text(
            text = "Visualize tasks in 3D: Urgency (Width), Duration (Depth), Importance (Height)",
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // 3D Isometric Viewport (Visual feedback only, no gesture conflict)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.3f)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                width = size.width
                height = size.height

                // Isometric math constants
                val cos30 = 0.8660254f
                val sin30 = 0.5f

                // Scale sizes of the wireframe cube
                val scaleX = width * 0.35f
                val scaleY = width * 0.26f // Y represents Depth (Duration)
                val scaleZ = height * 0.45f // Z represents Height (Importance)

                // Center Origin of the cube base (shifted down slightly for Z-up height)
                val originX = width * 0.5f
                val originY = height * 0.72f

                // Z-up isometric projection:
                // x3d = Urgency (left-to-right positive)
                // y3d = Duration (front-to-back positive)
                // z3d = Importance (bottom-to-top positive)
                fun project(x3d: Float, y3d: Float, z3d: Float): Offset {
                    val px = originX + (x3d * scaleX * cos30) - (y3d * scaleY * cos30)
                    val py = originY + (x3d * scaleX * sin30) + (y3d * scaleY * sin30) - (z3d * scaleZ)
                    return Offset(px, py)
                }

                // 1. Draw Cube Floor Grid (bottom plane z=0)
                val gridColor = Color(0xFF28283C)
                val activeGridColor = Color(0xFFBB86FC).copy(alpha = 0.04f)

                // Render floor grid lines (10x10) Urgency vs. Duration
                for (i in 0..10) {
                    val frac = i / 10f
                    // Draw lines parallel to Y (depth) axis
                    drawLine(
                        color = gridColor,
                        start = project(frac, 0f, 0f),
                        end = project(frac, 1f, 0f),
                        strokeWidth = 1.dp.toPx()
                    )
                    // Draw lines parallel to X (urgency) axis
                    drawLine(
                        color = gridColor,
                        start = project(0f, frac, 0f),
                        end = project(1f, frac, 0f),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Highlight active floor region
                val activeFloorPath = Path().apply {
                    val p1 = project(0.5f, 0.5f, 0f)
                    val p2 = project(1.0f, 0.5f, 0f)
                    val p3 = project(1.0f, 1.0f, 0f)
                    val p4 = project(0.5f, 1.0f, 0f)
                    moveTo(p1.x, p1.y)
                    lineTo(p2.x, p2.y)
                    lineTo(p3.x, p3.y)
                    lineTo(p4.x, p4.y)
                    close()
                }
                drawPath(activeFloorPath, activeGridColor)

                // 2. Draw Wireframe Cube Edges (Z-up Box)
                val wireframeColor = Color(0xFF42425A)
                val basePoints = listOf(
                    Pair(0f, 0f), Pair(1f, 0f), Pair(1f, 1f), Pair(0f, 1f)
                )

                // Draw base and top rings
                for (i in 0..3) {
                    val next = (i + 1) % 4
                    val vCurrent = basePoints[i]
                    val vNext = basePoints[next]

                    // Floor ring (z=0)
                    drawLine(
                        color = wireframeColor,
                        start = project(vCurrent.first, vCurrent.second, 0f),
                        end = project(vNext.first, vNext.second, 0f),
                        strokeWidth = 1.5.dp.toPx()
                    )
                    // Roof ring (z=1)
                    drawLine(
                        color = wireframeColor,
                        start = project(vCurrent.first, vCurrent.second, 1f),
                        end = project(vNext.first, vNext.second, 1f),
                        strokeWidth = 1.5.dp.toPx()
                    )
                    // Vertical pillars connecting floor and roof
                    drawLine(
                        color = wireframeColor,
                        start = project(vCurrent.first, vCurrent.second, 0f),
                        end = project(vCurrent.first, vCurrent.second, 1f),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }

                // 3. Project current coordinates inside unit space (0..1)
                val currentX = (urgency - 1) / 9f             // X = Urgency (Eisenhower Horizontal)
                val currentY = (importance - 1) / 9f          // Y = Importance (Eisenhower Depth)
                val currentZ = (durationSliderValue - 1) / 9f    // Z = Duration (Vertical Height)

                val floorOffset = project(currentX, currentY, 0f)
                val nodeOffset = project(currentX, currentY, currentZ)

                // Draw vertical dashed line from floor to floating node
                drawLine(
                    color = Color(0xFFBB86FC).copy(alpha = 0.5f),
                    start = floorOffset,
                    end = nodeOffset,
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                        floatArrayOf(10f, 10f), 0f
                    )
                )

                // Help circle on floor
                drawCircle(
                    color = Color(0xFFBB86FC).copy(alpha = 0.2f),
                    radius = 8.dp.toPx(),
                    center = floorOffset
                )

                // 4. Draw Floating Node (Current 3D position)
                drawCircle(
                    color = Color(0xFF03DAC6).copy(alpha = 0.3f),
                    radius = 16.dp.toPx(),
                    center = nodeOffset
                )
                drawCircle(
                    color = Color(0xFF03DAC6),
                    radius = 7.dp.toPx(),
                    center = nodeOffset
                )
                drawCircle(
                    color = Color.White,
                    radius = 7.dp.toPx(),
                    center = nodeOffset,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Sliders Section
        // 1. Urgency Slider (X-Axis)
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Urgency (X-Axis / Urgent)", color = Color.Gray, fontSize = 13.sp)
                Text("$urgency / 10", color = Color(0xFF03DAC6), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Slider(
                value = urgency.toFloat(),
                onValueChange = { onValueChange(importance, it.roundToInt(), estimatedMinutes) },
                valueRange = 1f..10f,
                steps = 8,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF03DAC6),
                    activeTrackColor = Color(0xFF03DAC6),
                    inactiveTrackColor = Color(0xFF28283C)
                )
            )
        }

        // 2. Importance Slider (Y-Axis)
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Importance (Y-Axis / Important)", color = Color.Gray, fontSize = 13.sp)
                Text("$importance / 10", color = Color(0xFFBB86FC), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Slider(
                value = importance.toFloat(),
                onValueChange = { onValueChange(it.roundToInt(), urgency, estimatedMinutes) },
                valueRange = 1f..10f,
                steps = 8,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFBB86FC),
                    activeTrackColor = Color(0xFFBB86FC),
                    inactiveTrackColor = Color(0xFF28283C)
                )
            )
        }

        // 3. Duration Slider (Z-Axis / Height)
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Duration (Z-Axis / Height)", color = Color.Gray, fontSize = 13.sp)
                Text(formatDuration(estimatedMinutes), color = Color(0xFF03DAC6), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Slider(
                value = durationSliderValue.toFloat(),
                onValueChange = { newValue ->
                    val calculatedMinutes = sliderToMinutes(newValue.roundToInt())
                    onValueChange(importance, urgency, calculatedMinutes)
                },
                valueRange = 1f..10f,
                steps = 8,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF03DAC6),
                    activeTrackColor = Color(0xFF03DAC6),
                    inactiveTrackColor = Color(0xFF28283C)
                )
            )
        }
    }
}

// Convert 1..10 slider value to ADHD-friendly task durations
fun sliderToMinutes(value: Int): Int {
    return when (value) {
        1 -> 5      // 5 min (Quick win)
        2 -> 15     // 15 min
        3 -> 30     // 30 min
        4 -> 60     // 1 hr
        5 -> 120    // 2 hr
        6 -> 240    // 4 hr
        7 -> 480    // 8 hr (1 full day)
        8 -> 1440   // 24 hr (3 days elapsed time)
        9 -> 2400   // 40 hr (1 week elapsed)
        10 -> 4800  // 80 hr+ (multi-week project)
        else -> 15
    }
}

fun minutesToSlider(minutes: Int): Int {
    return when {
        minutes <= 5 -> 1
        minutes <= 15 -> 2
        minutes <= 30 -> 3
        minutes <= 60 -> 4
        minutes <= 120 -> 5
        minutes <= 240 -> 6
        minutes <= 480 -> 7
        minutes <= 1440 -> 8
        minutes <= 2400 -> 9
        else -> 10
    }
}

fun formatDuration(minutes: Int): String {
    return when {
        minutes < 60 -> "$minutes min"
        minutes < 1440 -> "${minutes / 60} hr"
        else -> "${minutes / 1440} day"
    }
}
