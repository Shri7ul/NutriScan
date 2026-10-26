package com.nutriscan.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nutriscan.app.R
import com.nutriscan.app.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private const val TARGET_IMAGE_WIDTH_PX = 720

private sealed interface ImageState {
    data object Loading : ImageState
    data class Loaded(val bitmap: Bitmap) : ImageState
    data object Missing : ImageState
}

/**
 * Product photo loader. Deliberately small: one image per screen, an in-memory LRU cache and
 * downsampling. No image library is needed for this milestone.
 */
@Composable
fun RemoteProductImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    showMissingLabel: Boolean = true
) {
    val state by produceState<ImageState>(initialValue = ImageState.Loading, key1 = url) {
        value = ImageState.Loading
        value = if (url.isNullOrBlank()) {
            ImageState.Missing
        } else {
            val bitmap = ProductImageLoader.load(url, TARGET_IMAGE_WIDTH_PX)
            if (bitmap != null) ImageState.Loaded(bitmap) else ImageState.Missing
        }
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        when (val current = state) {
            is ImageState.Loaded -> Image(
                bitmap = current.bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            ImageState.Loading -> CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ImageState.Missing -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(8.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_scan_barcode),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(if (showMissingLabel) 28.dp else 20.dp)
                )
                if (showMissingLabel) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.details_image_missing),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

private object ProductImageLoader {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    suspend fun load(url: String, targetWidthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(url)?.let { return@withContext it }

        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", NetworkModule.USER_AGENT)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val bytes = response.body?.bytes() ?: return@use null
                decodeSampled(bytes, targetWidthPx)
            }
        }.getOrNull()?.also { cache.put(url, it) }
    }

    private fun decodeSampled(bytes: ByteArray, targetWidthPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, targetWidthPx)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun calculateInSampleSize(sourceWidth: Int, targetWidthPx: Int): Int {
        var sampleSize = 1
        while (sourceWidth / sampleSize > targetWidthPx * 2) {
            sampleSize *= 2
        }
        return sampleSize
    }
}
