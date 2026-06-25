package com.eazpire.wear.discovery

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.eazpire.wear.R
import com.eazpire.wear.theme.EazWearColors
import java.io.ByteArrayOutputStream
import kotlin.math.max

private data class StrokePath(val points: List<Offset>)

/** Draw-first canvas workflow phases (placement + save handled by [ArtifactArScreen]). */
enum class ArCanvasPhase {
    Draw,
    Place,
}

@Composable
fun ArtifactArCanvasOverlay(
    phase: ArCanvasPhase,
    isSaving: Boolean,
    isPlaced: Boolean,
    placementScale: Float,
    onPlacementScaleChange: (Float) -> Unit,
    onDrawComplete: (ByteArray) -> Unit,
    onSave: () -> Unit,
    onCancelPlacement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strokes = remember { mutableStateListOf<StrokePath>() }
    var currentStroke by remember { mutableStateOf<MutableList<Offset>?>(null) }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val canvasWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val canvasHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    Box(modifier = modifier.fillMaxSize().zIndex(4f)) {
        when (phase) {
            ArCanvasPhase.Draw -> {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentStroke = mutableListOf(offset)
                                },
                                onDrag = { change, _ ->
                                    currentStroke?.add(change.position)
                                    change.consume()
                                },
                                onDragEnd = {
                                    currentStroke?.let { pts ->
                                        if (pts.size >= 2) strokes.add(StrokePath(pts.toList()))
                                    }
                                    currentStroke = null
                                },
                                onDragCancel = { currentStroke = null },
                            )
                        },
                ) {
                    val strokeStyle = Stroke(
                        width = 6f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    )
                    strokes.forEach { stroke ->
                        if (stroke.points.size < 2) return@forEach
                        val path = ComposePath().apply {
                            moveTo(stroke.points.first().x, stroke.points.first().y)
                            stroke.points.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(path, Color.Black, style = strokeStyle)
                    }
                    currentStroke?.let { pts ->
                        if (pts.size >= 2) {
                            val path = ComposePath().apply {
                                moveTo(pts.first().x, pts.first().y)
                                pts.drop(1).forEach { lineTo(it.x, it.y) }
                            }
                            drawPath(path, Color.Black, style = strokeStyle)
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.artifact_ar_canvas_draw_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = EazWearColors.HubText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 120.dp, start = 16.dp, end = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(EazWearColors.HubPanel.copy(alpha = 0.88f))
                        .padding(12.dp),
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 72.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex)
                        },
                        enabled = strokes.isNotEmpty(),
                    ) {
                        Icon(
                            Icons.Filled.Undo,
                            contentDescription = stringResource(R.string.artifact_ar_canvas_undo),
                            tint = EazWearColors.HubText,
                        )
                    }
                    Button(
                        onClick = {
                            if (strokes.isEmpty()) return@Button
                            val png = exportStrokesToPng(
                                strokes = strokes,
                                widthPx = max(canvasWidthPx.toInt(), 512),
                                heightPx = max(canvasHeightPx.toInt(), 512),
                            )
                            onDrawComplete(png)
                        },
                        enabled = strokes.isNotEmpty(),
                    ) {
                        Text(stringResource(R.string.artifact_ar_canvas_place))
                    }
                }
            }

            ArCanvasPhase.Place -> {
                Text(
                    text = if (isPlaced) {
                        stringResource(R.string.artifact_ar_canvas_place_adjust_hint)
                    } else {
                        stringResource(R.string.artifact_ar_canvas_place_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = EazWearColors.HubText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 120.dp, start = 16.dp, end = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(EazWearColors.HubPanel.copy(alpha = 0.88f))
                        .padding(12.dp),
                )

                if (isPlaced) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(EazWearColors.HubPanel.copy(alpha = 0.9f))
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = {
                                onPlacementScaleChange((placementScale * 0.85f).coerceIn(0.3f, 3f))
                            },
                        ) {
                            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.artifact_ar_canvas_scale_down), tint = EazWearColors.HubText)
                        }
                        Text(
                            text = "${(placementScale * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = EazWearColors.HubOrange,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        IconButton(
                            onClick = {
                                onPlacementScaleChange((placementScale * 1.15f).coerceIn(0.3f, 3f))
                            },
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.artifact_ar_canvas_scale_up), tint = EazWearColors.HubText)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 72.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onCancelPlacement) {
                            Text(stringResource(R.string.artifact_ar_canvas_cancel), color = EazWearColors.HubText)
                        }
                        Button(
                            onClick = onSave,
                            enabled = !isSaving,
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.artifact_ar_canvas_save), modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        }

        if (isSaving) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = EazWearColors.HubOrange)
                    Text(
                        stringResource(R.string.artifact_ar_canvas_saving),
                        color = EazWearColors.HubText,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

private fun exportStrokesToPng(
    strokes: List<StrokePath>,
    widthPx: Int,
    heightPx: Int,
): ByteArray {
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(android.graphics.Color.TRANSPARENT)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    strokes.forEach { stroke ->
        if (stroke.points.size < 2) return@forEach
        val path = Path()
        val first = stroke.points.first()
        path.moveTo(first.x, first.y)
        stroke.points.drop(1).forEach { path.lineTo(it.x, it.y) }
        canvas.drawPath(path, paint)
    }
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
    bitmap.recycle()
    return stream.toByteArray()
}
