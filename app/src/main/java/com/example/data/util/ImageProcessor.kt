package com.example.data.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okio.Buffer
import java.io.ByteArrayOutputStream
import kotlin.math.min

data class ProcessedImagePayload(
    val bytes: ByteArray,
    val base64Data: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Int
)

object ImageProcessor {
    const val MAX_DIMENSION_PX = 900
    const val MAX_BYTES = 450_000
    const val CLOUD_IMAGE_SCHEME = "cloudimg://"

    suspend fun decodeForPreview(context: Context, uri: Uri, maxPreviewPx: Int = 1200): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxPreviewPx, maxPreviewPx)
                val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
            }.getOrNull()
        }

    fun cropAndRotate(
        source: Bitmap,
        rotationDegrees: Float,
        cropFractionLeft: Float,
        cropFractionTop: Float,
        cropFractionRight: Float,
        cropFractionBottom: Float
    ): Bitmap {
        val rotated = if (rotationDegrees % 360f != 0f) {
            val matrix = Matrix().apply { postRotate(rotationDegrees) }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        } else {
            source
        }
        val left = (cropFractionLeft.coerceIn(0f, 0.9f) * rotated.width).toInt()
        val top = (cropFractionTop.coerceIn(0f, 0.9f) * rotated.height).toInt()
        val right = (cropFractionRight.coerceIn(cropFractionLeft + 0.1f, 1f) * rotated.width).toInt()
        val bottom = (cropFractionBottom.coerceIn(cropFractionTop + 0.1f, 1f) * rotated.height).toInt()
        val width = (right - left).coerceAtLeast(16)
        val height = (bottom - top).coerceAtLeast(16)
        return Bitmap.createBitmap(rotated, left, top, width, height)
    }

    suspend fun compressForCloudUpload(bitmap: Bitmap): Result<ProcessedImagePayload> =
        withContext(Dispatchers.IO) {
            runCatching {
                val scaled = scaleDownIfNeeded(bitmap, MAX_DIMENSION_PX)
                var quality = 86
                var outputBytes: ByteArray
                do {
                    val stream = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                    outputBytes = stream.toByteArray()
                    quality -= 8
                } while (outputBytes.size > MAX_BYTES && quality >= 38)

                require(outputBytes.isNotEmpty() && outputBytes.size <= MAX_BYTES) {
                    "Image exceeds maximum cloud upload size (${MAX_BYTES / 1024} KB). Please crop tighter."
                }
                val base64 = Base64.encodeToString(outputBytes, Base64.NO_WRAP)
                ProcessedImagePayload(
                    bytes = outputBytes,
                    base64Data = base64,
                    mimeType = "image/jpeg",
                    width = scaled.width,
                    height = scaled.height,
                    sizeBytes = outputBytes.size
                )
            }
        }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = min(maxDim.toFloat() / w.toFloat(), maxDim.toFloat() / h.toFloat())
        val targetW = (w * ratio).toInt().coerceAtLeast(1)
        val targetH = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}

class CloudImageFetcherFactory(
    private val context: Context,
    private val db: FirebaseFirestore
) : Fetcher.Factory<Uri> {
    override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
        if (data.scheme != "cloudimg") return null
        val imageId = data.host ?: data.path?.trimStart('/') ?: return null
        return CloudImageFetcher(context, db, imageId)
    }
}

private class CloudImageFetcher(
    private val context: Context,
    private val db: FirebaseFirestore,
    private val imageId: String
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val docRef = db.collection("productImages").document(imageId)
        val snapshot = try {
            docRef.get().await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
        val base64 = snapshot.getString("base64Data")
            ?: throw IllegalStateException("Cloud image $imageId not found")
        val mimeType = snapshot.getString("mimeType") ?: "image/jpeg"
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        val buffer = Buffer().write(bytes)
        return SourceResult(
            source = ImageSource(buffer, context),
            mimeType = mimeType,
            dataSource = DataSource.NETWORK
        )
    }
}
