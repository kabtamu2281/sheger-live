package et.ethiolive.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import et.ethiolive.app.AppContainer
import et.ethiolive.app.R
import et.ethiolive.app.data.BackendConfig
import et.ethiolive.app.ui.components.Chip
import et.ethiolive.app.ui.components.NotConfiguredNotice
import et.ethiolive.app.ui.components.PrimaryButton
import et.ethiolive.app.ui.components.errorMessage
import et.ethiolive.app.ui.components.loadScaledJpeg
import et.ethiolive.app.ui.nav.Nav
import et.ethiolive.app.ui.nav.Routes
import et.ethiolive.app.ui.theme.Brand
import et.ethiolive.core.AppLanguage
import et.ethiolive.core.RoomMode
import et.ethiolive.core.SeatLayout
import et.ethiolive.core.Validation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun GoLiveScreen(container: AppContainer, nav: Nav) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val account by container.account.collectAsState()
    var mode by rememberSaveable { mutableStateOf(RoomMode.VIDEO) }
    var title by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("chat") }
    var language by rememberSaveable { mutableStateOf(currentLanguage().tag) }
    var followersOnly by rememberSaveable { mutableStateOf(false) }
    var hashtags by rememberSaveable { mutableStateOf("") }
    var seats by rememberSaveable { mutableStateOf(1) }
    var coverUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }
    val audio = mode == RoomMode.AUDIO
    val adult = account?.private?.isAdult == true

    val choices = SeatLayout.choicesFor(mode)
    if (seats !in choices) seats = choices.first()

    val needed = if (audio) arrayOf(Manifest.permission.RECORD_AUDIO) else arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
    fun hasPermissions() = needed.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    var previewOn by remember { mutableStateOf(false) }

    // Local camera preview so the host can frame themselves before going live (nothing is published yet).
    DisposableEffect(mode, previewOn) {
        if (previewOn && !audio && BackendConfig.streamingConfigured) {
            scope.launch {
                try {
                    container.stream.startPreview(false, et.ethiolive.core.QualityAdapter.initial(container.prefs.prefs.first().quality))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error = e
                    previewOn = false
                }
            }
        }
        onDispose { }
    }
    var wentLive by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { if (!wentLive) container.stream.leave() } }

    fun start() {
        scope.launch {
            busy = true; error = null
            try {
                val cover = coverUri?.let { uri -> loadScaledJpeg(context, uri, 720)?.let { container.profiles.uploadCover(it) } }
                val room = container.live.startLive(
                    mode = mode.wire, title = title.trim(), category = category, language = language,
                    audience = if (followersOnly) "followers" else "public",
                    hashtags = Validation.hashtags(hashtags), maxSeats = seats, coverUrl = cover,
                )
                wentLive = true
                nav.replaceWithRoom(room.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e
            }
            busy = false
        }
    }

    // Once Android stops showing the permission dialog ("don't ask again"), only app settings can grant it.
    var permissionBlocked by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permissionDenied = !result.values.all { it }
        if (!permissionDenied) previewOn = true
        val activity = context as? android.app.Activity
        permissionBlocked = permissionDenied && activity != null && result.filterValues { !it }.keys
            .none { androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) coverUri = uri }

    Box(Modifier.fillMaxSize().background(Brand.Night)) {
        if (previewOn && !audio && BackendConfig.streamingConfigured) {
            VideoSurface(container.stream, 0, true, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color(0x66000000)))
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.go_live_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { nav.to(Routes.STREAM_SETTINGS) }) { Icon(Icons.Default.Tune, stringResource(R.string.stream_settings)) }
            }
            if (!BackendConfig.streamingConfigured) {
                NotConfiguredNotice(R.string.streaming_not_configured_body)
                Spacer(Modifier.height(12.dp))
            }
            if (!adult) {
                NotConfiguredNotice(R.string.err_broadcast_age_restricted)
                Spacer(Modifier.height(12.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeTile(Icons.Default.Videocam, stringResource(R.string.mode_video), mode == RoomMode.VIDEO, Modifier.weight(1f)) { mode = RoomMode.VIDEO }
                ModeTile(Icons.Default.Groups, stringResource(R.string.mode_multi), mode == RoomMode.MULTI_GUEST, Modifier.weight(1f)) { mode = RoomMode.MULTI_GUEST }
                ModeTile(Icons.Default.GraphicEq, stringResource(R.string.mode_audio), mode == RoomMode.AUDIO, Modifier.weight(1f)) { mode = RoomMode.AUDIO }
                ModeTile(Icons.Default.SportsEsports, stringResource(R.string.mode_gaming), mode == RoomMode.GAMING, Modifier.weight(1f)) { mode = RoomMode.GAMING }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(onClick = { coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(84.dp)) {
                    if (coverUri != null) AsyncImage(coverUri, stringResource(R.string.cover_image), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.AddPhotoAlternate, stringResource(R.string.cover_image)) }
                }
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(
                    value = title, onValueChange = { title = it.take(Validation.TITLE_MAX) },
                    label = { Text(stringResource(R.string.live_title)) }, singleLine = false, maxLines = 2,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.category), style = MaterialTheme.typography.labelLarge)
            LazyRow(contentPadding = PaddingValues(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CATEGORIES) { c -> Chip(categoryLabel(c), category == c, { category = c }) }
            }
            Text(stringResource(R.string.stream_language), style = MaterialTheme.typography.labelLarge)
            LazyRow(contentPadding = PaddingValues(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AppLanguage.entries) { l -> Chip(l.nativeName, language == l.tag, { language = l.tag }) }
            }
            if (choices.size > 1) {
                Text(stringResource(R.string.guest_seats), style = MaterialTheme.typography.labelLarge)
                LazyRow(contentPadding = PaddingValues(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(choices) { n -> Chip(stringResource(R.string.seats_n, n), seats == n, { seats = n }) }
                }
            }
            OutlinedTextField(
                value = hashtags, onValueChange = { hashtags = it.take(80) },
                label = { Text(stringResource(R.string.hashtags)) }, placeholder = { Text("#buna #addis") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(stringResource(R.string.audience_public), !followersOnly, { followersOnly = false })
                Chip(stringResource(R.string.audience_followers), followersOnly, { followersOnly = true })
            }
            Spacer(Modifier.height(16.dp))
            if (permissionDenied) Text(stringResource(R.string.permission_needed_live), color = MaterialTheme.colorScheme.error)
            error?.let { Text(errorMessage(it), color = MaterialTheme.colorScheme.error); Spacer(Modifier.height(8.dp)) }
            Text(stringResource(R.string.go_live_guidelines), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            if (!hasPermissions()) {
                if (permissionBlocked) {
                    PrimaryButton(stringResource(R.string.open_app_settings), onClick = {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(android.net.Uri.fromParts("package", context.packageName, null)),
                        )
                    })
                } else {
                    PrimaryButton(stringResource(R.string.allow_camera_mic), onClick = { permissionLauncher.launch(needed) })
                }
            } else {
                if (!previewOn && !audio) SecondaryButton(stringResource(R.string.preview_camera), onClick = { previewOn = true })
                Spacer(Modifier.height(8.dp))
                PrimaryButton(
                    stringResource(R.string.go_live_now), loading = busy,
                    enabled = adult && BackendConfig.streamingConfigured && title.isNotBlank(),
                    onClick = { start() },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ModeTile(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Column(Modifier.padding(vertical = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
