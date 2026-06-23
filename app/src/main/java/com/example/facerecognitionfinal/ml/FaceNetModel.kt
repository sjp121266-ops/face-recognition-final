package com.example.facerecognitionfinal.ml

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.TensorOperator
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import org.tensorflow.lite.support.tensorbuffer.TensorBufferFloat
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.sqrt

class FaceNetModel(context: Context) {

    private val interpreter = Interpreter(
        FileUtil.loadMappedFile(context, MODEL_FILE),
        Interpreter.Options().apply {
            numThreads = 4
            setUseXNNPACK(true)
        }
    )

    private val imageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
        .add(HistogramEqualizeOp())
        .add(StandardizeOp())
        .build()

    fun getEmbedding(faceBitmap: Bitmap): FloatArray {
        val output = Array(1) { FloatArray(EMBEDDING_DIM) }
        interpreter.run(convertBitmapToBuffer(faceBitmap), output)
        return output[0]
    }

    /**
     * TTA (Test-Time Augmentation): average embedding of original + horizontally flipped face.
     * Improves robustness to slight head rotations (~1-3% accuracy gain).
     */
    fun getEmbeddingTTA(faceBitmap: Bitmap): FloatArray {
        val original = getEmbedding(faceBitmap)
        val flipped = Bitmap.createBitmap(faceBitmap.width, faceBitmap.height, faceBitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(flipped)
        val matrix = android.graphics.Matrix().apply { postScale(-1f, 1f, faceBitmap.width / 2f, faceBitmap.height / 2f) }
        canvas.drawBitmap(faceBitmap, matrix, null)
        val flippedEmbedding = getEmbedding(flipped)
        flipped.recycle()
        // Average
        val fused = FloatArray(EMBEDDING_DIM)
        for (i in 0 until EMBEDDING_DIM) { fused[i] = (original[i] + flippedEmbedding[i]) / 2f }
        return EmbeddingDistance.normalize(fused)
    }

    fun close() {
        interpreter.close()
    }

    private fun convertBitmapToBuffer(image: Bitmap): ByteBuffer {
        return imageProcessor.process(TensorImage.fromBitmap(image)).buffer
    }

    /**
     * Histogram equalization as a TensorOperator.
     * Converts to grayscale luminance, applies CDF-based equalization,
     * then expands back to RGB. Improves robustness to lighting variation.
     */
    class HistogramEqualizeOp : TensorOperator {
        override fun apply(buffer: TensorBuffer?): TensorBuffer {
            val pixels = buffer!!.floatArray
            val size = pixels.size

            // Convert RGB to luminance (0..255 range assumption for input)
            val luminance = FloatArray(size / 3)
            for (i in luminance.indices) {
                val r = pixels[i * 3]
                val g = pixels[i * 3 + 1]
                val b = pixels[i * 3 + 2]
                luminance[i] = 0.299f * r + 0.587f * g + 0.114f * b
            }

            // Build histogram (256 bins)
            val hist = IntArray(256)
            for (l in luminance) {
                val bin = l.toInt().coerceIn(0, 255)
                hist[bin]++
            }

            // CDF
            val cdf = FloatArray(256)
            var accum = 0
            for (i in 0..255) {
                accum += hist[i]
                cdf[i] = accum.toFloat() / luminance.size
            }

            // Find min non-zero CDF
            val cdfMin = cdf.first { it > 0f }

            // Apply equalization
            val equalized = FloatArray(luminance.size)
            for (i in luminance.indices) {
                val bin = luminance[i].toInt().coerceIn(0, 255)
                equalized[i] = ((cdf[bin] - cdfMin) / (1f - cdfMin) * 255f).coerceIn(0f, 255f)
            }

            // Reconstruct RGB: scale original by equalized/luminance ratio
            val result = FloatArray(size)
            for (i in luminance.indices) {
                val ratio = if (luminance[i] > 1f) equalized[i] / luminance[i] else 1f
                result[i * 3] = (pixels[i * 3] * ratio).coerceIn(0f, 255f)
                result[i * 3 + 1] = (pixels[i * 3 + 1] * ratio).coerceIn(0f, 255f)
                result[i * 3 + 2] = (pixels[i * 3 + 2] * ratio).coerceIn(0f, 255f)
            }

            return TensorBufferFloat
                .createFixedSize(buffer.shape, DataType.FLOAT32)
                .apply { loadArray(result) }
        }
    }

    class StandardizeOp : TensorOperator {
        override fun apply(buffer: TensorBuffer?): TensorBuffer {
            val pixels = buffer!!.floatArray
            var sum = 0f
            for (index in pixels.indices) {
                sum += pixels[index]
            }

            val mean = sum / pixels.size.toFloat()
            var squaredDifferenceSum = 0f
            for (index in pixels.indices) {
                val difference = pixels[index] - mean
                squaredDifferenceSum += difference * difference
            }

            val std = max(
                sqrt(squaredDifferenceSum / pixels.size.toFloat()),
                1f / sqrt(pixels.size.toFloat())
            )
            for (index in pixels.indices) {
                pixels[index] = (pixels[index] - mean) / std
            }
            return TensorBufferFloat
                .createFixedSize(buffer.shape, DataType.FLOAT32)
                .apply { loadArray(pixels) }
        }
    }

    companion object {
        private const val MODEL_FILE = "facenet.tflite"
        private const val INPUT_SIZE = 160
        private const val EMBEDDING_DIM = 128
    }
}
