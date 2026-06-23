package com.example.facerecognitionfinal.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBufferFloat
import kotlin.math.sqrt

class FaceNetModelTest {

    @Test
    fun standardizeOpCentersFixedInputAndKeepsExpectedStandardDeviation() {
        val output = standardize(floatArrayOf(1f, 2f, 3f, 4f))

        assertEquals(0f, output.average().toFloat(), 0.0001f)
        assertEquals(1f, standardDeviation(output), 0.0001f)
    }

    @Test
    fun standardizeOpDoesNotProduceNonFiniteValuesForConstantInput() {
        val output = standardize(FloatArray(4) { 7f })

        output.forEach { value ->
            assertTrue(value.isFinite())
        }
        assertEquals(0f, output.average().toFloat(), 0.0001f)
        assertEquals(0f, standardDeviation(output), 0.0001f)
    }

    private fun standardize(input: FloatArray): FloatArray {
        val buffer = TensorBufferFloat.createFixedSize(intArrayOf(input.size), DataType.FLOAT32)
        buffer.loadArray(input)

        return FaceNetModel.StandardizeOp().apply(buffer).floatArray
    }

    private fun standardDeviation(values: FloatArray): Float {
        val mean = values.average().toFloat()
        var squaredDifferenceSum = 0f
        for (value in values) {
            val difference = value - mean
            squaredDifferenceSum += difference * difference
        }
        return sqrt(squaredDifferenceSum / values.size.toFloat())
    }
}
