package et.ethiolive.app.ui.gifts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import et.ethiolive.app.ui.components.Avatar
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** A gift shown in the overlay. Built from realtime "gift" room messages, which the server writes after a successful debit. */
data class GiftShow(
    val id: Long,
    val senderName: String,
    val senderAvatar: String?,
    val recipientName: String,
    val giftName: String,
    val artworkKey: String,
    val animationKey: String,
    val combo: Int,
    val fullscreen: Boolean,
)

/** Left-side banners for regular gifts, with the running combo count. */
@Composable
fun GiftBanner(show: GiftShow?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = show != null,
        enter = slideInHorizontally { -it } + fadeIn(),
        exit = slideOutHorizontally { -it } + fadeOut(),
        modifier = modifier,
    ) {
        val s = show ?: return@AnimatedVisibility
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(Color(0xCC6B3DF5), Color(0x33FF4F6D))))
                .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(s.senderAvatar, s.senderName, 32.dp)
            Spacer(Modifier.width(6.dp))
            Column {
                Text(s.senderName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                Text(s.giftName, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, maxLines = 1)
            }
            Spacer(Modifier.width(6.dp))
            GiftArt(s.artworkKey, 36.dp)
            if (s.combo > 1) {
                val scale = remember(s.combo) { Animatable(1.6f) }
                LaunchedEffect(s.combo) { scale.animateTo(1f, tween(250, easing = FastOutSlowInEasing)) }
                Text(
                    "x${s.combo}",
                    color = Color(0xFFFFB627),
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    fontSize = 22.sp,
                    modifier = Modifier.padding(start = 6.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value },
                )
            }
        }
    }
}

/** Full-screen effect for premium gifts (Demera, Gelada, Lion, Crown, Lalibela). Plays once, ~3 seconds. */
@Composable
fun FullscreenGiftEffect(show: GiftShow, onFinished: () -> Unit) {
    val progress = remember(show.id) { Animatable(0f) }
    LaunchedEffect(show.id) {
        progress.animateTo(1f, tween(3000, easing = LinearEasing))
        onFinished()
    }
    val particles = remember(show.id) { List(36) { Random.nextFloat() to Random.nextFloat() } }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val t = progress.value
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height * 0.45f)
            particles.forEachIndexed { i, (a, r) ->
                val angle = a * 2 * Math.PI
                val dist = (0.1f + r * 0.6f) * size.minDimension * t
                val pos = Offset(c.x + (cos(angle) * dist).toFloat(), c.y + (sin(angle) * dist).toFloat() - t * 120f)
                val color = listOf(Color(0xFFFFB627), Color(0xFFFF4F6D), Color(0xFF9C7CFF), Color(0xFF13C38B))[i % 4]
                drawCircle(color.copy(alpha = (1f - t).coerceIn(0f, 1f)), 6f + r * 10f, pos)
            }
            drawCircle(
                Brush.radialGradient(listOf(Color(0x66FFB627), Color.Transparent), center = c, radius = size.minDimension * 0.6f),
                radius = size.minDimension * 0.6f,
                center = c,
                alpha = (1f - t).coerceIn(0f, 1f),
            )
        }
        val enter = (t / 0.2f).coerceAtMost(1f)
        val exit = ((1f - t) / 0.15f).coerceAtMost(1f)
        val motion = when (show.animationKey) {
            "rise" -> (1f - enter) * 300f
            "jump" -> -kotlin.math.abs(sin(t * 12f)) * 60f
            else -> 0f
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer {
            val s = 0.4f + 0.6f * enter
            scaleX = s; scaleY = s
            alpha = exit
            translationY = motion
            rotationZ = if (show.animationKey == "roar" || show.animationKey == "shake") sin(t * 40f) * 4f else 0f
        }) {
            GiftArt(show.artworkKey, 200.dp)
            Text(show.giftName, color = Color.White, fontWeight = FontWeight.Black, fontSize = 26.sp)
            Text("${show.senderName} → ${show.recipientName}", color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp)
        }
    }
}

/** Small floating animation for free gifts and taps (hearts drifting up the right edge). */
@Composable
fun FloatingHearts(trigger: Int, modifier: Modifier = Modifier) {
    Box(modifier.size(width = 80.dp, height = 260.dp)) {
        if (trigger > 0) {
            val a = remember(trigger) { Animatable(0f) }
            LaunchedEffect(trigger) { a.animateTo(1f, tween(1800)) }
            val drift = remember(trigger) { Random.nextFloat() * 2 - 1 }
            Canvas(Modifier.fillMaxSize()) {
                val y = size.height * (1f - a.value)
                val x = size.width / 2 + sin(a.value * 6f) * 20f * drift
                drawCircle(Color(0xFFFF4F6D).copy(alpha = 1f - a.value), 14f * (1f + a.value * 0.4f), Offset(x, y))
            }
        }
    }
}
