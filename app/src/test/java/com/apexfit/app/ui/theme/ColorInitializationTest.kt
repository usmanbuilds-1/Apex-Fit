package com.apexfit.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorInitializationTest {
    @Test
    fun legacyAliasesRemainReadableAfterSemanticPaletteIsInitializedFirst() {
        // This order matches application startup and previously exposed the cycle.
        assertEquals(Color(0xFF101617), ApexColors.Canvas)
        assertEquals(Color(0xFFF3F5F3), PrimaryText)
        assertEquals(Color(0xFFADB7B8), SecondaryText)
        assertEquals(Color(0xFF1B2223), DarkCardSurface)
        assertEquals(ApexColors.Primary, GreenAccent)
    }
    @Test
    fun supportingTextHasReadableContrastOnAppSurfaces() {
        for (background in listOf(ApexColors.Canvas, ApexColors.Surface, ApexColors.SurfaceRaised)) {
            val ratio = (ApexColors.TextSecondary.luminance() + 0.05f) / (background.luminance() + 0.05f)
            assertTrue("Supporting text contrast was $ratio", ratio >= 4.5f)
        }
    }
}
