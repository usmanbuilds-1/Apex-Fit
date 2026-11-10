package com.apexfit.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorInitializationTest {
    @Test
    fun legacyAliasesRemainReadableAfterSemanticPaletteIsInitializedFirst() {
        // This order matches application startup and previously exposed the cycle.
        assertEquals(Color(0xFF041521), ApexColors.Canvas)
        assertEquals(Color.White, PrimaryText)
        assertEquals(Color(0xFF8B98A5), SecondaryText)
        assertEquals(Color(0xFF081C27), DarkCardSurface)
        assertEquals(ApexColors.Primary, GreenAccent)
    }
}
