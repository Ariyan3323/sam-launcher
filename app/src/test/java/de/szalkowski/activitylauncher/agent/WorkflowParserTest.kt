package de.szalkowski.activitylauncher.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkflowParserTest {
    @Test
    fun parsesPersianAndEnglishWorkflowSeparatorsInOrder() {
        val steps = WorkflowParser.parse("باز کن Chrome، سپس جستجو آب و هوا; بعدش باتری")

        assertEquals(listOf("باز کن Chrome", "جستجو آب و هوا", "باتری"), steps)
    }

    @Test
    fun limitsWorkflowToSixSteps() {
        val steps = WorkflowParser.parse("one; two; three; four; five; six; seven")

        assertEquals(6, steps.size)
        assertEquals("six", steps.last())
    }
}
