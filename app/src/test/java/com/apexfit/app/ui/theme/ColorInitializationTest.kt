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
        assertEquals(Color(0xFF041521), ApexColors.Canvas)
        assertEquals(Color.White, PrimaryText)
        assertEquals(Color(0xFFAABAC5), SecondaryText)
        assertEquals(Color(0xFF0B202B), DarkCardSurface)
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
