package com.example.mylibrary.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val LibraryShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(CardCornerRadius),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
)
