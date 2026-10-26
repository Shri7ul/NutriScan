package com.nutriscan.app.scanner

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Verifies that the bundled ML Kit barcode model is present in the APK and decodes a real EAN-13
 * image with the same options the scanner uses. The bundled model ships inside the APK, so this
 * runs without downloading anything.
 */
@RunWith(AndroidJUnit4::class)
class BundledBarcodeModelTest {

    @Test
    fun decodesEan13FromBundledModel() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Test assets are packaged in the test APK, not the app APK.
        val testContext = instrumentation.context
        val appContext = instrumentation.targetContext

        val imageFile = File(appContext.cacheDir, "ean13_test.png").apply {
            testContext.assets.open("ean13_3017620422003.png").use { input ->
                outputStream().use { output -> input.copyTo(output) }
            }
        }

        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E
                )
                .build()
        )

        try {
            val image = InputImage.fromFilePath(appContext, Uri.fromFile(imageFile))
            val barcodes = Tasks.await(scanner.process(image), 30, TimeUnit.SECONDS)

            assertFalse("Bundled model returned no barcode", barcodes.isEmpty())
            assertEquals("3017620422003", barcodes.first().rawValue)
            assertEquals(Barcode.FORMAT_EAN_13, barcodes.first().format)
        } finally {
            scanner.close()
        }
    }
}
