package de.mrt.rich2euro.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.mrt.rich2euro.R
import de.mrt.rich2euro.model.CoinSide
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "CoinCameraCapture"
private const val CIRCLE_RADIUS_SCREEN_RATIO = 0.38f
private const val MAX_CROPPED_OUTPUT_PX = 1024

@Composable
fun CoinCameraCaptureScreen(
    side: CoinSide,
    onPhotoCaptured: (Uri) -> Unit,
    onCancel: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var isCapturing by remember { mutableStateOf(false) }
    var isTorchEnabled by remember { mutableStateOf(false) }
    var hasFlashUnit by remember { mutableStateOf(false) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    BackHandler(enabled = !isCapturing, onBack = onCancel)

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    val cameraFailMessage = stringResource(R.string.camera_launch_failed)
    val maskBackgroundColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.68f)
    val outerGuideColor = MaterialTheme.colorScheme.secondary
    val innerGuideColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
    val cropCornerFillArgb = MaterialTheme.colorScheme.surfaceContainerLowest.toArgb()

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)

        val listener = Runnable {
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val selector = when {
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ->
                        CameraSelector.DEFAULT_BACK_CAMERA
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ->
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> CameraSelector.DEFAULT_BACK_CAMERA
                }

                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    imageCapture
                )
                boundCamera = camera
                hasFlashUnit = camera.cameraInfo.hasFlashUnit()

                // Auto-focus center where the coin circle sits
                previewView.post {
                    if (previewView.width > 0 && previewView.height > 0) {
                        val point = previewView.meteringPointFactory.createPoint(
                            previewView.width / 2f,
                            previewView.height / 2f
                        )
                        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                            .setAutoCancelDuration(3, TimeUnit.SECONDS)
                            .build()
                        camera.cameraControl.startFocusAndMetering(action)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind CameraX use cases", e)
                onError(cameraFailMessage)
            }
        }

        cameraProviderFuture.addListener(listener, mainExecutor)

        onDispose {
            try {
                cameraProviderFuture.get().unbindAll()
            } catch (_: Exception) {
                // Ignore cleanup errors
            }
        }
    }

    val captureAndCropCoin: () -> Unit = {
        if (!isCapturing) {
            isCapturing = true
            val mainExecutor = ContextCompat.getMainExecutor(context)
            val snapshotViewport = viewportSize

            imageCapture.takePicture(
                mainExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        coroutineScope.launch {
                            try {
                                val savedUri = withContext(Dispatchers.Default) {
                                    val rawBitmap = image.use { proxy ->
                                        val bmp = proxy.toBitmap()
                                        val rotation = proxy.imageInfo.rotationDegrees
                                        rotateBitmapIfNeeded(bmp, rotation)
                                    }
                                    val croppedCircleBitmap = cropBitmapToViewfinderCircle(
                                        uprightBitmap = rawBitmap,
                                        viewWidth = snapshotViewport.width,
                                        viewHeight = snapshotViewport.height,
                                        backgroundFillArgb = cropCornerFillArgb
                                    )
                                    saveCroppedCoinBitmap(
                                        context = context,
                                        side = side,
                                        bitmap = croppedCircleBitmap
                                    )
                                }
                                isCapturing = false
                                onPhotoCaptured(savedUri)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed cropping captured coin image", e)
                                isCapturing = false
                                onError(cameraFailMessage)
                            }
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "ImageCapture failed", exception)
                        isCapturing = false
                        onError(cameraFailMessage)
                    }
                }
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim)
            .onSizeChanged { viewportSize = it }
            .pointerInput(boundCamera) {
                detectTapGestures { tapOffset ->
                    val cam = boundCamera ?: return@detectTapGestures
                    if (previewView.width > 0 && previewView.height > 0) {
                        val point = previewView.meteringPointFactory.createPoint(
                            tapOffset.x,
                            tapOffset.y
                        )
                        val action = FocusMeteringAction.Builder(point).build()
                        cam.cameraControl.startFocusAndMetering(action)
                    }
                }
            }
            .testTag("coin_camera_screen")
    ) {
        // 1. Live CameraX Viewfinder
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Darkened Vignette with Transparent Circular Coin Cutout & Bi-Metallic Ring Guide
        Canvas(modifier = Modifier.fillMaxSize()) {
            val minDim = min(size.width, size.height)
            val circleRadius = minDim * CIRCLE_RADIUS_SCREEN_RATIO
            val center = Offset(size.width / 2f, size.height / 2f)

            val circlePath = Path().apply {
                addOval(
                    Rect(
                        left = center.x - circleRadius,
                        top = center.y - circleRadius,
                        right = center.x + circleRadius,
                        bottom = center.y + circleRadius
                    )
                )
            }

            // Darken everything OUTSIDE the coin circle
            clipPath(circlePath, clipOp = ClipOp.Difference) {
                drawRect(color = maskBackgroundColor)
            }

            // Outer bi-metallic coin rim target circle
            drawCircle(
                color = outerGuideColor,
                radius = circleRadius,
                center = center,
                style = Stroke(width = 3.5.dp.toPx())
            )

            // Subtle inner 2-Euro core ("Pille") reference ring (~68% of outer diameter)
            drawCircle(
                color = innerGuideColor.copy(alpha = 0.45f),
                radius = circleRadius * 0.68f,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )

            // 4 cardinal alignment ticks around outer ring
            val tickLength = 12.dp.toPx()
            val strokePx = 3.dp.toPx()
            // Top tick
            drawLine(
                color = outerGuideColor,
                start = Offset(center.x, center.y - circleRadius - tickLength),
                end = Offset(center.x, center.y - circleRadius + tickLength * 0.4f),
                strokeWidth = strokePx
            )
            // Bottom tick
            drawLine(
                color = outerGuideColor,
                start = Offset(center.x, center.y + circleRadius - tickLength * 0.4f),
                end = Offset(center.x, center.y + circleRadius + tickLength),
                strokeWidth = strokePx
            )
            // Left tick
            drawLine(
                color = outerGuideColor,
                start = Offset(center.x - circleRadius - tickLength, center.y),
                end = Offset(center.x - circleRadius + tickLength * 0.4f, center.y),
                strokeWidth = strokePx
            )
            // Right tick
            drawLine(
                color = outerGuideColor,
                start = Offset(center.x + circleRadius - tickLength * 0.4f, center.y),
                end = Offset(center.x + circleRadius + tickLength, center.y),
                strokeWidth = strokePx
            )
        }

        // 3. Top Controls & Side Label + Bottom Shutter Button
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar: Close + Coin Side Badge + Torch toggle
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onCancel,
                        enabled = !isCapturing,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("camera_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.cd_camera_close)
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = stringResource(
                                if (side == CoinSide.OBVERSE) {
                                    R.string.camera_obverse_title
                                } else {
                                    R.string.camera_reverse_title
                                }
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }

                    if (hasFlashUnit) {
                        IconButton(
                            onClick = {
                                val nextTorch = !isTorchEnabled
                                isTorchEnabled = nextTorch
                                boundCamera?.cameraControl?.enableTorch(nextTorch)
                            },
                            enabled = !isCapturing,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (isTorchEnabled) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                                },
                                contentColor = if (isTorchEnabled) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("camera_torch_button")
                        ) {
                            Icon(
                                imageVector = if (isTorchEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                                contentDescription = stringResource(R.string.cd_camera_torch)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(48.dp))
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = stringResource(R.string.camera_circle_hint),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // Bottom Shutter Controls
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                if (isCapturing) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = stringResource(R.string.camera_capturing_status),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .border(4.dp, MaterialTheme.colorScheme.secondary, CircleShape)
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = captureAndCropCoin,
                        enabled = !isCapturing,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .testTag("camera_shutter_button")
                    ) {
                        if (isCapturing) {
                            CircularProgressIndicator(
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(28.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.PhotoCamera,
                                contentDescription = stringResource(R.string.cd_camera_shutter),
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun rotateBitmapIfNeeded(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
    if (rotationDegrees == 0) return bitmap
    val matrix = Matrix().apply {
        postRotate(rotationDegrees.toFloat())
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

/**
 * Maps the exact circle displayed in the FILL_CENTER PreviewView onto the upright camera Bitmap,
 * crops the square bounding box of that circle, and masks pixels outside the circle so only the
 * coin positioned inside the circle is saved and analyzed by Gemini.
 */
private fun cropBitmapToViewfinderCircle(
    uprightBitmap: Bitmap,
    viewWidth: Int,
    viewHeight: Int,
    backgroundFillArgb: Int
): Bitmap {
    val bmpW = uprightBitmap.width
    val bmpH = uprightBitmap.height

    val bmpCircleRadius: Float = if (viewWidth > 0 && viewHeight > 0) {
        val screenMinDim = min(viewWidth, viewHeight).toFloat()
        val screenCircleRadius = screenMinDim * CIRCLE_RADIUS_SCREEN_RATIO
        // FILL_CENTER scale factor from bitmap space to view space
        val fillCenterScale = max(
            viewWidth.toFloat() / bmpW.toFloat(),
            viewHeight.toFloat() / bmpH.toFloat()
        )
        // Slight 3% rim buffer so coin edge lettering/beading right on the line is preserved
        (screenCircleRadius / fillCenterScale) * 1.03f
    } else {
        min(bmpW, bmpH) * CIRCLE_RADIUS_SCREEN_RATIO
    }

    val maxAllowedRadius = min(bmpW, bmpH) / 2f
    val clampedRadius = bmpCircleRadius.coerceIn(32f, maxAllowedRadius)
    val centerX = bmpW / 2f
    val centerY = bmpH / 2f

    val left = (centerX - clampedRadius).toInt().coerceAtLeast(0)
    val top = (centerY - clampedRadius).toInt().coerceAtLeast(0)
    val diameter = (clampedRadius * 2f).toInt()
        .coerceAtMost(bmpW - left)
        .coerceAtMost(bmpH - top)
        .coerceAtLeast(1)

    val squareCrop = Bitmap.createBitmap(uprightBitmap, left, top, diameter, diameter)

    // Scale to <= 1024x1024 before applying circular isolation mask
    val targetSize = min(diameter, MAX_CROPPED_OUTPUT_PX)
    val scaledSquare = if (targetSize < diameter) {
        Bitmap.createScaledBitmap(squareCrop, targetSize, targetSize, true)
    } else {
        squareCrop
    }

    // Isolate the exact circular area so background distractions outside the circle are removed
    val output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(output)
    val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val half = targetSize / 2f

    // 1. Draw circular mask
    canvas.drawCircle(half, half, half, maskPaint)
    // 2. Keep coin bitmap inside circle
    maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(scaledSquare, 0f, 0f, maskPaint)

    // 3. Composite onto a clean neutral background for JPEG encoding (no alpha channel in JPEG)
    val finalJpegBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
    val finalCanvas = AndroidCanvas(finalJpegBitmap)
    finalCanvas.drawColor(backgroundFillArgb)
    finalCanvas.drawBitmap(output, 0f, 0f, null)

    return finalJpegBitmap
}

private fun saveCroppedCoinBitmap(
    context: Context,
    side: CoinSide,
    bitmap: Bitmap
): Uri {
    val dir = File(context.cacheDir, "coin_captures").apply { mkdirs() }
    val sideName = if (side == CoinSide.OBVERSE) "vorderseite" else "rueckseite"
    val file = File(dir, "coin_${sideName}_${System.currentTimeMillis()}.jpg")
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
        out.flush()
    }
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
}
