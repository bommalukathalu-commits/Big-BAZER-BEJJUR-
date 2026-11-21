package com.example.data.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ProcessedVideoPayload(
    val bytes: ByteArray,
    val base64Data: String,
    val mimeType: String,
    val durationMs: Long,
    val sizeBytes: Int,
    val thumbnailBitmap: Bitmap?
)

object VideoProcessor {
    const val CLOUD_VIDEO_SCHEME = "cloudvid://"
    const val MAX_UPLOAD_BYTES = 540_000
    const val MAX_DURATION_MS = 12_000L
    private const val DISK_CACHE_DIR = "cloud_videos"

    @Volatile
    private var quotaPausedUntilMs: Long = 0L

    fun getCachedVideoFile(context: Context, videoId: String): File? {
        if (videoId.isBlank()) return null
        val file = cacheFileFor(context, videoId)
        return if (file.exists() && file.length() > 0L) file else null
    }

    fun cacheCloudVideoBytes(context: Context, videoId: String, bytes: ByteArray): File? {
        if (videoId.isBlank() || bytes.isEmpty()) return null
        return runCatching {
            val dir = File(context.cacheDir, DISK_CACHE_DIR).apply { mkdirs() }
            val target = cacheFileFor(context, videoId)
            val tmp = File(dir, "${safeFileName(videoId)}.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) {
                target.writeBytes(bytes)
                tmp.delete()
            }
            target
        }.getOrNull()
    }

    private fun cacheFileFor(context: Context, videoId: String): File {
        val dir = File(context.cacheDir, DISK_CACHE_DIR)
        return File(dir, "${safeFileName(videoId)}.mp4")
    }

    private fun safeFileName(videoId: String): String =
        videoId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")

