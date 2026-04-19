package com.bgg.combined.ui.scan

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.bgg.combined.AppViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ScanScreen(
    viewModel: AppViewModel,
    gameName: String,
    onScoresExtracted: () -> Unit,
    onDiscard: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val loading by viewModel.scanLoading.collectAsState()
    val error   by viewModel.scanError.collectAsState()
    val play    by viewModel.extractedPlay.collectAsState()

    // Navigate when extraction succeeds
    LaunchedEffect(play) {
        if (play != null) {
            viewModel.initEditablePlayers(play!!.players)
            onScoresExtracted()
        }
    }

    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    // Temp file for camera capture
    val photoFile = remember {
        File(context.cacheDir, "score_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg")
    }
    val photoUri = remember(photoFile) {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
    }

    // CameraX image capture use-case
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }

    val onEnterManually: () -> Unit = {
        viewModel.initEditablePlayers(emptyList())
        viewModel.setExtractedPlayManual()
        onScoresExtracted()
    }

    // Gallery picker
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val file = uriToFile(context, it)
            if (file != null) viewModel.extractScores(file)
        }
    }

    Scaffold(contentWindowInsets = WindowInsets(0)) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
            when {
                loading -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text("Extracting scores...")
                }

                error != null -> Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Error:",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    SelectionContainer {
                        Text(
                            text = error ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                    Button(onClick = { galleryLauncher.launch("image/*") }) {
                        Text("Try again from gallery")
                    }
                    OutlinedButton(onClick = onEnterManually) {
                        Text("Enter Manually")
                    }
                }

                cameraPermission.status.isGranted -> {
                // Shared capture action used by both the preview tap and the FAB
                    val capturePhoto = {
                        if (!loading) {
                            imageCapture?.let { capture ->
                                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                                capture.takePicture(
                                    outputOptions,
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                            viewModel.extractScores(photoFile)
                                        }
                                        override fun onError(exc: ImageCaptureException) {}
                                    }
                                )
                            }
                        }
                    }

                    // Camera preview; tap anywhere to capture
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).also { previewView ->
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }
                                    val capture = ImageCapture.Builder()
                                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                        .build()
                                    imageCapture = capture

                                    try {
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            capture
                                        )
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null  // no ripple on the full-screen preview
                            ) { capturePhoto() }
                    )

                    // Buttons column: camera on top, gallery + manual below
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        FloatingActionButton(
                            onClick = { capturePhoto() },
                            containerColor = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(Icons.Default.CameraAlt, "Capture")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            SmallFloatingActionButton(onClick = { galleryLauncher.launch("image/*") }) {
                                Icon(Icons.Default.Photo, "Gallery")
                            }
                            SmallFloatingActionButton(onClick = onEnterManually) {
                                Icon(Icons.Default.Edit, "Enter Manually")
                            }
                        }
                    }
                }

                else -> Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Camera permission is needed to scan scoresheets.")
                    Button(onClick = { cameraPermission.launchPermissionRequest() }) {
                        Text("Grant Permission")
                    }
                    OutlinedButton(onClick = { galleryLauncher.launch("image/*") }) {
                        Text("Pick from gallery instead")
                    }
                    OutlinedButton(onClick = onEnterManually) {
                        Text("Enter Manually")
                    }
                }
            }
            } // end Box
        } // end Column
    }
}

/** Copy a content:// URI to a temp cache file so we can read it as a File. */
private fun uriToFile(context: Context, uri: Uri): File? = try {
    val input = context.contentResolver.openInputStream(uri) ?: return null
    val file = File(context.cacheDir, "gallery_score_${System.currentTimeMillis()}.jpg")
    file.outputStream().use { input.copyTo(it) }
    file
} catch (e: Exception) {
    null
}
