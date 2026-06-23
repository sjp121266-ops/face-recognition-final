package com.example.facerecognitionfinal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonNameValidatorTest {

    private val validator = PersonNameValidator()

    @Test
    fun trimsAndCollapsesWhitespace() {
        val result = validator.validate("  张   三  ")

        assertEquals(PersonNameValidator.Result.Valid("张 三"), result)
    }

    @Test
    fun rejectsBlankName() {
        val result = validator.validate("   ")

        assertTrue(result is PersonNameValidator.Result.Invalid)
        assertTrue((result as PersonNameValidator.Result.Invalid).message.contains("请先输入"))
    }

    @Test
    fun rejectsReservedName() {
        val result = validator.validate("未知人员")

        assertTrue(result is PersonNameValidator.Result.Invalid)
        assertTrue((result as PersonNameValidator.Result.Invalid).message.contains("系统保留名称"))
    }

    @Test
    fun rejectsLongName() {
        val result = validator.validate("这是一个非常非常非常长的姓名")

        assertTrue(result is PersonNameValidator.Result.Invalid)
        assertTrue((result as PersonNameValidator.Result.Invalid).message.contains("姓名过长"))
    }
}
