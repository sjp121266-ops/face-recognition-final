package com.example.facerecognitionfinal.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

object BitmapUtils {

    fun fromImageProxy(image: ImageProxy): Bitmap {
        val bitmap = if (image.format == ImageFormat.JPEG) {
            val buffer = image.planes[0].buffer
            buffer.rewind()
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw IllegalStateException("Unable to decode captured JPEG image")
        } else {
            val nv21 = yuv420ToNv21(image)
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 90, out)
            val jpegBytes = out.toByteArray()
            BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                ?: throw IllegalStateException("Unable to decode YUV image as JPEG")
        }
        return rotate(bitmap, image.imageInfo.rotationDegrees.toFloat())
    }

    fun crop(source: Bitmap, rect: Rect): Bitmap {
        val left = rect.left.coerceAtLeast(0)
        val top = rect.top.coerceAtLeast(0)
        val right = rect.right.coerceAtMost(source.width)
        val bottom = rect.bottom.coerceAtMost(source.height)
        require(right > left && bottom > top) { "Invalid face bounds" }
        return Bitmap.createBitmap(source, left, top, right - left, bottom - top)
    }

    fun cropFace(source: Bitmap, rect: Rect, paddingRatio: Float = FACE_CROP_PADDING_RATIO): Bitmap {
        return crop(source, expandedFaceBounds(source, rect, paddingRatio))
    }

    fun toJpegBytes(bitmap: Bitmap, quality: Int = 92): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }

    fun expandedFaceBounds(source: Bitmap, rect: Rect, paddingRatio: Float = FACE_CROP_PADDING_RATIO): Rect {
        require(paddingRatio >= 0f) { "Face crop padding ratio must be non-negative" }
        val width = rect.width().coerceAtLeast(1)
        val height = rect.height().coerceAtLeast(1)
        val horizontalPadding = (width * paddingRatio).toInt()
        val verticalPadding = (height * paddingRatio).toInt()
        val left = (rect.left - horizontalPadding).coerceAtLeast(0)
        val top = (rect.top - verticalPadding).coerceAtLeast(0)
        val right = (rect.right + horizontalPadding).coerceAtMost(source.width)
        val bottom = (rect.bottom + verticalPadding).coerceAtMost(source.height)
        require(right > left && bottom > top) { "Invalid face bounds" }
        return Rect(left, top, right, bottom)
    }

    /**
     * Align face crop using eye positions for better recognition accuracy.
     * Rotates the face so eyes are horizontal, then crops.
     */
    fun cropAlignedFace(source: Bitmap, rect: Rect, leftEyeX: Float, leftEyeY: Float, rightEyeX: Float, rightEyeY: Float): Bitmap {
        val expanded = expandedFaceBounds(source, rect)
        // Calculate rotation angle to make eyes horizontal
        val dX = rightEyeX - leftEyeX
        val dY = rightEyeY - leftEyeY
        val angle = Math.toDegrees(Math.atan2(dY.toDouble(), dX.toDouble())).toFloat()

        val matrix = Matrix()
        if (kotlin.math.abs(angle) > 2f) {
            val cx = expanded.exactCenterX()
            val cy = expanded.exactCenterY()
            matrix.postRotate(angle, cx, cy)
        }

        return Bitmap.createBitmap(source, expanded.left, expanded.top, expanded.width(), expanded.height(), matrix, true)
    }

    private fun yuv420ToNv21(image: ImageProxy): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val nv21 = ByteArray(ySize + ySize / 2)

        copyLumaPlane(
            buffer = image.planes[0].buffer,
            rowStride = image.planes[0].rowStride,
            pixelStride = image.planes[0].pixelStride,
            width = width,
            height = height,
            output = nv21
        )
        copyChromaPlanes(
            uBuffer = image.planes[1].buffer,
            uRowStride = image.planes[1].rowStride,
            uPixelStride = image.planes[1].pixelStride,
            vBuffer = image.planes[2].buffer,
            vRowStride = image.planes[2].rowStride,
            vPixelStride = image.planes[2].pixelStride,
            width = width,
            height = height,
            output = nv21,
            offset = ySize
        )
        return nv21
    }

    private fun copyLumaPlane(
        buffer: ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        width: Int,
        height: Int,
        output: ByteArray
    ) {
        val source = buffer.duplicate()
        for (row in 0 until height) {
            for (col in 0 until width) {
                output[row * width + col] = source.get(row * rowStride + col * pixelStride)
            }
        }
    }

    private fun copyChromaPlanes(
        uBuffer: ByteBuffer,
        uRowStride: Int,
        uPixelStride: Int,
        vBuffer: ByteBuffer,
        vRowStride: Int,
        vPixelStride: Int,
        width: Int,
        height: Int,
        output: ByteArray,
        offset: Int
    ) {
        val uSource = uBuffer.duplicate()
        val vSource = vBuffer.duplicate()
        val chromaWidth = width / 2
        val chromaHeight = height / 2
        for (row in 0 until chromaHeight) {
            for (col in 0 until chromaWidth) {
                val outputIndex = offset + row * width + col * 2
                output[outputIndex] = vSource.get(row * vRowStride + col * vPixelStride)
                output[outputIndex + 1] = uSource.get(row * uRowStride + col * uPixelStride)
            }
        }
    }

    private fun rotate(source: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return source
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, false)
    }

    private const val FACE_CROP_PADDING_RATIO = 0.12f
}
