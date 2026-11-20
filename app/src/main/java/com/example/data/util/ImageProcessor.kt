package com.example.data.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
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
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Collections
import okio.Buffer
import java.io.ByteArrayOutputStream
import java.io.File
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
    const val MAX_DIMENSION_PX = 800
    const val TARGET_BYTES = 240_000
    const val MAX_BYTES = 440_000
    const val CLOUD_IMAGE_SCHEME = "cloudimg://"
    private const val DISK_CACHE_DIR = "cloud_images"

    suspend fun decodeForPreview(context: Context, uri: Uri, maxPreviewPx: Int = 1200): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    // Fallback direct decode if bounds probe didn't report dimensions
                    val direct = context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    } ?: return@runCatching null
                    return@runCatching applyExifOrientation(context, uri, scaleDownIfNeeded(direct, maxPreviewPx))
                }

                var sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxPreviewPx, maxPreviewPx)
                var decoded: Bitmap? = null
                for (attempt in 0 until 3) {
                    try {
                        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                        decoded = context.contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it, null, opts)
                        }
                        if (decoded != null) break
                    } catch (_: OutOfMemoryError) {
                        sampleSize *= 2
                    }
                }
                val bmp = decoded ?: return@runCatching null
                applyExifOrientation(context, uri, bmp)
            }.getOrNull()
        }

    private fun applyExifOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return runCatching {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            rotated
        }.getOrDefault(bitmap)
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
                val dimensionSteps = listOf(MAX_DIMENSION_PX, 640, 480, 360)
                var chosenBitmap = bitmap
                var outputBytes = ByteArray(0)

                outer@ for (maxDim in dimensionSteps) {
                    val scaled = scaleDownIfNeeded(bitmap, maxDim)
                    chosenBitmap = scaled
                    var quality = 84
                    while (quality >= 36) {
                        val stream = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                        outputBytes = stream.toByteArray()
                        if (outputBytes.isNotEmpty() && outputBytes.size <= TARGET_BYTES) {
                            break@outer
                        }
                        quality -= 8
                    }
                    if (outputBytes.isNotEmpty() && outputBytes.size <= MAX_BYTES) {
                        break@outer
                    }
                }

                require(outputBytes.isNotEmpty() && outputBytes.size <= MAX_BYTES) {
                    "Image exceeds maximum cloud upload size (${MAX_BYTES / 1024} KB). Please crop tighter."
                }
                val base64 = Base64.encodeToString(outputBytes, Base64.NO_WRAP)
                ProcessedImagePayload(
                    bytes = outputBytes,
                    base64Data = base64,
                    mimeType = "image/jpeg",
                    width = chosenBitmap.width,
                    height = chosenBitmap.height,
                    sizeBytes = outputBytes.size
                )
            }
        }

    fun getCachedCloudImageBytes(context: Context, imageId: String): ByteArray? {
        if (imageId.isBlank()) return null
        return runCatching {
            val file = cacheFileFor(context, imageId)
            if (file.exists() && file.length() > 0L) file.readBytes() else null
        }.getOrNull()
    }

    fun cacheCloudImageBytes(context: Context, imageId: String, bytes: ByteArray) {
        if (imageId.isBlank() || bytes.isEmpty()) return
        runCatching {
            val dir = File(context.cacheDir, DISK_CACHE_DIR).apply { mkdirs() }
            val target = cacheFileFor(context, imageId)
            val tmp = File(dir, "${safeFileName(imageId)}.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) {
                target.writeBytes(bytes)
                tmp.delete()
            }
        }
    }

    private fun cacheFileFor(context: Context, imageId: String): File {
        val dir = File(context.cacheDir, DISK_CACHE_DIR)
        return File(dir, "${safeFileName(imageId)}.img")
    }

    private fun safeFileName(imageId: String): String =
        imageId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")

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
        val imageId = data.host?.trim()?.takeIf { it.isNotBlank() }
            ?: data.path?.trimStart('/')?.trim()?.takeIf { it.isNotBlank() }
            ?: return null
        return CloudImageFetcher(context, db, imageId)
    }
}

