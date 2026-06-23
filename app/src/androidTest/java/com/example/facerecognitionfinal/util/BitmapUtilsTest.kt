package com.example.facerecognitionfinal.util

import android.graphics.Bitmap
import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class BitmapUtilsTest {

    @Test
    fun cropReturnsCorrectSubRegion() {
        val source = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val cropped = BitmapUtils.crop(source, Rect(10, 20, 110, 120))
        assertEquals(100, cropped.width)
        assertEquals(100, cropped.height)
    }

    @Test
    fun cropClampsRectBeyondBitmapBounds() {
        val source = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val cropped = BitmapUtils.crop(source, Rect(50, 50, 200, 200))
        assertEquals(50, cropped.width)
        assertEquals(50, cropped.height)
    }

    @Test
    fun cropClampsNegativeCoordinatesToZero() {
        val source = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val cropped = BitmapUtils.crop(source, Rect(-10, -10, 50, 50))
        assertEquals(50, cropped.width)
        assertEquals(50, cropped.height)
    }

    @Test
    fun cropFaceExpandsBoundsWithDefaultPadding() {
        val source = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val expanded = BitmapUtils.expandedFaceBounds(source, Rect(50, 50, 100, 100))

        assertEquals(Rect(44, 44, 106, 106), expanded)
    }

    @Test
    fun cropFaceClampsExpandedBoundsToBitmap() {
        val source = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val cropped = BitmapUtils.cropFace(source, Rect(0, 0, 20, 20))

        assertEquals(22, cropped.width)
        assertEquals(22, cropped.height)
    }

    @Test
    fun cropFaceHandlesTinyBounds() {
        val source = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        val cropped = BitmapUtils.cropFace(source, Rect(10, 10, 11, 11))

        assertEquals(1, cropped.width)
        assertEquals(1, cropped.height)
    }

    @Test(expected = IllegalArgumentException::class)
    fun cropThrowsForZeroSizeResult() {
        val source = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        BitmapUtils.crop(source, Rect(100, 0, 100, 50))
    }
}
