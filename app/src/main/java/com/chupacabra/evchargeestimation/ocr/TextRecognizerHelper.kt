package com.chupacabra.evchargeestimation.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * On-device ML Kit text recognition wrapper.
 * Runs fully offline after the model is available on the device.
 *
 * Preprocessing is tuned for glowing blue-on-black EV instrument clusters
 * like those in app/sampledata/ (reflections, low ambient light, cyan text).
 */
class TextRecognizerHelper {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(bitmap: Bitmap): String {
        val prepared = prepareForOcr(bitmap)
        return try {
            val image = InputImage.fromBitmap(prepared, 0)
            val result = recognizer.process(image).await()
            result.text
        } finally {
            if (prepared !== bitmap && !prepared.isRecycled) {
                prepared.recycle()
            }
        }
    }

    /**
     * Continuous analyzer path: use the camera frame directly (fast path).
     * Falls back to bitmap + contrast prep when MediaImage is unavailable.
     */
    suspend fun recognize(imageProxy: ImageProxy): String {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )
            return recognizer.process(image).await().text
        }
        val bitmap = imageProxyToBitmap(imageProxy) ?: return ""
        return try {
            recognize(bitmap)
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    fun close() {
        recognizer.close()
    }

    companion object {
        /** Upscale small captures so thin dashboard glyphs are easier for ML Kit. */
        private const val MIN_LONG_EDGE = 1600

        /**
         * Converts a CameraX ImageProxy (JPEG or YUV) to a rotated Bitmap.
         */
        fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
            val bitmap = when (imageProxy.format) {
                ImageFormat.JPEG -> jpegImageProxyToBitmap(imageProxy)
                ImageFormat.YUV_420_888 -> yuvImageProxyToBitmap(imageProxy)
                else -> {
                    // ImageCapture often delivers JPEG even when format constant differs
                    jpegImageProxyToBitmap(imageProxy)
                        ?: yuvImageProxyToBitmap(imageProxy)
                }
            } ?: return null

            val rotation = imageProxy.imageInfo.rotationDegrees
            if (rotation == 0) return bitmap

            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        /**
         * Upscale + contrast boost for dark dashboards with cyan/white glyphs.
         * Returns a new bitmap when work is done (caller may recycle it if !== source).
         */
        fun prepareForOcr(source: Bitmap): Bitmap {
            var working = source

            val longEdge = max(working.width, working.height)
            if (longEdge in 1 until MIN_LONG_EDGE) {
                val scale = MIN_LONG_EDGE.toFloat() / longEdge
                val w = (working.width * scale).roundToInt().coerceAtLeast(1)
                val h = (working.height * scale).roundToInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(working, w, h, true)
                if (working !== source && !working.isRecycled) working.recycle()
                working = scaled
            }

            val enhanced = boostContrastForCluster(working)
            if (enhanced !== working && working !== source && !working.isRecycled) {
                working.recycle()
            }
            return enhanced
        }

        /**
         * Grayscale + contrast stretch so faint blue HUD text stands out from
         * reflections. Keeps the pipeline fully on-device and cheap.
         */
        private fun boostContrastForCluster(source: Bitmap): Bitmap {
            val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            // Push mid-tones apart: dark stays dark, glowing digits go near-white.
            val contrast = 1.45f
            val translate = (-0.5f * contrast + 0.5f) * 255f
            val matrix = ColorMatrix(
                floatArrayOf(
                    // R'  G'  B'  A'  T
                    0.213f * contrast, 0.715f * contrast, 0.072f * contrast, 0f, translate,
                    0.213f * contrast, 0.715f * contrast, 0.072f * contrast, 0f, translate,
                    0.213f * contrast, 0.715f * contrast, 0.072f * contrast, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            paint.colorFilter = ColorMatrixColorFilter(matrix)
            canvas.drawBitmap(source, 0f, 0f, paint)
            return out
        }

        private fun jpegImageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
            return try {
                val buffer: ByteBuffer = imageProxy.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {
                null
            }
        }

        private fun yuvImageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
            val image = imageProxy.image ?: return null
            return try {
                val nv21 = yuv420888ToNv21(image)
                val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
                val out = ByteArrayOutputStream()
                yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 90, out)
                val jpegBytes = out.toByteArray()
                BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            } catch (_: Exception) {
                null
            }
        }

        private fun yuv420888ToNv21(image: Image): ByteArray {
            val width = image.width
            val height = image.height
            val ySize = width * height
            val nv21 = ByteArray(ySize + ySize / 2)

            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            val yRowStride = image.planes[0].rowStride
            val yPixelStride = image.planes[0].pixelStride
            var pos = 0
            for (row in 0 until height) {
                val yRowStart = row * yRowStride
                for (col in 0 until width) {
                    nv21[pos++] = yBuffer.get(yRowStart + col * yPixelStride)
                }
            }

            val uRowStride = image.planes[1].rowStride
            val vRowStride = image.planes[2].rowStride
            val uPixelStride = image.planes[1].pixelStride
            val vPixelStride = image.planes[2].pixelStride
            val chromaHeight = height / 2
            val chromaWidth = width / 2

            for (row in 0 until chromaHeight) {
                for (col in 0 until chromaWidth) {
                    val vIndex = row * vRowStride + col * vPixelStride
                    val uIndex = row * uRowStride + col * uPixelStride
                    nv21[pos++] = vBuffer.get(vIndex)
                    nv21[pos++] = uBuffer.get(uIndex)
                }
            }
            return nv21
        }
    }
}