private class CloudImageFetcher(
    private val context: Context,
    private val db: FirebaseFirestore,
    private val imageId: String
) : Fetcher {
    companion object {
        // Limit parallel multi-hundred-KB Firestore image fetches so mobile networks don't time out
        private val fetchSemaphore = Semaphore(permits = 4)
        @Volatile
        private var quotaPausedUntilMs: Long = 0L
        private val missingImageIds: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    }

    override suspend fun fetch(): FetchResult {
        // 1. Serve immediately from local disk cache (0 Firestore reads)
        val cachedBytes = withContext(Dispatchers.IO) {
            ImageProcessor.getCachedCloudImageBytes(context, imageId)
        }
        if (cachedBytes != null && cachedBytes.isNotEmpty()) {
            return SourceResult(
                source = ImageSource(Buffer().write(cachedBytes), context),
                mimeType = "image/jpeg",
                dataSource = DataSource.DISK
            )
        }

        // 2. Fetch via Firestore offline cache first, then server only if needed & quota isn't paused
        return fetchSemaphore.withPermit {
            val recheck = withContext(Dispatchers.IO) {
                ImageProcessor.getCachedCloudImageBytes(context, imageId)
            }
            if (recheck != null && recheck.isNotEmpty()) {
                return@withPermit SourceResult(
                    source = ImageSource(Buffer().write(recheck), context),
                    mimeType = "image/jpeg",
                    dataSource = DataSource.DISK
                )
            }

            val docRef = db.collection("productImages").document(imageId)

            // Check Firestore SQLite cache first (0 billed server reads)
            var snapshot = runCatching { docRef.get(Source.CACHE).await() }
                .getOrNull()
                ?.takeIf { it.exists() }

            var lastError: Exception? = null
            if (snapshot == null) {
                val now = System.currentTimeMillis()
                if (now < quotaPausedUntilMs) {
                    throw IllegalStateException("Firestore daily read quota paused — cached image unavailable")
                }
                if (imageId in missingImageIds) {
                    throw IllegalStateException("Cloud image $imageId not found")
                }

                // Try server at most twice ONLY for transient network errors; never retry quota/auth/not-found
                for (attempt in 0..1) {
                    if (attempt > 0) delay(450L)
                    try {
                        val fetched = docRef.get(Source.SERVER).await()
                        if (fetched.exists()) {
                            snapshot = fetched
                        } else {
                            missingImageIds.add(imageId)
                        }
                        break
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        lastError = e
                        val ffe = generateSequence(e as Throwable) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        when (ffe?.code) {
                            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> {
                                quotaPausedUntilMs = System.currentTimeMillis() + 60_000L
                                break
                            }
                            FirebaseFirestoreException.Code.PERMISSION_DENIED,
                            FirebaseFirestoreException.Code.UNAUTHENTICATED,
                            FirebaseFirestoreException.Code.NOT_FOUND -> break
                            else -> Unit // Retry once on transient network error
                        }
                    }
                }
            }

            if (snapshot == null) {
                lastError?.let { handleFirestoreError(it, OperationType.GET, docRef.path) }
                throw lastError ?: IllegalStateException("Cloud image $imageId not found")
            }

            val base64 = snapshot.getString("base64Data")?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Cloud image $imageId not found")
            val mimeType = snapshot.getString("mimeType") ?: "image/jpeg"
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            require(bytes.isNotEmpty()) { "Decoded cloud image $imageId is empty" }

            withContext(Dispatchers.IO) {
                ImageProcessor.cacheCloudImageBytes(context, imageId, bytes)
            }

            SourceResult(
                source = ImageSource(Buffer().write(bytes), context),
                mimeType = mimeType,
                dataSource = DataSource.NETWORK
            )
        }
    }
}
