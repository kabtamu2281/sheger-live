package et.ethiolive.app.ui.gifts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Original gift artwork drawn from primitives (no third-party or copied assets). Keys match gifts.artwork_key.
 * Unknown keys fall back to a generic gift box so new server-side gifts never crash older app versions.
 */
@Composable
fun GiftArt(key: String, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawGift(key) }
}

private val Gold = Color(0xFFFFB627)
private val GoldDeep = Color(0xFFE08A00)
private val Coral = Color(0xFFFF4F6D)
private val Violet = Color(0xFF6B3DF5)
private val Emerald = Color(0xFF13C38B)
private val Clay = Color(0xFF9A4F2B)
private val ClayDark = Color(0xFF6E3219)
private val Cream = Color(0xFFFFF1D6)
private val Brown = Color(0xFF5B3A1E)

fun DrawScope.drawGift(key: String) {
    when (key) {
        "adey" -> adey()
        "clap" -> clap()
        "buna" -> buna()
        "rose" -> rose()
        "heart" -> heart(Coral)
        "injera" -> injera()
        "jebena" -> jebena()
        "bloom" -> bloom()
        "kebero" -> kebero()
        "demera" -> demera()
        "gelada" -> gelada()
        "lion" -> lion()
        "crown" -> crown()
        "lalibela" -> lalibela()
        else -> giftBox()
    }
}

private fun DrawScope.u(f: Float) = size.minDimension * f
private fun DrawScope.p(x: Float, y: Float) = Offset(size.width * x, size.height * y)

private fun DrawScope.petals(center: Offset, count: Int, length: Float, width: Float, color: Color, startDeg: Float = 0f) {
    for (i in 0 until count) {
        rotate(startDeg + i * 360f / count, center) {
            drawOval(color, topLeft = Offset(center.x - width / 2, center.y - length), size = Size(width, length))
        }
    }
}

private fun DrawScope.adey() {
    val c = p(0.5f, 0.45f)
    drawLine(Emerald, p(0.5f, 0.55f), p(0.5f, 0.95f), strokeWidth = u(0.06f))
    petals(c, 12, u(0.32f), u(0.12f), Gold)
    drawCircle(GoldDeep, u(0.11f), c)
    drawCircle(Brown, u(0.06f), c)
}

private fun DrawScope.clap() {
    rotate(-18f, p(0.42f, 0.55f)) {
        drawRoundRect(Gold, topLeft = p(0.22f, 0.25f), size = Size(u(0.3f), u(0.55f)), cornerRadius = CornerRadius(u(0.14f)))
    }
    rotate(18f, p(0.58f, 0.55f)) {
        drawRoundRect(GoldDeep, topLeft = p(0.48f, 0.25f), size = Size(u(0.3f), u(0.55f)), cornerRadius = CornerRadius(u(0.14f)))
    }
    for (i in 0..2) {
        val a = (-60 + i * 60) * PI / 180
        val o = p(0.5f, 0.18f)
        drawLine(Coral, o, Offset(o.x + (sin(a) * u(0.12f)).toFloat(), o.y - (cos(a) * u(0.12f)).toFloat()), strokeWidth = u(0.04f))
    }
}

private fun DrawScope.buna() {
    drawOval(Cream, topLeft = p(0.12f, 0.72f), size = Size(u(0.76f), u(0.16f)))
    val cup = Path().apply {
        moveTo(size.width * 0.24f, size.height * 0.42f)
        lineTo(size.width * 0.76f, size.height * 0.42f)
        quadraticTo(size.width * 0.74f, size.height * 0.78f, size.width * 0.5f, size.height * 0.8f)
        quadraticTo(size.width * 0.26f, size.height * 0.78f, size.width * 0.24f, size.height * 0.42f)
        close()
    }
    drawPath(cup, Color.White)
    drawOval(Brown, topLeft = p(0.26f, 0.38f), size = Size(u(0.48f), u(0.1f)))
    drawPath(Path().apply {
        moveTo(size.width * 0.18f, size.height * 0.6f)
        lineTo(size.width * 0.82f, size.height * 0.6f)
    }, Coral, style = Stroke(u(0.04f)))
    for (i in 0..2) {
        val x = size.width * (0.38f + i * 0.12f)
        drawPath(Path().apply {
            moveTo(x, size.height * 0.32f)
            cubicTo(x - u(0.06f), size.height * 0.24f, x + u(0.06f), size.height * 0.18f, x, size.height * 0.1f)
        }, Color.White.copy(alpha = 0.7f), style = Stroke(u(0.03f)))
    }
}