    suspend fun extractThumbnail(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private suspend fun probeVideoMetadata(context: Context, uri: Uri): Pair<Long, Long> = withContext(Dispatchers.IO) {
        val sizeBytes = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        }.getOrDefault(-1L)

        val retriever = MediaMetadataRetriever()
        val durationMs = try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Throwable) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
        durationMs to sizeBytes
    }

    suspend fun compressForCloudUpload(
        context: Context,
        uri: Uri,
        onProgress: (Int) -> Unit = {}
    ): Result<ProcessedVideoPayload> = runCatching {
        val (rawDurationMs, rawSize) = probeVideoMetadata(context, uri)
        val mime = context.contentResolver.getType(uri).orEmpty().lowercase()

        // Fast path: if already a small short MP4, upload directly without transcoding
        if (mime == "video/mp4" && rawSize in 1L..MAX_UPLOAD_BYTES.toLong() && rawDurationMs in 1L..MAX_DURATION_MS) {
            onProgress(70)
            val bytes = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } ?: error("Could not read selected video")
            require(bytes.isNotEmpty() && bytes.size <= MAX_UPLOAD_BYTES) {
                "Selected video is too large"
            }
            val thumb = extractThumbnail(context, uri)
            onProgress(100)
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            return@runCatching ProcessedVideoPayload(
                bytes = bytes,
                base64Data = b64,
                mimeType = "video/mp4",
                durationMs = rawDurationMs,
                sizeBytes = bytes.size,
                thumbnailBitmap = thumb
            )
        }

        // Progressive Media3 compression passes so almost any phone video fits cleanly in Firestore
        data class CompressionPass(
            val maxDurationMs: Long,
            val targetHeight: Int,
            val videoBitrate: Int,
            val removeAudio: Boolean
        )

        val passes = listOf(
            CompressionPass(MAX_DURATION_MS, 360, 280_000, removeAudio = false),
            CompressionPass(7_500L, 288, 210_000, removeAudio = false),
            CompressionPass(5_000L, 240, 160_000, removeAudio = true)
        )

        val tmpDir = File(context.cacheDir, "video_tmp").apply { mkdirs() }
        var chosenFile: File? = null
        var chosenDurationMs = rawDurationMs.coerceIn(1_000L, MAX_DURATION_MS)

        for ((idx, pass) in passes.withIndex()) {
            val outFile = File(tmpDir, "vid_${UUID.randomUUID().toString().take(8)}.mp4")
            try {
                val clipEndMs = if (rawDurationMs > 0L) minOf(rawDurationMs, pass.maxDurationMs) else pass.maxDurationMs
                transcodeWithMedia3(
                    context = context,
                    sourceUri = uri,
                    outputFile = outFile,
                    clipEndMs = clipEndMs,
                    targetHeight = pass.targetHeight,
                    videoBitrate = pass.videoBitrate,
                    removeAudio = pass.removeAudio,
                    onProgress = { pct ->
                        val base = (idx * 25).coerceAtMost(60)
                        onProgress((base + (pct * 0.4f).toInt()).coerceIn(5, 95))
                    }
                )
                if (outFile.exists() && outFile.length() in 1L..MAX_UPLOAD_BYTES.toLong()) {
                    chosenFile = outFile
                    chosenDurationMs = clipEndMs
                    break
                } else {
                    outFile.delete()
                }
            } catch (e: CancellationException) {
                outFile.delete()
                throw e
            } catch (e: Throwable) {
                outFile.delete()
                if (idx == passes.lastIndex) throw e
            }
        }

        val finalFile = chosenFile
            ?: error("Video could not be compressed under ${MAX_UPLOAD_BYTES / 1024} KB. Try a shorter clip (5–12s).")

        val finalBytes = withContext(Dispatchers.IO) { finalFile.readBytes() }
        val finalThumb = extractThumbnail(context, Uri.fromFile(finalFile))
        withContext(Dispatchers.IO) { finalFile.delete() }
        onProgress(100)

        ProcessedVideoPayload(
            bytes = finalBytes,
            base64Data = Base64.encodeToString(finalBytes, Base64.NO_WRAP),
            mimeType = "video/mp4",
            durationMs = chosenDurationMs,
            sizeBytes = finalBytes.size,
            thumbnailBitmap = finalThumb
        )
    }

    @OptIn(UnstableApi::class)
    private suspend fun transcodeWithMedia3(
        context: Context,
        sourceUri: Uri,
        outputFile: File,
        clipEndMs: Long,
        targetHeight: Int,
        videoBitrate: Int,
        removeAudio: Boolean,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<Unit> { continuation ->
            val mediaItem = MediaItem.Builder()
                .setUri(sourceUri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(0L)
                        .setEndPositionMs(clipEndMs)
                        .build()
                )
                .build()

            val editedItem = EditedMediaItem.Builder(mediaItem)
                .setRemoveAudio(removeAudio)
                .setEffects(
                    Effects(
                        emptyList(),
                        listOf(Presentation.createForHeight(targetHeight))
                    )
                )
                .build()

            val encoderFactory = DefaultEncoderFactory.Builder(context)
                .setEnableFallback(true)
                .setRequestedVideoEncoderSettings(
                    VideoEncoderSettings.Builder()
                        .setBitrate(videoBitrate)
                        .build()
                )
                .build()

            val mainHandler = Handler(Looper.getMainLooper())
            var progressRunnable: Runnable? = null

            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setEncoderFactory(encoderFactory)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        progressRunnable?.let { mainHandler.removeCallbacks(it) }
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        progressRunnable?.let { mainHandler.removeCallbacks(it) }
                        if (continuation.isActive) continuation.resumeWithException(exportException)
                    }
                })
                .build()

            continuation.invokeOnCancellation {
                progressRunnable?.let { mainHandler.removeCallbacks(it) }
                runCatching { transformer.cancel() }
            }

            transformer.start(editedItem, outputFile.absolutePath)

            val holder = ProgressHolder()
            val runnable = object : Runnable {
                override fun run() {
                    val state = transformer.getProgress(holder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(holder.progress)
                    }
                    if (state != Transformer.PROGRESS_STATE_NOT_STARTED && continuation.isActive) {
                        mainHandler.postDelayed(this, 250L)
                    }
                }
            }
            progressRunnable = runnable
            mainHandler.postDelayed(runnable, 250L)
        }
    }

    suspend fun resolveVideoPlayUri(
        context: Context,
        db: FirebaseFirestore,
        videoUrl: String
    ): Result<Uri> = runCatching {
        val clean = videoUrl.trim()
        require(clean.isNotBlank()) { "Video URL is blank" }
        if (!clean.startsWith(CLOUD_VIDEO_SCHEME)) {
            return@runCatching Uri.parse(clean)
        }

        val videoId = clean.removePrefix(CLOUD_VIDEO_SCHEME).trim()
        require(videoId.isNotBlank()) { "Invalid cloud video ID" }

        // 1. Local disk cache (0 Firestore reads)
        val cached = withContext(Dispatchers.IO) { getCachedVideoFile(context, videoId) }
        if (cached != null) {
            return@runCatching Uri.fromFile(cached)
        }

        // 2. Firestore SQLite cache (0 billed reads), then Server
        val docRef = db.collection("productVideos").document(videoId)
        var snapshot = runCatching { docRef.get(Source.CACHE).await() }
            .getOrNull()
            ?.takeIf { it.exists() }

        if (snapshot == null) {
            if (System.currentTimeMillis() < quotaPausedUntilMs) {
                error("Daily read quota reached. Video will play once quota resets.")
            }
            try {
                snapshot = docRef.get(Source.SERVER).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val ffe = generateSequence(e as Throwable) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (ffe?.code == FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED) {
                    quotaPausedUntilMs = System.currentTimeMillis() + 60_000L
                }
                handleFirestoreError(e, OperationType.GET, docRef.path)
                throw e
            }
        }

        require(snapshot != null && snapshot.exists()) { "Product video not found" }
        val base64 = snapshot.getString("base64Data")?.trim().orEmpty()
        require(base64.isNotBlank()) { "Product video data empty" }

        val bytes = withContext(Dispatchers.IO) { Base64.decode(base64, Base64.DEFAULT) }
        val file = withContext(Dispatchers.IO) { cacheCloudVideoBytes(context, videoId, bytes) }
            ?: error("Could not cache video for playback")
        Uri.fromFile(file)
    }
}
