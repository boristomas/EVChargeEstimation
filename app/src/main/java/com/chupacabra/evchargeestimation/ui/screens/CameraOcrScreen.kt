package com.chupacabra.evchargeestimation.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chupacabra.evchargeestimation.domain.ChargeEstimator
import com.chupacabra.evchargeestimation.domain.DashboardOcrParser
import com.chupacabra.evchargeestimation.ocr.OcrConsensus
import com.chupacabra.evchargeestimation.ocr.TextRecognizerHelper
import com.chupacabra.evchargeestimation.ui.theme.NeonCyan
import com.chupacabra.evchargeestimation.ui.theme.NeonMint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "CameraScan"
private const val MIN_FRAME_INTERVAL_MS = 350L

@Composable
fun CameraOcrScreen(
    onResult: (DashboardOcrParser.ParsedDashboard) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val scope = rememberCoroutineScope()

    // Keep the display awake the whole time the scan screen is open.
    DisposableEffect(activity, view) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var useFrontCamera by remember { mutableStateOf(false) }
    var livePercent by remember { mutableStateOf<Int?>(null) }
    var liveMinutes by remember { mutableStateOf<Int?>(null) }
    var framesWithSignal by remember { mutableIntStateOf(0) }
    var framesProcessed by remember { mutableIntStateOf(0) }
    var statusMessage by remember {
        mutableStateOf("Turn your phone sideways to scan")
    }

    val consensus = remember { OcrConsensus() }
    val recognizer = remember { TextRecognizerHelper() }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val analyzing = remember { AtomicBoolean(false) }
    val lastFrameAt = remember { AtomicLong(0L) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) statusMessage = "Camera access is needed to scan"
    }

    DisposableEffect(activity) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            cameraExecutor.shutdown()
            recognizer.close()
            try {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(isLandscape) {
        statusMessage = if (isLandscape) {
            "Point at the charge screen"
        } else {
            "Turn your phone sideways to scan"
        }
        if (!isLandscape) {
            consensus.reset()
            livePercent = null
            liveMinutes = null
            framesWithSignal = 0
            framesProcessed = 0
        }
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    LaunchedEffect(hasPermission, useFrontCamera, lifecycleOwner, isLandscape) {
        if (!hasPermission || !isLandscape) {
            try {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            } catch (_: Exception) {
            }
            return@LaunchedEffect
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val previewUseCase = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()

                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    val now = System.currentTimeMillis()
                    val due = now - lastFrameAt.get() >= MIN_FRAME_INTERVAL_MS
                    if (!due || !analyzing.compareAndSet(false, true)) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    lastFrameAt.set(now)

                    scope.launch {
                        try {
                            val text = withContext(Dispatchers.Default) {
                                recognizer.recognize(imageProxy)
                            }
                            val parsed = DashboardOcrParser.parse(text)
                            withContext(Dispatchers.Main.immediate) {
                                consensus.add(parsed)
                                livePercent = consensus.consensusPercent
                                liveMinutes = consensus.consensusMinutes
                                framesWithSignal = consensus.framesWithSignal
                                framesProcessed = consensus.framesProcessed
                                if (livePercent != null || liveMinutes != null) {
                                    statusMessage = "Looking good — tap Use when ready"
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Scan frame failed", e)
                        } finally {
                            try {
                                imageProxy.close()
                            } catch (_: Exception) {
                            }
                            analyzing.set(false)
                        }
                    }
                }

                val selector = if (useFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    previewUseCase,
                    analysis
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind failed", e)
                statusMessage = "Could not open camera"
            }
        }, ContextCompat.getMainExecutor(context))
    }

    val canConfirm = livePercent != null || liveMinutes != null

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            !hasPermission -> {
                PermissionPanel(
                    onGrant = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onCancel = onCancel,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                )
            }
            !isLandscape -> {
                RotateToLandscapePanel(
                    onCancel = onCancel,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                )
            }
            else -> {
                // Full-bleed preview (no top bar / nav chrome)
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )

                // Slim control panel on the right only
                ScanSidePanel(
                    statusMessage = statusMessage,
                    livePercent = livePercent,
                    liveMinutes = liveMinutes,
                    framesWithSignal = framesWithSignal,
                    framesProcessed = framesProcessed,
                    canConfirm = canConfirm,
                    onBack = onCancel,
                    onSwitchCamera = { useFrontCamera = !useFrontCamera },
                    onConfirm = {
                        val snapshot = consensus.toParsed()
                        if (snapshot.currentPercent != null || snapshot.minutesToFull != null) {
                            onResult(snapshot)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(168.dp)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                )
            }
        }
    }
}

@Composable
private fun ScanSidePanel(
    statusMessage: String,
    livePercent: Int?,
    liveMinutes: Int?,
    framesWithSignal: Int,
    framesProcessed: Int,
    canConfirm: Boolean,
    onBack: () -> Unit,
    onSwitchCamera: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.78f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onSwitchCamera, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Cameraswitch,
                    contentDescription = "Switch camera",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Text(
            text = "Scan",
            color = NeonCyan,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
        )

        Text(
            text = statusMessage,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp,
            modifier = Modifier.fillMaxWidth()
        )

        ScanValueRow(
            label = "Battery",
            value = livePercent?.let { "$it%" } ?: "—",
            active = livePercent != null
        )
        ScanValueRow(
            label = "To full",
            value = liveMinutes?.let { ChargeEstimator.formatDuration(it) } ?: "—",
            active = liveMinutes != null
        )

        Text(
            text = scanStatsLabel(framesWithSignal, framesProcessed),
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = onConfirm,
            enabled = canConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonCyan,
                contentColor = Color.Black,
                disabledContainerColor = Color.White.copy(alpha = 0.12f),
                disabledContentColor = Color.White.copy(alpha = 0.4f)
            ),
            contentPadding = ButtonDefaults.ContentPadding
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (canConfirm) "Use" else "…",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        TextButton(onClick = onBack) {
            Text("Cancel", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun ScanValueRow(
    label: String,
    value: String,
    active: Boolean
) {
    val border by animateColorAsState(
        targetValue = if (active) NeonMint else Color.White.copy(alpha = 0.2f),
        label = "scanValueBorder"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 10.sp,
            letterSpacing = 0.4.sp
        )
        Text(
            text = value,
            color = if (active) NeonMint else Color.White.copy(alpha = 0.4f),
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
    }
}

private fun scanStatsLabel(hits: Int, total: Int): String = when {
    total == 0 -> "Watching…"
    hits == 0 -> "Still looking…"
    hits == 1 -> "Found once"
    else -> "Found $hits times"
}

@Composable
private fun RotateToLandscapePanel(
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.ScreenRotation,
            contentDescription = null,
            tint = NeonCyan,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = "Turn your phone sideways",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Scanning works best when the car screen fills the view. Rotate to landscape, then point at the charge display.",
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel) {
            Text("Go back", color = NeonCyan)
        }
    }
}

@Composable
private fun PermissionPanel(
    onGrant: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Allow camera access to scan the car screen.",
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onGrant) {
            Text("Allow camera", color = NeonCyan)
        }
        TextButton(onClick = onCancel) {
            Text("Enter numbers instead", color = Color.White.copy(alpha = 0.8f))
        }
    }
}
