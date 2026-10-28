package com.nutriscan.app.ui.scanner

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nutriscan.app.R
import com.nutriscan.app.scanner.BarcodeAnalyzer
import com.nutriscan.app.ui.components.ManualBarcodeDialog
import com.nutriscan.app.ui.components.barcodeRejectionMessage
import com.nutriscan.app.util.BarcodeValidator
import java.util.concurrent.Executors

private enum class CameraPermissionState { GRANTED, NOT_GRANTED, PERMANENTLY_DENIED }

@Composable
fun ScannerScreen(
    onBack: () -> Unit,
    onBarcodeDetected: (String) -> Unit
) {
    val context = LocalContext.current
    var permissionState by remember {
        mutableStateOf(
            if (hasCameraPermission(context)) {
                CameraPermissionState.GRANTED
            } else {
                CameraPermissionState.NOT_GRANTED
            }
        )
    }
    var requestAttempted by rememberSaveable { mutableStateOf(false) }
    var showManualEntry by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionState = when {
            granted -> CameraPermissionState.GRANTED
            canShowRationale(context) -> CameraPermissionState.NOT_GRANTED
            else -> CameraPermissionState.PERMANENTLY_DENIED
        }
    }

    // Ask once on entry: the user already tapped "Scan Product", so the request is expected here.
    LaunchedEffect(Unit) {
        if (permissionState == CameraPermissionState.NOT_GRANTED && !requestAttempted) {
            requestAttempted = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Picks up a permission granted from system settings while the app was in the background.
    LifecycleResumeEffect(Unit) {
        if (hasCameraPermission(context)) {
            permissionState = CameraPermissionState.GRANTED
        }
        onPauseOrDispose { }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (permissionState == CameraPermissionState.GRANTED) {
            CameraScannerContent(
                onBarcodeDetected = onBarcodeDetected,
                onManualEntry = { showManualEntry = true },
                onBack = onBack
            )
        } else {
            PermissionContent(
                permanentlyDenied = permissionState == CameraPermissionState.PERMANENTLY_DENIED,
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onOpenSettings = { context.openAppSettings() },
                onBack = onBack
            )
        }

        if (showManualEntry) {
            ManualBarcodeDialog(
                onDismiss = { showManualEntry = false },
                onConfirm = { barcode ->
                    showManualEntry = false
                    onBarcodeDetected(barcode)
                }
            )
        }
    }
}

@Composable
private fun CameraScannerContent(
    onBarcodeDetected: (String) -> Unit,
    onManualEntry: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val onDetectedState = rememberUpdatedState(onBarcodeDetected)

    var torchOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var cameraFailed by remember { mutableStateOf(false) }
    var rejection by remember { mutableStateOf<BarcodeValidator.Rejection?>(null) }

    // A detected-but-invalid code gets a short explanation, then the hint returns.
    LaunchedEffect(rejection) {
        if (rejection != null) {
            kotlinx.coroutines.delay(3_000)
            rejection = null
        }
    }

    DisposableEffect(lifecycleOwner, previewView) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val analyzer = BarcodeAnalyzer(
            onBarcodeDetected = { barcode -> onDetectedState.value(barcode) },
            onInvalidBarcode = { problem -> rejection = problem }
        )
        var boundProvider: ProcessCameraProvider? = null

        providerFuture.addListener(
            {
                try {
                    val provider = providerFuture.get()
                    boundProvider = provider

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { it.setAnalyzer(analysisExecutor, analyzer) }

                    provider.unbindAll()
                    camera = provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                    cameraFailed = false
                } catch (exception: Exception) {
                    // Permission revoked, camera in use by another app, or no camera at all.
                    cameraFailed = true
                }
            },
            ContextCompat.getMainExecutor(context)
        )

        onDispose {
            analyzer.pause()
            boundProvider?.unbindAll()
            camera = null
            analyzer.close()
            analysisExecutor.shutdown()
        }
    }

    LaunchedEffect(torchOn, camera) {
        try {
            camera?.cameraControl?.enableTorch(torchOn)
        } catch (exception: Exception) {
            torchOn = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        ScanFrameOverlay(modifier = Modifier.fillMaxSize())

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = Color.White
                )
            }
            Text(
                text = stringResource(R.string.scanner_title),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { torchOn = !torchOn }) {
                Icon(
                    painter = painterResource(
                        if (torchOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off
                    ),
                    contentDescription = stringResource(
                        if (torchOn) R.string.scanner_torch_on else R.string.scanner_torch_off
                    ),
                    tint = Color.White
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (cameraFailed) {
                Text(
                    text = stringResource(R.string.camera_start_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFFFB4AB),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
            }
            val currentRejection = rejection
            if (currentRejection != null) {
                Text(
                    text = barcodeRejectionMessage(currentRejection),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFFFB4AB),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = stringResource(R.string.scanner_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            TextButton(onClick = onManualEntry) {
                Text(
                    text = stringResource(R.string.scanner_manual_fallback),
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun ScanFrameOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val frameWidth = size.width * 0.78f
        val frameHeight = frameWidth * 0.62f
        val left = (size.width - frameWidth) / 2f
        val top = (size.height - frameHeight) / 2f
        val scrim = Color.Black.copy(alpha = 0.45f)

        drawRect(scrim, topLeft = Offset.Zero, size = Size(size.width, top))
        drawRect(
            scrim,
            topLeft = Offset(0f, top + frameHeight),
            size = Size(size.width, size.height - top - frameHeight)
        )
        drawRect(scrim, topLeft = Offset(0f, top), size = Size(left, frameHeight))
        drawRect(
            scrim,
            topLeft = Offset(left + frameWidth, top),
            size = Size(size.width - left - frameWidth, frameHeight)
        )

        drawRoundRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = Size(frameWidth, frameHeight),
            cornerRadius = CornerRadius(24f, 24f),
            style = Stroke(width = 3.dp.toPx())
        )
    }
}

@Composable
private fun PermissionContent(
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_scan_barcode),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.camera_permission_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(
                if (permanentlyDenied) {
                    R.string.camera_permission_denied_permanently
                } else {
                    R.string.camera_permission_body
                }
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        if (permanentlyDenied) {
            Button(onClick = onOpenSettings) {
                Text(stringResource(R.string.action_open_settings))
            }
        } else {
            Button(onClick = onRequest) {
                Text(stringResource(R.string.camera_permission_grant))
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.action_back))
        }
    }
}

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

private fun canShowRationale(context: Context): Boolean {
    val activity = context.findActivity() ?: return false
    return ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}
