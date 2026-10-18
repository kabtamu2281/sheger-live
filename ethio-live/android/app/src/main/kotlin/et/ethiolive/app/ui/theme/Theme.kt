package et.ethiolive.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Brand {
    val Night = Color(0xFF120B24)
    val NightRaised = Color(0xFF1D1436)
    val Violet = Color(0xFF6B3DF5)
    val VioletSoft = Color(0xFF9C7CFF)
    val Gold = Color(0xFFFFB627)
    val Emerald = Color(0xFF13C38B)
    val Coral = Color(0xFFFF4F6D)
    val Sky = Color(0xFF3FA7FF)
    val Live = Color(0xFFFF3B5C)
    val Scrim = Color(0x99000000)

    val heroGradient = Brush.linearGradient(listOf(Violet, Coral))
    val goldGradient = Brush.linearGradient(listOf(Gold, Coral))
    val pkA = Color(0xFFFF4F6D)
    val pkB = Color(0xFF3FA7FF)
}

private val DarkColors = darkColorScheme(
    primary = Brand.Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3B2390),
    onPrimaryContainer = Color.White,
    secondary = Brand.Gold,
    onSecondary = Color(0xFF2B1B00),
    tertiary = Brand.Emerald,
    error = Brand.Coral,
    background = Brand.Night,
    onBackground = Color(0xFFF2EEFF),
    surface = Brand.Night,
    onSurface = Color(0xFFF2EEFF),
    surfaceVariant = Brand.NightRaised,
    onSurfaceVariant = Color(0xFFBDB3DA),
    surfaceContainer = Brand.NightRaised,
    surfaceContainerHigh = Color(0xFF261B45),
    outline = Color(0xFF4A3D72),
)

private val LightColors = lightColorScheme(
    primary = Brand.Violet,
    onPrimary = Color.White,
    secondary = Color(0xFFB57800),
    tertiary = Color(0xFF0C8F66),
    error = Color(0xFFD32F4F),
    background = Color(0xFFF8F6FF),
    surface = Color(0xFFF8F6FF),
    surfaceVariant = Color(0xFFECE6FA),
    surfaceContainer = Color(0xFFF0EBFC),
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp),
)

/** Always dark: live video, gift effects and overlays are designed for a dark canvas. */
@Composable
fun EthioLiveTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = AppTypography, content = content)
}
