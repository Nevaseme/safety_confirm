package io.github.nevaseme.plandmk1.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Light = lightColorScheme(primary = Color(0xFF155EEF), onPrimary = Color.White,
    primaryContainer = Color(0xFFE8EFFF), onPrimaryContainer = Color(0xFF163C54),
    secondary = Color(0xFF163C54), secondaryContainer = Color(0xFFE5EDF4), onSecondaryContainer = Color(0xFF163C54),
    surface = Color.White, surfaceContainer = Color(0xFFF0F4F8), background = Color(0xFFF3F7FA),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF3F7FA),
    surfaceContainerHigh = Color(0xFFEAF0F5), surfaceContainerHighest = Color(0xFFE1EAF1), surfaceTint = Color(0xFF155EEF),
    onSurface = Color(0xFF183447), onBackground = Color(0xFF183447), onSurfaceVariant = Color(0xFF4C6272),
    error = Color(0xFFB42318), onError=Color.White, outlineVariant=Color(0xFFD6E0E8))
private val Dark = darkColorScheme(primary = Color(0xFFAAC7FF), secondary = Color(0xFFBED7E8),
    surface=Color(0xFF142630),background=Color(0xFF10212B),onSurface=Color(0xFFE4EFF7),onBackground=Color(0xFFE4EFF7),
    surfaceContainerLowest=Color(0xFF0C1C25),surfaceContainerLow=Color(0xFF142630),surfaceContainer=Color(0xFF1B303C),
    surfaceContainerHigh=Color(0xFF223945),surfaceContainerHighest=Color(0xFF29434F),onSurfaceVariant=Color(0xFFBDCDD8),surfaceTint=Color(0xFFAAC7FF),
    secondaryContainer=Color(0xFF294756),onSecondaryContainer=Color(0xFFDFEFFA),error=Color(0xFFFFB4AB))

@Composable
fun PlanDMk1Theme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) Dark else Light, typography = Typography,
        shapes=androidx.compose.material3.Shapes(small=androidx.compose.foundation.shape.RoundedCornerShape(8.dp),medium=androidx.compose.foundation.shape.RoundedCornerShape(12.dp),large=androidx.compose.foundation.shape.RoundedCornerShape(20.dp)), content = content)
}

