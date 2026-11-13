package com.kugo.qr

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var scanner: BarcodeScanner
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var resultText: TextView
    private lateinit var statusText: TextView
    private lateinit var torchButton: MaterialButton
    private var camera: Camera? = null
    private var torchEnabled = false
    private var lastResult = ""
    @Volatile private var isProcessing = false

    private val galleryPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { scanImage(it) } }

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera()
        else statusText.text = "Permita o acesso à câmera para escanear."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.rgb(8, 14, 24)
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = false
        }
        setContentView(R.layout.activity_main)

        val rootView = findViewById<View>(R.id.rootContainer)
        val headerBar = findViewById<View>(R.id.headerBar)
        val density = resources.displayMetrics.density
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, 0, 0, systemBars.bottom)
            headerBar.setPadding(
                (20 * density).toInt(), systemBars.top + (10 * density).toInt(),
                (18 * density).toInt(), (10 * density).toInt()
            )
            insets
        }
        ViewCompat.requestApplyInsets(rootView)

        scanner = BarcodeScanning.getClient()
        cameraExecutor = Executors.newSingleThreadExecutor()
        resultText = findViewById(R.id.resultText)
        statusText = findViewById(R.id.statusText)
        torchButton = findViewById(R.id.torchButton)

        findViewById<MaterialButton>(R.id.galleryButton).setOnClickListener {
            galleryPicker.launch("image/*")
        }
        findViewById<MaterialButton>(R.id.copyButton).setOnClickListener {
            if (lastResult.isBlank()) toast("Nenhum resultado para copiar.")
            else {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Kugo QR", lastResult))
                toast("Conteúdo copiado!")
            }
        }
        findViewById<MaterialButton>(R.id.openButton).setOnClickListener { openResult() }
        torchButton.setOnClickListener { toggleTorch() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) startCamera() else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = findViewById<androidx.camera.view.PreviewView>(
                    R.id.previewView
                ).surfaceProvider
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(cameraExecutor) { proxy ->
                if (isProcessing) { proxy.close(); return@setAnalyzer }
                val mediaImage = proxy.image
                if (mediaImage == null) { proxy.close(); return@setAnalyzer }
                isProcessing = true
                val image = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        val value = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                        if (!value.isNullOrBlank()) runOnUiThread { showResult(value) }
                    }
                    .addOnFailureListener { runOnUiThread { statusText.text = "Falha na leitura. Tente novamente." } }
                    .addOnCompleteListener { isProcessing = false; proxy.close() }
            }

            try {
                provider.unbindAll()
                camera = provider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                )
                torchButton.isEnabled = camera?.cameraInfo?.hasFlashUnit() == true
                statusText.text = "Procurando QR Code em toda a tela"
            } catch (e: Exception) {
                statusText.text = "Não foi possível iniciar a câmera."
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun toggleTorch() {
        val activeCamera = camera ?: return
        if (!activeCamera.cameraInfo.hasFlashUnit()) {
            toast("Este aparelho não possui flash disponível.")
            return
        }
        torchEnabled = !torchEnabled
        activeCamera.cameraControl.enableTorch(torchEnabled)
        torchButton.text = if (torchEnabled) "●" else "ϟ"
    }

    private fun scanImage(uri: Uri) {
        statusText.text = "Analisando imagem..."
        try {
            val image = InputImage.fromFilePath(this, uri)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    val value = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                    if (!value.isNullOrBlank()) showResult(value)
                    else statusText.text = "Nenhum código encontrado nessa imagem."
                }
                .addOnFailureListener { statusText.text = "Erro ao analisar a imagem." }
        } catch (e: Exception) {
            statusText.text = "Não foi possível abrir essa imagem."
        }
    }

    private fun showResult(value: String) {
        if (value == lastResult) return
        lastResult = value
        resultText.text = value
        resultText.visibility = View.VISIBLE
        statusText.text = "QR Code identificado"
        toast("QR Code lido!")
    }

    private fun openResult() {
        if (lastResult.isBlank()) { toast("Leia um QR Code primeiro."); return }
        val uri = runCatching { Uri.parse(lastResult) }.getOrNull()
        if (uri == null || uri.scheme !in listOf("http", "https")) {
            toast("O conteúdo não é um link HTTP/HTTPS.")
            return
        }
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        scanner.close()
    }
}
