package com.ebsoft.shollu.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Zip-skill starting radii on M3 [Shapes] tokens ([androidx.compose.foundation.shape.CornerBasedShape]
 * / [RoundedCornerShape], not graphics-shapes squircles). [Shapes.medium] is 16.dp+, not
 * classic 12.dp cards. Passed at the [androidx.compose.material3.MaterialExpressiveTheme]
 * root so surfaces pick these up without hardcoded radii. [Shapes.largeIncreased] is the
 * Expressive featured-surface token (hero / picker).
 */
val SholluShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
    largeIncreased = RoundedCornerShape(36.dp)
)
