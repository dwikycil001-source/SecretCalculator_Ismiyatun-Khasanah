package id.ac.secretcalculator

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    companion object {
        private const val GPS_PERMISSION_REQUEST = 410
        private const val STUDENT_NAME = "Ismiyatun Khasanah"
        private const val STUDENT_NIM = "STI202404018"
        // Lima digit terakhir NIM STI202404018 = 04018.
        // Urutan: digit 1 kamera, digit 2 audio, digit 3 gambar, digit 4 GPS, digit 5 sensor.
        private val NIM_DIGITS = intArrayOf(0, 4, 0, 1, 8)
    }

    private val colorBackground = Color.parseColor("#F3F8FF")
    private val colorSurface = Color.parseColor("#FFFFFF")
    private val colorSurfaceSoft = Color.parseColor("#EAF3FF")
    private val colorPrimary = Color.parseColor("#2878C8")
    private val colorPrimarySoft = Color.parseColor("#B9D8FA")
    private val colorPrimaryLight = Color.parseColor("#D9EAFE")
    private val colorPrimaryExtraLight = Color.parseColor("#EDF5FF")
    private val colorTextDark = Color.parseColor("#173B65")
    private val colorTextMedium = Color.parseColor("#4D719B")
    private val colorTextLight = Color.parseColor("#7193B8")
    private val colorBorder = Color.parseColor("#BBD5F2")

    private lateinit var resultText: TextView
    private lateinit var expressionText: TextView
    private var displayValue: String = "0"
    private var storedValue: Double? = null
    private var pendingOperator: String? = null
    private var startNewNumber: Boolean = false

    private lateinit var cameraLauncher: ActivityResultLauncher<Intent>
    private var mediaPlayer: MediaPlayer? = null

    private lateinit var locationManager: LocationManager
    private var lastGpsLocation: Location? = null
    private var gpsDialogStatus: TextView? = null
    private var gpsListener: LocationListener? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var gpsTimeout: Runnable? = null

    private lateinit var sensorManager: SensorManager
    private var activeSensorListener: SensorEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = colorBackground
        window.navigationBarColor = colorBackground
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val thumbnail = result.data?.extras?.get("data") as? Bitmap
                if (thumbnail != null) showCapturedPhoto(thumbnail)
                else toast("Kamera selesai digunakan.")
            }
        }

        buildInterface()
    }

    private fun buildInterface() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(colorBackground)
            isFillViewport = true
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(20))
        }
        scroll.addView(
            content,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val logo = TextView(this).apply {
            text = "♡"
            textSize = 31f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = rounded(colorPrimary, 18)
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(58), dp(58)))
        val titleColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), 0, 0, 0)
        }
        titleColumn.addView(label("SECRET CALCULATOR", 20f, colorTextDark, true))
        titleColumn.addView(label("Blue & white edition", 12f, colorTextMedium, false))
        header.addView(titleColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(header, marginParams(bottom = 16))

        val identity = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(12), dp(15), dp(12))
            background = rounded(colorSurface, 18, colorBorder, dp(1))
        }
        identity.addView(label("UTS MOBILE PROGRAMMING", 10f, colorPrimary, true))
        identity.addView(label(STUDENT_NAME, 15f, colorTextDark, true).apply { setPadding(0, dp(4), 0, 0) })
        identity.addView(label("NIM  $STUDENT_NIM", 12f, colorTextMedium, false).apply { setPadding(0, dp(2), 0, 0) })
        content.addView(identity, marginParams(bottom = 16))

        val displayCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(16))
            background = rounded(colorSurface, 22, colorBorder, dp(1))
        }
        displayCard.addView(label("HASIL PERHITUNGAN", 10f, colorTextLight, true))
        expressionText = label("", 15f, colorTextMedium, false).apply {
            gravity = Gravity.END
            maxLines = 1
            setPadding(0, dp(8), 0, dp(3))
        }
        displayCard.addView(expressionText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        resultText = label(displayValue, 42f, colorTextDark, true).apply {
            gravity = Gravity.END
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(22, 42, 1, TypedValue.COMPLEX_UNIT_SP)
        }
        displayCard.addView(resultText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
        content.addView(displayCard, marginParams(bottom = 15))

        val keypad = GridLayout(this).apply {
            columnCount = 4
            rowCount = 5
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
        }
        val keys = listOf(
            "AC", "±", "%", "÷",
            "7", "8", "9", "×",
            "4", "5", "6", "−",
            "1", "2", "3", "+",
            "0", ".", "⌫", "="
        )
        keys.forEach { key ->
            val button = createKey(key)
            button.setOnClickListener { handleKey(key) }
            val params = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply {
                width = 0
                height = dp(62)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            keypad.addView(button, params)
        }
        content.addView(keypad, marginParams(bottom = 12))

        val footer = label("Kode rahasia ditentukan dari 5 digit terakhir NIM", 11f, colorTextMedium, false).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(4))
        }
        content.addView(footer)
        setContentView(scroll)
    }

    private fun createKey(key: String): Button {
        val backgroundColor = when {
            key == "=" -> colorPrimary
            key in listOf("÷", "×", "−", "+") -> colorPrimarySoft
            key in listOf("AC", "±", "%", "⌫") -> colorPrimaryLight
            else -> colorSurface
        }
        val textColor = when {
            key == "=" -> Color.WHITE
            else -> colorTextDark
        }
        return Button(this).apply {
            text = key
            textSize = if (key == "AC") 17f else 21f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
            background = rounded(backgroundColor, 17, colorBorder, dp(1))
            stateListAnimator = null
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
        }
    }

    private fun handleKey(key: String) {
        when (key) {
            "AC" -> clearCalculator()
            "±" -> toggleSign()
            "%" -> applyPercent()
            "⌫" -> backspace()
            "." -> appendDecimal()
            "=" -> calculateResult()
            "÷", "×", "−", "+" -> chooseOperator(key)
            else -> if (key.length == 1 && key[0].isDigit()) appendDigit(key)
        }
    }

    private fun clearCalculator() {
        displayValue = "0"
        storedValue = null
        pendingOperator = null
        startNewNumber = false
        expressionText.text = ""
        refreshDisplay()
    }

    private fun appendDigit(digit: String) {
        if (startNewNumber || displayValue == "Error") {
            displayValue = digit
            startNewNumber = false
        } else if (displayValue == "0") {
            displayValue = digit
        } else if (displayValue.length < 15) {
            displayValue += digit
        }
        refreshDisplay()
    }

    private fun appendDecimal() {
        if (startNewNumber || displayValue == "Error") {
            displayValue = "0."
            startNewNumber = false
        } else if (!displayValue.contains('.')) {
            displayValue += "."
        }
        refreshDisplay()
    }

    private fun toggleSign() {
        val number = displayValue.toDoubleOrNull() ?: return
        if (number == 0.0) return
        displayValue = if (displayValue.startsWith("-")) displayValue.drop(1) else "-$displayValue"
        startNewNumber = false
        refreshDisplay()
    }

    private fun applyPercent() {
        val number = displayValue.toDoubleOrNull() ?: return
        displayValue = formatNumber(number / 100.0)
        startNewNumber = true
        refreshDisplay()
    }

    private fun backspace() {
        if (startNewNumber || displayValue == "Error") {
            displayValue = "0"
            startNewNumber = false
        } else {
            displayValue = if (displayValue.length <= 1 || (displayValue.length == 2 && displayValue.startsWith("-"))) {
                "0"
            } else {
                displayValue.dropLast(1)
            }
        }
        refreshDisplay()
    }

    private fun chooseOperator(operator: String) {
        val current = displayValue.toDoubleOrNull() ?: 0.0
        if (storedValue != null && pendingOperator != null && !startNewNumber) {
            val interim = calculate(storedValue!!, current, pendingOperator!!)
            if (interim == null) {
                toast("Tidak bisa membagi dengan nol.")
                clearCalculator()
                return
            }
            displayValue = formatNumber(interim)
            storedValue = interim
        } else {
            storedValue = current
        }
        pendingOperator = operator
        expressionText.text = "${formatNumber(storedValue ?: current)} $operator"
        startNewNumber = true
    }

    private fun calculateResult() {
        val left = storedValue ?: return
        val operator = pendingOperator ?: return
        val right = displayValue.toDoubleOrNull() ?: return
        val result = calculate(left, right, operator)
        if (result == null || result.isNaN() || result.isInfinite()) {
            expressionText.text = "${formatNumber(left)} $operator ${formatNumber(right)}"
            displayValue = "Error"
            storedValue = null
            pendingOperator = null
            startNewNumber = true
            refreshDisplay()
            toast("Operasi tidak dapat dilakukan.")
            return
        }

        expressionText.text = "${formatNumber(left)} $operator ${formatNumber(right)} ="
        displayValue = formatNumber(result)
        storedValue = null
        pendingOperator = null
        startNewNumber = true
        refreshDisplay()
        checkSecretCode(result)
    }

    private fun calculate(left: Double, right: Double, operator: String): Double? {
        return when (operator) {
            "+" -> left + right
            "−" -> left - right
            "×" -> left * right
            "÷" -> if (right == 0.0) null else left / right
            else -> null
        }
    }

    private fun checkSecretCode(result: Double) {
        // Fitur rahasia hanya dipicu oleh hasil bilangan bulat yang sama dengan digit NIM.
        val rounded = result.toInt()
        if (abs(result - rounded.toDouble()) > 0.000001) return

        // NIM STI202404018 berakhir dengan 04018.
        // Angka 0 muncul pada digit ke-1 (Kamera) dan digit ke-3 (Open Image).
        // Jika hasil kalkulasi 0, tampilkan TEPAT dua pilihan ini saja.
        if (rounded == NIM_DIGITS[0] && rounded == NIM_DIGITS[2]) {
            AlertDialog.Builder(this)
                .setTitle("Pilih Fitur Rahasia")
                .setMessage("Hasil perhitungan 0 cocok dengan dua digit NIM kamu. Pilih salah satu fitur:")
                .setItems(arrayOf("Kamera (digit ke-1)", "Open Image (digit ke-3)")) { _, which ->
                    when (which) {
                        0 -> openCameraFeature()
                        1 -> showSecretImage()
                    }
                }
                .show()
            return
        }

        // Digit NIM yang lain masing-masing hanya memicu satu fitur.
        val action = when (rounded) {
            NIM_DIGITS[1] -> SecretAction("Play Audio (digit ke-2)", ::playSecretAudio)
            NIM_DIGITS[3] -> SecretAction("Open GPS (digit ke-4)", ::openGpsFeature)
            NIM_DIGITS[4] -> SecretAction("Accelerometer & Gyroscope (digit ke-5)", ::showSensors)
            else -> null
        }
        action?.action?.invoke()
    }

    private data class SecretAction(val label: String, val action: () -> Unit)

    private fun openCameraFeature() {
        try {
            cameraLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
        } catch (_: ActivityNotFoundException) {
            toast("Aplikasi kamera tidak ditemukan di perangkat ini.")
        }
    }

    private fun showCapturedPhoto(bitmap: Bitmap) {
        val image = ImageView(this).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            setBackgroundColor(colorPrimaryExtraLight)
        }
        AlertDialog.Builder(this)
            .setTitle("Hasil Kamera")
            .setView(image)
            .setPositiveButton("Tutup", null)
            .show()
    }

    private fun playSecretAudio() {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(this, R.raw.secret_audio)
            val player = mediaPlayer
            if (player == null) {
                toast("File audio tidak dapat diputar.")
                return
            }
            player.setOnCompletionListener { completed ->
                completed.release()
                if (mediaPlayer === completed) mediaPlayer = null
            }
            player.start()
            toast("Memutar audio rahasia.")
        } catch (_: Exception) {
            toast("Audio gagal diputar. Coba jalankan kembali aplikasi.")
        }
    }

    private fun showSecretImage() {
        val image = ImageView(this).apply {
            setImageResource(R.drawable.secret_image)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(360))
        }
        val holder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(4))
            addView(image)
        }
        AlertDialog.Builder(this)
            .setTitle("Gambar Rahasia")
            .setView(holder)
            .setPositiveButton("Tutup", null)
            .show()
    }

    private fun openGpsFeature() {
        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                GPS_PERMISSION_REQUEST
            )
            return
        }
        showGpsDialog()
    }

    private fun showGpsDialog() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        val status = label("Tekan 'Cari lokasi sekarang' untuk membaca koordinat perangkat.", 14f, colorTextDark, false).apply {
            setPadding(0, 0, 0, dp(14))
        }
        gpsDialogStatus = status
        panel.addView(status)
        val searchButton = Button(this).apply {
            text = "Cari lokasi sekarang"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = rounded(colorPrimary, 14)
            setOnClickListener { startGpsSearch(status) }
        }
        panel.addView(searchButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        val mapButton = Button(this).apply {
            text = "Buka aplikasi peta"
            isAllCaps = false
            setTextColor(colorTextDark)
            background = rounded(colorPrimaryLight, 14, colorBorder, dp(1))
            setOnClickListener { openMap() }
        }
        val mapParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48))
        mapParams.topMargin = dp(8)
        panel.addView(mapButton, mapParams)

        val dialog = AlertDialog.Builder(this)
            .setTitle("GPS & Lokasi")
            .setView(panel)
            .setPositiveButton("Tutup", null)
            .create()
        dialog.setOnDismissListener {
            stopGpsSearch()
            gpsDialogStatus = null
        }
        dialog.show()
    }

    private fun startGpsSearch(status: TextView) {
        stopGpsSearch()
        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) {
            status.text = "Izin lokasi belum diberikan. Tutup lalu coba buka GPS lagi."
            return
        }

        try {
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            for (provider in providers) {
                try {
                    if (locationManager.isProviderEnabled(provider)) {
                        val cached = locationManager.getLastKnownLocation(provider)
                        if (cached != null && (lastGpsLocation == null || cached.time > lastGpsLocation!!.time)) {
                            lastGpsLocation = cached
                        }
                    }
                } catch (_: SecurityException) { }
            }
        } catch (_: Exception) { }

        val cachedLocation = lastGpsLocation
        if (cachedLocation != null) {
            status.text = formatLocation(cachedLocation) + "\n\nData ini berasal dari lokasi terakhir yang tersimpan di perangkat."
            return
        }

        status.text = "Sedang mencari koordinat... Pastikan layanan Lokasi aktif."
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                lastGpsLocation = location
                status.text = formatLocation(location) + "\n\nLokasi berhasil diperoleh."
                stopGpsSearch()
            }
            override fun onProviderEnabled(provider: String) { }
            override fun onProviderDisabled(provider: String) {
                status.text = "Provider $provider tidak aktif. Coba aktifkan Lokasi/GPS di pengaturan HP."
            }
            @Deprecated("Deprecated in Android")
            override fun onStatusChanged(provider: String?, statusCode: Int, extras: Bundle?) { }
        }
        gpsListener = listener
        var requested = false
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                if (locationManager.isProviderEnabled(provider)) {
                    locationManager.requestLocationUpdates(provider, 1000L, 0f, listener)
                    requested = true
                }
            } catch (_: SecurityException) {
            } catch (_: IllegalArgumentException) { }
        }
        if (!requested) {
            status.text = "Tidak ada layanan lokasi yang aktif. Aktifkan GPS/Lokasi pada HP lalu coba lagi."
            stopGpsSearch()
            return
        }
        val timeout = Runnable {
            status.text = "Lokasi belum ditemukan. Coba pindah ke area terbuka, aktifkan GPS, lalu tekan cari lagi."
            stopGpsSearch()
        }
        gpsTimeout = timeout
        mainHandler.postDelayed(timeout, 20_000L)
    }

    private fun stopGpsSearch() {
        gpsTimeout?.let { mainHandler.removeCallbacks(it) }
        gpsTimeout = null
        gpsListener?.let {
            try {
                locationManager.removeUpdates(it)
            } catch (_: SecurityException) { }
        }
        gpsListener = null
    }

    private fun formatLocation(location: Location): String {
        return String.format(
            Locale.US,
            "Latitude : %.6f\nLongitude: %.6f\nAkurasi  : %.1f meter",
            location.latitude,
            location.longitude,
            location.accuracy
        )
    }

    private fun openMap() {
        val location = lastGpsLocation
        val uri = if (location != null) {
            Uri.parse("geo:${location.latitude},${location.longitude}?q=${location.latitude},${location.longitude}")
        } else {
            Uri.parse("geo:0,0?q=lokasi saya")
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            toast("Tidak ada aplikasi peta yang terpasang.")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == GPS_PERMISSION_REQUEST) {
            if (grantResults.any { it == android.content.pm.PackageManager.PERMISSION_GRANTED }) {
                showGpsDialog()
            } else {
                toast("Izin lokasi diperlukan untuk menggunakan fitur GPS.")
            }
        }
    }

    private fun showSensors() {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        if (accelerometer == null && gyroscope == null) {
            AlertDialog.Builder(this)
                .setTitle("Sensor perangkat")
                .setMessage("Perangkat ini tidak menyediakan accelerometer maupun gyroscope yang dapat dibaca.")
                .setPositiveButton("Tutup", null)
                .show()
            return
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(6), dp(20), dp(8))
        }
        panel.addView(label("ACCELEROMETER · m/s²", 12f, colorPrimary, true))
        val accelValues = label(if (accelerometer == null) "Sensor tidak tersedia" else "X: 0.00   Y: 0.00   Z: 0.00", 14f, colorTextDark, false)
        accelValues.setPadding(0, dp(7), 0, dp(16))
        panel.addView(accelValues)
        panel.addView(label("GYROSCOPE · rad/s", 12f, colorPrimary, true))
        val gyroValues = label(if (gyroscope == null) "Sensor tidak tersedia" else "X: 0.00   Y: 0.00   Z: 0.00", 14f, colorTextDark, false)
        gyroValues.setPadding(0, dp(7), 0, dp(10))
        panel.addView(gyroValues)
        panel.addView(label("Gerakkan atau miringkan HP untuk melihat perubahan nilai sensor.", 12f, colorTextMedium, false))

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val text = String.format(Locale.US, "X: %.2f   Y: %.2f   Z: %.2f", event.values[0], event.values[1], event.values[2])
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> accelValues.text = text
                    Sensor.TYPE_GYROSCOPE -> gyroValues.text = text
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }
        }
        activeSensorListener = listener
        val dialog = AlertDialog.Builder(this)
            .setTitle("Gyroscope & Accelerometer")
            .setView(panel)
            .setPositiveButton("Tutup", null)
            .create()
        dialog.setOnDismissListener {
            sensorManager.unregisterListener(listener)
            if (activeSensorListener === listener) activeSensorListener = null
        }
        dialog.show()
        accelerometer?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        gyroscope?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
    }

    private fun refreshDisplay() {
        resultText.text = displayValue
    }

    private fun formatNumber(number: Double): String {
        if (number == 0.0) return "0"
        val formatter = DecimalFormat("0.########", DecimalFormatSymbols(Locale.US))
        return formatter.format(number)
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
        }
    }

    private fun rounded(color: Int, radiusDp: Int, strokeColor: Int? = null, strokeWidth: Int = 0): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeColor != null && strokeWidth > 0) setStroke(strokeWidth, strokeColor)
        }
    }

    private fun marginParams(bottom: Int = 0): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            if (bottom > 0) bottomMargin = dp(bottom)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        stopGpsSearch()
        activeSensorListener?.let { sensorManager.unregisterListener(it) }
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }
}
