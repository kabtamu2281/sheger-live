package et.ethiolive.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import et.ethiolive.app.R
import et.ethiolive.app.data.RoomCard
import et.ethiolive.app.ui.components.Avatar
import et.ethiolive.app.ui.components.LiveBadge
import et.ethiolive.app.ui.components.ViewerPill
import et.ethiolive.app.ui.theme.Brand

val CATEGORIES = listOf("music", "chat", "dance", "gaming", "education", "comedy", "culture", "other")

@Composable
fun categoryLabel(key: String): String = stringResource(
    when (key) {
        "music" -> R.string.cat_music
        "chat" -> R.string.cat_chat
        "dance" -> R.string.cat_dance
        "gaming" -> R.string.cat_gaming
        "education" -> R.string.cat_education
        "comedy" -> R.string.cat_comedy
        "culture" -> R.string.cat_culture
        else -> R.string.cat_other
    },
)

private fun coverBrush(seed: String): Brush {
    val palettes = listOf(
        listOf(Color(0xFF6B3DF5), Color(0xFFFF4F6D)),
        listOf(Color(0xFF13C38B), Color(0xFF3FA7FF)),
        listOf(Color(0xFFFFB627), Color(0xFFFF4F6D)),
        listOf(Color(0xFF3FA7FF), Color(0xFF6B3DF5)),
    )
    return Brush.linearGradient(palettes[(seed.hashCode() and 0x7fffffff) % palettes.size])
}

@Composable
fun RoomCardTile(room: RoomCard, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().aspectRatio(0.78f).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxSize().background(coverBrush(room.host.id)))
        val cover = room.coverUrl ?: room.host.avatarUrl
        if (cover != null) AsyncImage(model = cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xCC000000))))
        Row(Modifier.padding(8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveBadge(label = if (room.inPk) stringResource(R.string.pk_badge) else stringResource(R.string.live_badge))
                Spacer(Modifier.width(4.dp))
                when (room.mode) {
                    "audio" -> Icon(Icons.Default.GraphicEq, stringResource(R.string.mode_audio), tint = Color.White)
                    "multi_guest" -> Icon(Icons.Default.Groups, stringResource(R.string.mode_multi), tint = Color.White)
                    else -> if (room.inPk) Icon(Icons.Default.SportsMma, null, tint = Color.White)
                }
            }
            ViewerPill(room.viewerCount)
        }
        Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            Text(room.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Avatar(room.host.avatarUrl, room.host.displayName, 20.dp)
                Spacer(Modifier.width(6.dp))
                Text(room.host.displayName, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (room.hashtags.isNotEmpty()) Text(
                room.hashtags.take(2).joinToString(" ") { "#$it" }, color = Brand.Gold, fontSize = 11.sp, maxLines = 1,
            )
        }
    }
}
