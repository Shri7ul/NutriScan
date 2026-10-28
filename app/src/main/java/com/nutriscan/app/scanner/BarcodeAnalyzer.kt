package com.nutriscan.app.scanner

import android.os.SystemClock
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.nutriscan.app.util.BarcodeValidator

/**
 * CameraX analyzer backed by the bundled ML Kit barcode model (no Play Services download,
 * works offline right after install).
 *
 * Only the four product symbologies are requested, so QR / Data Matrix / Code-128 codes are never
 * decoded and are never claimed as supported. Frame handling rules:
 *  - a value that is not a well formed GTIN is reported through [onInvalidBarcode] instead of being
 *    silently cleaned into a fake code;
 *  - the same code seen twice inside [DEBOUNCE_WINDOW_MS] is dropped;
 *  - after a successful detection the analyzer pauses itself, so no further frames are decoded
 *    until the screen calls [resume] (or a fresh analyzer is created).
 */
class BarcodeAnalyzer(
    private val onBarcodeDetected: (String) -> Unit,
    private val onInvalidBarcode: (BarcodeValidator.Rejection) -> Unit = {}
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E
            )
            .build()
    )

    private val lock = Any()
    private var lastValue: String? = null
    private var lastDetectedAt = 0L
    private var lastRejectedAt = 0L

    @Volatile
    private var paused = false

    @ExperimentalGetImage
    override fun analyze(image: ImageProxy) {
        if (paused) {
            image.close()
            return
        }

        val mediaImage = image.image
        if (mediaImage == null) {
            image.close()
            return
        }

        val input = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes -> handle(barcodes) }
            // A single failed frame is not a user-visible error: the next frame is analysed.
            .addOnCompleteListener { image.close() }
    }

    private fun handle(barcodes: List<Barcode>) {
        if (paused) return

        for (barcode in barcodes) {
            val raw = barcode.rawValue.orEmpty()
            val rejection = BarcodeValidator.inspect(raw)
            if (rejection != BarcodeValidator.Rejection.OK) {
                reportRejected(rejection)
                continue
            }

            val normalized = BarcodeValidator.sanitize(raw) ?: continue
            val now = SystemClock.elapsedRealtime()
            synchronized(lock) {
                if (normalized == lastValue && now - lastDetectedAt < DEBOUNCE_WINDOW_MS) return
                lastValue = normalized
                lastDetectedAt = now
            }

            paused = true
            onBarcodeDetected(normalized)
            return
        }
    }

    /** Rate limits the "this is not a product barcode" message so it cannot spam the UI. */
    private fun reportRejected(rejection: BarcodeValidator.Rejection) {
        val now = SystemClock.elapsedRealtime()
        synchronized(lock) {
            if (now - lastRejectedAt < REJECTION_COOLDOWN_MS) return
            lastRejectedAt = now
        }
        onInvalidBarcode(rejection)
    }

    /** Stops decoding while the app navigates away from the scanner. */
    fun pause() {
        paused = true
    }

    /** Allows decoding again after the user explicitly asks to scan another product. */
    fun resume() {
        synchronized(lock) {
            lastValue = null
            lastDetectedAt = 0L
            lastRejectedAt = 0L
        }
        paused = false
    }

    fun close() {
        paused = true
        scanner.close()
    }

    private companion object {
        const val DEBOUNCE_WINDOW_MS = 1_200L
        const val REJECTION_COOLDOWN_MS = 2_500L
    }
}
