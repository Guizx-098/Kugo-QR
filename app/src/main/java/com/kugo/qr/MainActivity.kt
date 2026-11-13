package com.kugo.qr

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONArray
import org.json.JSONObject
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

    private data class SavedQr(val name: String, val url: String)

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
        if (Build.VERSION.SDK_INT >= 29) window.isStatusBarContrastEnforced = false
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
        findViewById<MaterialButton>(R.id.createQrButton).setOnClickListener { showCreateQrDialog() }
        findViewById<MaterialButton>(R.id.savedQrsButton).setOnClickListener { showSavedQrs() }
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
                    .addOnFailureListener {
                        runOnUiThread { statusText.text = "Falha na leitura. Tente novamente." }
                    }
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
        // Sem toast aqui: o resultado permanece visível no painel para não cobrir o link.
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

    private fun normalizeUrl(raw: String): String {
        val value = raw.trim()
        return if (value.startsWith("http://", true) || value.startsWith("https://", true)) {
            value
        } else "https://$value"
    }

    private fun makeQrBitmap(value: String, size: Int = 900): Bitmap {
        val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        return bitmap
    }

    private fun showCreateQrDialog(existing: SavedQr? = null) {
        val density = resources.displayMetrics.density
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((22 * density).toInt(), (8 * density).toInt(), (22 * density).toInt(), 0)
        }
        val nameInput = EditText(this).apply {
            hint = "Nome do QR Code"
            setSingleLine(true)
            setText(existing?.name.orEmpty())
        }
        val urlInput = EditText(this).apply {
            hint = "https://exemplo.com"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_URI
            setText(existing?.url.orEmpty())
        }
        container.addView(nameInput)
        container.addView(urlInput)
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Criar QR Code" else "Editar QR Code")
            .setMessage("Digite um nome e o link que deseja transformar em QR Code.")
            .setView(container)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Gerar", null)
            .create()
        dialog.setOnShowListener {
            styleDialog(dialog)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text.toString().trim()
                val rawUrl = urlInput.text.toString().trim()
                if (name.isBlank()) {
                    nameInput.error = "Dê um nome ao QR Code"
                    return@setOnClickListener
                }
                if (rawUrl.isBlank()) {
                    urlInput.error = "Digite um link"
                    return@setOnClickListener
                }
                val url = normalizeUrl(rawUrl)
                val parsed = Uri.parse(url)
                if (parsed.host.isNullOrBlank() || parsed.scheme !in listOf("http", "https")) {
                    urlInput.error = "Digite um link válido"
                    return@setOnClickListener
                }
                dialog.dismiss()
                showQrPreview(SavedQr(name, url), saveAfterPreview = true, original = existing)
            }
        }
        dialog.show()
    }

    private fun showQrPreview(item: SavedQr, saveAfterPreview: Boolean, original: SavedQr? = null) {
        val bitmap = try { makeQrBitmap(item.url) } catch (e: Exception) {
            toast("Não foi possível gerar esse QR Code.")
            return
        }
        val density = resources.displayMetrics.density
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((22 * density).toInt(), (8 * density).toInt(), (22 * density).toInt(), 0)
        }
        val image = ImageView(this).apply {
            setImageBitmap(bitmap)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(
                (240 * density).toInt(), (240 * density).toInt()
            )
        }
        val name = TextView(this).apply {
            text = item.name
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, (10 * density).toInt(), 0, (4 * density).toInt())
        }
        val link = TextView(this).apply {
            text = item.url
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(0xFF65758B.toInt())
        }
        content.addView(image)
        content.addView(name)
        content.addView(link)
        val builder = AlertDialog.Builder(this)
            .setTitle("Seu QR Code")
            .setView(content)
            .setNegativeButton("Fechar", null)
            .setNeutralButton("Baixar imagem", null)
            .setPositiveButton(if (saveAfterPreview && original == null) "Salvar QR Code" else if (original != null) "Salvar alterações" else "Fechar", null)
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                saveQrImage(bitmap, item.name)
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (saveAfterPreview || original != null) {
                    saveQr(item, original)
                    toast(if (original != null) "QR Code atualizado." else "QR Code salvo em Meus QR Codes.")
                }
                dialog.dismiss()
            }
            styleDialog(dialog)
        }
        dialog.show()
    }

    private fun readSavedQrs(): MutableList<SavedQr> {
        val raw = getSharedPreferences("saved_qrs", MODE_PRIVATE).getString("items", "[]") ?: "[]"
        val result = mutableListOf<SavedQr>()
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(SavedQr(obj.optString("name"), obj.optString("url")))
            }
        }
        return result
    }

    private fun saveQr(item: SavedQr, original: SavedQr? = null) {
        val items = readSavedQrs()
        if (original != null) items.removeAll { it.name == original.name && it.url == original.url }
        items.removeAll { it.name.equals(item.name, true) }
        items.add(0, item)
        writeSavedQrs(items)
    }

    private fun showSavedQrs() {
        val items = readSavedQrs()
        if (items.isEmpty()) {
            val empty = AlertDialog.Builder(this)
                .setTitle("Meus QR Codes")
                .setMessage("Sua coleção está vazia. Crie um QR Code para ele aparecer aqui.")
                .setPositiveButton("Criar QR Code") { _, _ -> showCreateQrDialog() }
                .setNegativeButton("Fechar", null)
                .create()
            empty.setOnShowListener { styleDialog(empty) }
            empty.show()
            return
        }

        val labels = items.map { item ->
            val subtitle = item.url.removePrefix("https://").removePrefix("http://")
            "${item.name}\n$subtitle"
        }.toTypedArray()
        val dialog = AlertDialog.Builder(this)
            .setTitle("Meus QR Codes · ${items.size}")
            .setItems(labels) { _, which -> showSavedQrActions(items[which]) }
            .setNegativeButton("Fechar", null)
            .create()
        dialog.setOnShowListener { styleDialog(dialog) }
        dialog.show()
    }

    private fun showSavedQrActions(item: SavedQr) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(item.name)
            .setMessage(item.url)
            .setItems(arrayOf(
                "Visualizar QR Code",
                "Compartilhar link",
                "Baixar imagem",
                "Editar QR Code",
                "Excluir QR Code"
            )) { _, which ->
                when (which) {
                    0 -> showQrPreview(item, false)
                    1 -> {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, item.url)
                        }
                        startActivity(Intent.createChooser(send, "Compartilhar QR Code"))
                    }
                    2 -> saveQrImage(makeQrBitmap(item.url), item.name)
                    3 -> showCreateQrDialog(item)
                    4 -> {
                        val confirm = AlertDialog.Builder(this)
                            .setTitle("Excluir QR Code?")
                            .setMessage("“${item.name}” será removido da sua coleção.")
                            .setNegativeButton("Cancelar", null)
                            .setPositiveButton("Excluir") { _, _ ->
                                val items = readSavedQrs()
                                items.removeAll { it.name == item.name && it.url == item.url }
                                writeSavedQrs(items)
                                toast("QR Code excluído.")
                                showSavedQrs()
                            }
                            .create()
                        confirm.setOnShowListener { styleDialog(confirm, destructive = true) }
                        confirm.show()
                    }
                }
            }
            .setNegativeButton("Voltar") { _, _ -> showSavedQrs() }
            .create()
        dialog.setOnShowListener { styleDialog(dialog) }
        dialog.show()
    }

    private fun writeSavedQrs(items: List<SavedQr>) {
        val array = JSONArray()
        items.forEach { array.put(JSONObject().put("name", it.name).put("url", it.url)) }
        getSharedPreferences("saved_qrs", MODE_PRIVATE).edit()
            .putString("items", array.toString()).apply()
    }

    private fun styleDialog(dialog: AlertDialog, destructive: Boolean = false) {
        val green = android.graphics.Color.rgb(32, 217, 144)
        val red = android.graphics.Color.rgb(255, 107, 107)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(if (destructive) red else green)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(0xFF9EACC0.toInt())
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(green)
    }

    private fun saveQrImage(bitmap: Bitmap, name: String) {
        val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "Kugo_QR" }
        val filename = "KugoQR_$safeName.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Kugo QR")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            toast("Não foi possível salvar a imagem.")
            return
        }
        try {
            resolver.openOutputStream(uri)?.use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw IllegalStateException("Falha ao gravar PNG")
                }
            } ?: throw IllegalStateException("Sem acesso ao arquivo")
            if (Build.VERSION.SDK_INT >= 29) {
                val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
            }
            toast("Imagem salva em Imagens/Kugo QR.")
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            toast("Erro ao salvar a imagem.")
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy()
        if (::cameraExecutor.isInitialized) cameraExecutor.shutdown()
        if (::scanner.isInitialized) scanner.close()
    }
}
