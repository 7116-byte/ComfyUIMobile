package com.local.comfyuimobile.bridge

import com.local.comfyuimobile.model.ParameterField
import com.local.comfyuimobile.model.ParameterKind
import org.junit.Assert.*
import org.junit.Test

class ParameterSyncTest {
    private fun field(value: String = "A") = ParameterField("1/model", "1", "模型", "UNETLoader",
        "model", "模型", "combo", ParameterKind.COMBO, "\"$value\"", displayValue = value)

    @Test fun revertingToInitialValueIsStillApplied() {
        val initial = field()
        val second = initial.copy(valueJson = "\"B\"")
        val reverted = second.copy(valueJson = initial.originalValueJson)
        assertEquals(listOf(initial), ParameterSync.editable(listOf(initial)))
        assertEquals("\"B\"", ParameterSync.editable(listOf(second)).single().valueJson)
        assertEquals("\"A\"", ParameterSync.editable(listOf(reverted)).single().valueJson)
    }

    @Test fun specialWidgetsAndLinkedInputsAreNeverOverwritten() {
        assertTrue(ParameterSync.editable(listOf(field().copy(kind = ParameterKind.UNSUPPORTED), field().copy(linked = true))).isEmpty())
    }

    @Test fun unchangedDecimalsRemainEligible() {
        val strength = field().copy(kind = ParameterKind.DECIMAL, valueJson = "0.5", originalValueJson = "0.5")
        assertEquals(listOf(strength), ParameterSync.editable(listOf(strength)))
    }
}
