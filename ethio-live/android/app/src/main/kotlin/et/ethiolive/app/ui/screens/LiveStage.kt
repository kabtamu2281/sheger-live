package et.ethiolive.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import et.ethiolive.app.R
import et.ethiolive.app.data.Participant
import et.ethiolive.app.data.PkMatch
import et.ethiolive.app.stream.StreamEngine
import et.ethiolive.app.ui.components.Avatar
import et.ethiolive.app.ui.theme.Brand
import et.ethiolive.core.Money
import et.ethiolive.core.PkMath
import et.ethiolive.core.SeatLayout
import java.time.Instant

/** Renders one RTC video stream. The view is created by the engine and released when it leaves composition. */
@Composable
fun VideoSurface(engine: StreamEngine, uid: Int, local: Boolean, modifier: Modifier = Modifier) {
    DisposableEffect(uid, local) { onDispose { engine.releaseVideoView(uid) } }
    AndroidView(factory = { engine.createVideoView(uid, local).also { (it.parent as? android.view.ViewGroup)?.removeView(it) } }, modifier = modifier)
}

@Composable
private fun AvatarPlaceholder(p: Participant?, modifier: Modifier = Modifier, label: Boolean = true) {
    Box(modifier.background(Brush.verticalGradient(listOf(Brand.NightRaised, Brand.Night))), contentAlignment = Alignment.Center) {
        if (p != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(p.avatarUrl, p.displayName, 64.dp)
            if (label) Text(p.displayName, color = Color.White, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** A seated person's tile: their video when publishing, otherwise their avatar. */
@Composable
fun SeatTile(
    engine: StreamEngine,
    participant: Participant?,
    myUserId: String,
    remoteUids: Set<Int>,
    remoteVideoOff: Set<Int>,
    speaking: Boolean,
    onEmptyClick: (() -> Unit)?,
    onOccupiedClick: ((Participant) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier.clip(shape)
            .border(if (speaking) 2.dp else 0.dp, if (speaking) Brand.Emerald else Color.Transparent, shape)
            .clickable(enabled = participant == null && onEmptyClick != null || participant != null && onOccupiedClick != null) {
                if (participant == null) onEmptyClick?.invoke() else onOccupiedClick?.invoke(participant)
            },
    ) {
        if (participant == null) {
            Box(Modifier.fillMaxSize().background(Color(0x33FFFFFF)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, stringResource(R.string.seat_empty), tint = Color.White.copy(alpha = 0.7f))
            }
        } else {
            val local = participant.userId == myUserId
            val showVideo = !participant.cameraOff && (local || (participant.rtcUid in remoteUids && participant.rtcUid !in remoteVideoOff))
            if (showVideo) VideoSurface(engine, if (local) 0 else participant.rtcUid, local, Modifier.fillMaxSize())
            else AvatarPlaceholder(participant, Modifier.fillMaxSize())
            Row(Modifier.align(Alignment.BottomStart).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (participant.micMuted) Icon(Icons.Default.MicOff, stringResource(R.string.muted), tint = Brand.Coral, modifier = Modifier.size(14.dp))
                Text(participant.displayName, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.background(Brand.Scrim, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp))
            }
        }
    }
}

/** Video rooms: full-screen host; multi-guest rooms: the server-defined seat grid. */
@Composable
fun VideoStage(
    engine: StreamEngine,
    maxSeats: Int,
    participants: List<Participant>,
    myUserId: String,
    remoteUids: Set<Int>,
    remoteVideoOff: Set<Int>,
    speaking: Set<Int>,
    onEmptySeat: (() -> Unit)?,
    onSeatClick: ((Participant) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val bySeat = participants.filter { it.seatIndex != null }.associateBy { it.seatIndex!! }
    if (maxSeats <= 1) {
        SeatTile(engine, bySeat[0], myUserId, remoteUids, remoteVideoOff, false, null, null, modifier.fillMaxSize())
        return
    }
    val grid = SeatLayout.grid(maxSeats)
    Column(modifier.fillMaxSize().padding(top = 96.dp, bottom = 200.dp, start = 4.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (row in 0 until grid.rows) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                grid.cells.filter { it.row == row }.sortedBy { it.column }.forEach { cell ->
                    val p = bySeat[cell.seatIndex]
                    SeatTile(
                        engine, p, myUserId, remoteUids, remoteVideoOff,
                        speaking = p != null && p.rtcUid in speaking,
                        onEmptyClick = onEmptySeat, onOccupiedClick = onSeatClick,
                        modifier = Modifier.weight(cell.columnSpan.toFloat()).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

/** Audio rooms: host on top, guests in rows, a ring around whoever is speaking. */
@Composable
fun AudioStage(
    maxSeats: Int,
    participants: List<Participant>,
    myUserId: String,
    speaking: Set<Int>,
    onEmptySeat: (() -> Unit)?,
    onSeatClick: ((Participant) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val bySeat = participants.filter { it.seatIndex != null }.associateBy { it.seatIndex!! }
    @Composable
    fun seat(index: Int, size: Int) {
        val p = bySeat[index]
        val isSpeaking = p != null && p.rtcUid in speaking
        val ring by animateFloatAsState(if (isSpeaking) 1f else 0f, label = "ring")
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(6.dp)) {
            Box(
                Modifier.size((size + 8).dp).clip(CircleShape).border((3 * ring).dp, Brand.Emerald, CircleShape)
                    .clickable { if (p == null) onEmptySeat?.invoke() else onSeatClick?.invoke(p) },
                contentAlignment = Alignment.Center,
            ) {
                if (p == null) Box(Modifier.size(size.dp).clip(CircleShape).background(Color(0x33FFFFFF)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, stringResource(R.string.seat_empty), tint = Color.White.copy(alpha = 0.7f))
                } else Avatar(p.avatarUrl, p.displayName, size.dp)
                if (p?.micMuted == true) Icon(Icons.Default.MicOff, null, tint = Brand.Coral, modifier = Modifier.align(Alignment.BottomEnd).size(16.dp))
            }
            Text(p?.displayName ?: stringResource(R.string.seat_number, index), color = Color.White, fontSize = 11.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
    }
    Column(modifier.fillMaxSize().padding(top = 110.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        seat(0, 84)
        var next = 1
        SeatLayout.audioRows(maxSeats).forEach { count ->
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                repeat(count) { seat(next++, 56) }
            }
        }
    }
}

/** PK battle: split screen (our host left, opponent right) with the server's score bar and timer. */
@Composable
fun PkStage(
    engine: StreamEngine,
    pk: PkMatch,
    myRoomId: String,
    hostParticipant: Participant?,
    myUserId: String,
    remoteUids: Set<Int>,
    serverOffsetMs: Long,
    nowMs: Long,
    modifier: Modifier = Modifier,
) {
    val weAreA = pk.roomA == myRoomId
    val ourScore = if (weAreA) pk.scoreA else pk.scoreB
    val theirScore = if (weAreA) pk.scoreB else pk.scoreA
    val share = PkMath.shareA(ourScore, theirScore)
    val animatedShare by animateFloatAsState(share, label = "pk-share")
    val endsAt = pk.endsAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
    val seconds = endsAt?.let { PkMath.secondsLeft(it, nowMs, serverOffsetMs) } ?: 0L
    val hostUid = hostParticipant?.rtcUid
    val opponentUid = remoteUids.firstOrNull { it != hostUid }
    Column(modifier.fillMaxWidth().padding(top = 92.dp)) {
        Row(Modifier.fillMaxWidth().height(14.dp).padding(horizontal = 8.dp).clip(RoundedCornerShape(7.dp))) {
            Box(Modifier.weight(animatedShare.coerceIn(0.02f, 0.98f)).fillMaxHeight().background(Brand.pkA))
            Box(Modifier.weight((1f - animatedShare).coerceIn(0.02f, 0.98f)).fillMaxHeight().background(Brand.pkB))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(Money.grouped(ourScore), color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                if (pk.status == "running") PkMath.clock(seconds) else stringResource(R.string.pk_waiting),
                color = Brand.Gold, fontWeight = FontWeight.Black,
            )
            Text(Money.grouped(theirScore), color = Color.White, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth().aspectRatio(1.5f)) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when {
                    hostParticipant == null -> AvatarPlaceholder(null, Modifier.fillMaxSize())
                    hostParticipant.userId == myUserId -> VideoSurface(engine, 0, true, Modifier.fillMaxSize())
                    hostParticipant.rtcUid in remoteUids -> VideoSurface(engine, hostParticipant.rtcUid, false, Modifier.fillMaxSize())
                    else -> AvatarPlaceholder(hostParticipant, Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.size(2.dp))
            Box(Modifier.weight(1f).fillMaxHeight().background(Brand.NightRaised), contentAlignment = Alignment.Center) {
                if (opponentUid != null) VideoSurface(engine, opponentUid, false, Modifier.fillMaxSize())
                else Text(stringResource(R.string.pk_connecting), color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}