private fun DrawScope.rose() {
    drawLine(Emerald, p(0.5f, 0.5f), p(0.5f, 0.95f), strokeWidth = u(0.06f))
    drawOval(Emerald, topLeft = p(0.5f, 0.66f), size = Size(u(0.22f), u(0.1f)))
    val c = p(0.5f, 0.38f)
    drawCircle(Coral, u(0.26f), c)
    drawCircle(Color(0xFFE0304F), u(0.18f), Offset(c.x + u(0.03f), c.y + u(0.02f)))
    drawArc(Color(0xFFB81F3B), 200f, 240f, false, topLeft = Offset(c.x - u(0.1f), c.y - u(0.1f)), size = Size(u(0.2f), u(0.2f)), style = Stroke(u(0.035f)))
}

private fun DrawScope.heartPath(cx: Float, cy: Float, w: Float): Path = Path().apply {
    moveTo(cx, cy + w * 0.35f)
    cubicTo(cx - w * 0.9f, cy - w * 0.2f, cx - w * 0.4f, cy - w * 0.85f, cx, cy - w * 0.35f)
    cubicTo(cx + w * 0.4f, cy - w * 0.85f, cx + w * 0.9f, cy - w * 0.2f, cx, cy + w * 0.35f)
    close()
}

private fun DrawScope.heart(color: Color) {
    drawPath(heartPath(size.width * 0.5f, size.height * 0.55f, u(0.62f)), Brush.linearGradient(listOf(color, Violet)))
    for (r in listOf(0.38f, 0.46f)) drawCircle(color.copy(alpha = 0.35f), u(r), p(0.5f, 0.5f), style = Stroke(u(0.025f)))
}

private fun DrawScope.injera() {
    drawOval(Clay, topLeft = p(0.08f, 0.55f), size = Size(u(0.84f), u(0.3f)))
    drawOval(Color(0xFFD9C29A), topLeft = p(0.12f, 0.4f), size = Size(u(0.76f), u(0.32f)))
    val dots = listOf(0.3f to 0.52f, 0.5f to 0.48f, 0.66f to 0.56f, 0.42f to 0.6f, 0.58f to 0.62f)
    dots.forEachIndexed { i, (x, y) -> drawCircle(listOf(Coral, Gold, Emerald, Brown, GoldDeep)[i], u(0.06f), p(x, y)) }
}

private fun DrawScope.jebena() {
    drawCircle(Clay, u(0.3f), p(0.5f, 0.62f))
    drawRect(ClayDark, topLeft = p(0.43f, 0.18f), size = Size(u(0.14f), u(0.2f)))
    drawOval(ClayDark, topLeft = p(0.38f, 0.12f), size = Size(u(0.24f), u(0.08f)))
    drawPath(Path().apply {
        moveTo(size.width * 0.72f, size.height * 0.55f)
        quadraticTo(size.width * 0.9f, size.height * 0.45f, size.width * 0.92f, size.height * 0.3f)
    }, ClayDark, style = Stroke(u(0.06f)))
    drawArc(ClayDark, 100f, 160f, false, topLeft = p(0.08f, 0.3f), size = Size(u(0.3f), u(0.36f)), style = Stroke(u(0.05f)))
    drawLine(Gold, p(0.3f, 0.62f), p(0.7f, 0.62f), strokeWidth = u(0.03f))
}

