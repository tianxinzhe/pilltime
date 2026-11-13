package com.alex.pilltime.scan

import org.junit.Assert.assertEquals
import org.junit.Test

class MedicineNameExtractorTest {

    @Test
    fun `drops pure dose lines, pure numbers and blank lines`() {
        val lines = listOf("500 mg", "10ml", "5 %", "250 g", "12345", "", "   ")

        val result = MedicineNameExtractor.extract(lines)

        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `keeps Chinese and English medicine names in original order`() {
        val lines = listOf("阿莫西林胶囊", "Amoxicillin", "布洛芬缓释胶囊 300 mg")

        val result = MedicineNameExtractor.extract(lines)

        assertEquals(listOf("阿莫西林胶囊", "Amoxicillin", "布洛芬缓释胶囊 300 mg"), result)
    }

    @Test
    fun `removes duplicates ignoring case`() {
        val lines = listOf("Amoxicillin", "amoxicillin", "AMOXICILLIN", "Ibuprofen")

        val result = MedicineNameExtractor.extract(lines)

        assertEquals(listOf("Amoxicillin", "Ibuprofen"), result)
    }

    @Test
    fun `truncates to five candidates by default`() {
        val lines = (1..6).map { "Medicine $it" }

        val result = MedicineNameExtractor.extract(lines)

        assertEquals(5, result.size)
        assertEquals("Medicine 1", result.first())
        assertEquals("Medicine 5", result.last())
    }

    @Test
    fun `drops lines outside length boundaries`() {
        val oneChar = "阿"
        val fortyChars = "药".repeat(40)
        val fortyOneChars = "药".repeat(41)

        val result = MedicineNameExtractor.extract(listOf(oneChar, fortyChars, fortyOneChars))

        assertEquals(listOf(fortyChars), result)
    }

    @Test
    fun `respects custom candidate limit`() {
        val lines = listOf("Aspirin", "Ibuprofen", "Paracetamol")

        val result = MedicineNameExtractor.extract(lines, maxCandidates = 2)

        assertEquals(listOf("Aspirin", "Ibuprofen"), result)
    }

    @Test
    fun `returns empty list for empty input`() {
        assertEquals(emptyList<String>(), MedicineNameExtractor.extract(emptyList()))
    }
}