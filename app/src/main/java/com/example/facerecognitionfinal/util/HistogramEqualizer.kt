package com.example.facerecognitionfinal.util

import android.graphics.Bitmap
import android.graphics.Color

object HistogramEqualizer {

    fun equalize(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val size = w * h
        val pixels = IntArray(size)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // 1. Calculate luminance
        val luma = IntArray(size)
        for (i in 0 until size) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            luma[i] = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255)
        }

        // 2. Equalize luma array using pure math function
        val equalizedLuma = equalizeLuma(luma)

        // 3. Reconstruct output pixels
        val outPixels = IntArray(size)
        for (i in 0 until size) {
            val equalized = equalizedLuma[i]
            outPixels[i] = Color.rgb(equalized, equalized, equalized)
        }

        val outBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        outBitmap.setPixels(outPixels, 0, w, 0, 0, w, h)
        return outBitmap
    }

    fun equalizeLuma(luma: IntArray): IntArray {
        val size = luma.size
        if (size == 0) return luma

        val hist = IntArray(256)
        for (i in 0 until size) {
            val y = luma[i].coerceIn(0, 255)
            hist[y]++
        }

        val cdf = IntArray(256)
        var sum = 0
        for (i in 0..255) {
            sum += hist[i]
            cdf[i] = sum
        }

        var cdfMin = 0
        for (i in 0..255) {
            if (cdf[i] > 0) {
                cdfMin = cdf[i]
                break
            }
        }

        val outLuma = IntArray(size)
        val denominator = size - cdfMin
        if (denominator <= 0) {
            for (i in 0 until size) {
                outLuma[i] = luma[i]
            }
        } else {
            for (i in 0 until size) {
                val y = luma[i]
                outLuma[i] = kotlin.math.round((cdf[y] - cdfMin).toFloat() / denominator * 255f).toInt().coerceIn(0, 255)
            }
        }
        return outLuma
    }
}