private fun DrawScope.bloom() {
    petals(p(0.32f, 0.5f), 8, u(0.18f), u(0.09f), Gold)
    drawCircle(GoldDeep, u(0.05f), p(0.32f, 0.5f))
    petals(p(0.66f, 0.38f), 8, u(0.2f), u(0.1f), Gold, 22f)
    drawCircle(Coral, u(0.06f), p(0.66f, 0.38f))
    petals(p(0.62f, 0.74f), 6, u(0.14f), u(0.08f), Color(0xFFFFD36E))
    drawCircle(GoldDeep, u(0.04f), p(0.62f, 0.74f))
}

private fun DrawScope.kebero() {
    drawOval(Cream, topLeft = p(0.15f, 0.18f), size = Size(u(0.7f), u(0.2f)))
    drawPath(Path().apply {
        moveTo(size.width * 0.15f, size.height * 0.28f)
        lineTo(size.width * 0.85f, size.height * 0.28f)
        lineTo(size.width * 0.72f, size.height * 0.82f)
        lineTo(size.width * 0.28f, size.height * 0.82f)
        close()
    }, Clay)
    for (i in 0..4) {
        val t = i / 4f
        drawLine(Cream, p(0.18f + t * 0.64f, 0.3f), p(0.3f + t * 0.4f, 0.8f), strokeWidth = u(0.02f))
    }
    drawOval(ClayDark, topLeft = p(0.27f, 0.76f), size = Size(u(0.46f), u(0.12f)))
}

private fun DrawScope.demera() {
    drawLine(Brown, p(0.3f, 0.92f), p(0.5f, 0.2f), strokeWidth = u(0.05f))
    drawLine(Brown, p(0.7f, 0.92f), p(0.5f, 0.2f), strokeWidth = u(0.05f))
    drawLine(Brown, p(0.5f, 0.92f), p(0.5f, 0.2f), strokeWidth = u(0.05f))
    val flame = Path().apply {
        moveTo(size.width * 0.5f, size.height * 0.05f)
        cubicTo(size.width * 0.85f, size.height * 0.35f, size.width * 0.75f, size.height * 0.7f, size.width * 0.5f, size.height * 0.72f)
        cubicTo(size.width * 0.25f, size.height * 0.7f, size.width * 0.15f, size.height * 0.35f, size.width * 0.5f, size.height * 0.05f)
        close()
    }
    drawPath(flame, Brush.verticalGradient(listOf(Gold, Coral)))
    drawCircle(Gold, u(0.04f), p(0.5f, 0.12f))
    drawLine(Gold, p(0.42f, 0.18f), p(0.58f, 0.18f), strokeWidth = u(0.035f))
}

private fun DrawScope.gelada() {
    drawCircle(Color(0xFF8A5A33), u(0.4f), p(0.5f, 0.5f))
    drawOval(Color(0xFFD9A577), topLeft = p(0.28f, 0.3f), size = Size(u(0.44f), u(0.5f)))
    drawCircle(Brown, u(0.05f), p(0.4f, 0.44f))
    drawCircle(Brown, u(0.05f), p(0.6f, 0.44f))
    drawOval(Color(0xFFB57B4E), topLeft = p(0.38f, 0.54f), size = Size(u(0.24f), u(0.16f)))
    drawPath(heartPath(size.width * 0.5f, size.height * 0.88f, u(0.22f)), Coral)
}

private fun DrawScope.lion() {
    val c = p(0.5f, 0.52f)
    for (i in 0 until 16) {
        rotate(i * 22.5f, c) { drawOval(GoldDeep, topLeft = Offset(c.x - u(0.07f), c.y - u(0.46f)), size = Size(u(0.14f), u(0.26f))) }
    }
    drawCircle(Gold, u(0.28f), c)
    drawCircle(Brown, u(0.035f), Offset(c.x - u(0.1f), c.y - u(0.06f)))
    drawCircle(Brown, u(0.035f), Offset(c.x + u(0.1f), c.y - u(0.06f)))
    drawPath(Path().apply {
        moveTo(c.x - u(0.06f), c.y + u(0.06f)); lineTo(c.x + u(0.06f), c.y + u(0.06f)); lineTo(c.x, c.y + u(0.13f)); close()
    }, Brown)
    drawLine(Coral, Offset(c.x - u(0.12f), c.y - u(0.24f)), Offset(c.x + u(0.12f), c.y - u(0.24f)), strokeWidth = u(0.04f))
}

private fun DrawScope.crown() {
    val path = Path().apply {
        moveTo(size.width * 0.12f, size.height * 0.75f)
        lineTo(size.width * 0.12f, size.height * 0.32f)
        lineTo(size.width * 0.32f, size.height * 0.52f)
        lineTo(size.width * 0.5f, size.height * 0.2f)
        lineTo(size.width * 0.68f, size.height * 0.52f)
        lineTo(size.width * 0.88f, size.height * 0.32f)
        lineTo(size.width * 0.88f, size.height * 0.75f)
        close()
    }
    drawPath(path, Brush.verticalGradient(listOf(Gold, GoldDeep)))
    drawRect(GoldDeep, topLeft = p(0.12f, 0.72f), size = Size(u(0.76f), u(0.1f)))
    drawCircle(Coral, u(0.05f), p(0.5f, 0.56f))
    drawCircle(Emerald, u(0.04f), p(0.3f, 0.64f))
    drawCircle(Violet, u(0.04f), p(0.7f, 0.64f))
    listOf(0.12f to 0.32f, 0.5f to 0.2f, 0.88f to 0.32f).forEach { (x, y) -> drawCircle(Cream, u(0.04f), p(x, y)) }
}

private fun DrawScope.lalibela() {
    // A cross-shaped rock-hewn church seen from above, set into the earth.
    drawRoundRect(Color(0xFFB0643A), topLeft = p(0.04f, 0.04f), size = Size(u(0.92f), u(0.92f)), cornerRadius = CornerRadius(u(0.12f)))
    val arm = u(0.24f)
    val c = p(0.5f, 0.5f)
    val stone = Brush.linearGradient(listOf(Color(0xFFD98A55), Color(0xFF8E4422)))
    drawRect(stone, topLeft = Offset(c.x - arm / 2, c.y - u(0.38f)), size = Size(arm, u(0.76f)))
    drawRect(stone, topLeft = Offset(c.x - u(0.38f), c.y - arm / 2), size = Size(u(0.76f), arm))
    drawRect(Color(0xFF6E3219), topLeft = Offset(c.x - u(0.05f), c.y - u(0.26f)), size = Size(u(0.1f), u(0.52f)))
    drawRect(Color(0xFF6E3219), topLeft = Offset(c.x - u(0.26f), c.y - u(0.05f)), size = Size(u(0.52f), u(0.1f)))
}

private fun DrawScope.giftBox() {
    drawRoundRect(Violet, topLeft = p(0.15f, 0.38f), size = Size(u(0.7f), u(0.5f)), cornerRadius = CornerRadius(u(0.06f)))
    drawRect(Gold, topLeft = p(0.45f, 0.38f), size = Size(u(0.1f), u(0.5f)))
    drawRoundRect(Coral, topLeft = p(0.1f, 0.28f), size = Size(u(0.8f), u(0.14f)), cornerRadius = CornerRadius(u(0.04f)))
    drawCircle(Gold, u(0.08f), p(0.4f, 0.22f), style = Stroke(u(0.04f)))
    drawCircle(Gold, u(0.08f), p(0.6f, 0.22f), style = Stroke(u(0.04f)))
}
