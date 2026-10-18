package et.ethiolive.app.stream

import android.content.Context
import android.view.TextureView
import android.view.View
import et.ethiolive.core.StreamQuality
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.video.BeautyOptions
import io.agora.rtc2.video.ChannelMediaInfo
import io.agora.rtc2.video.ChannelMediaRelayConfiguration
import io.agora.rtc2.video.VideoCanvas
import io.agora.rtc2.video.VideoEncoderConfiguration
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Agora RTC (live broadcasting profile). Created lazily on first use and destroyed when the app is torn down. */
class AgoraStreamEngine(private val context: Context, private val appId: String) : StreamEngine {
    private val _connection = MutableStateFlow(ConnectionState.IDLE)
    override val connection = _connection.asStateFlow()
    private val _signals = MutableSharedFlow<StreamSignal>(extraBufferCapacity = 64)
    override val signals = _signals.asSharedFlow()
    private val _speaking = MutableStateFlow<Set<Int>>(emptySet())
    override val speaking = _speaking.asStateFlow()
    override val isAvailable = true

    private val views = HashMap<Int, TextureView>()
    private var localUid = 0
    private var engine: RtcEngine? = null

    private val handler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            localUid = uid
            _connection.value = ConnectionState.CONNECTED
            _signals.tryEmit(StreamSignal.Joined(uid))
        }

        override fun onUserJoined(uid: Int, elapsed: Int) { _signals.tryEmit(StreamSignal.RemoteJoined(uid)) }
        override fun onUserOffline(uid: Int, reason: Int) { _signals.tryEmit(StreamSignal.RemoteLeft(uid)) }

        override fun onRemoteVideoStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
            val on = state == Constants.REMOTE_VIDEO_STATE_DECODING || state == Constants.REMOTE_VIDEO_STATE_STARTING
            if (state != Constants.REMOTE_VIDEO_STATE_FROZEN) _signals.tryEmit(StreamSignal.RemoteVideo(uid, on))
        }

        override fun onConnectionStateChanged(state: Int, reason: Int) {
            _connection.value = when (state) {
                Constants.CONNECTION_STATE_CONNECTING -> ConnectionState.CONNECTING
                Constants.CONNECTION_STATE_CONNECTED -> ConnectionState.CONNECTED
                Constants.CONNECTION_STATE_RECONNECTING -> ConnectionState.RECONNECTING
                Constants.CONNECTION_STATE_FAILED -> ConnectionState.FAILED
                else -> ConnectionState.DISCONNECTED
            }
        }

        override fun onTokenPrivilegeWillExpire(token: String?) { _signals.tryEmit(StreamSignal.TokenExpiring) }
        override fun onRequestToken() { _signals.tryEmit(StreamSignal.TokenExpiring) }

        override fun onNetworkQuality(uid: Int, txQuality: Int, rxQuality: Int) {
            if (uid == 0) _signals.tryEmit(StreamSignal.NetworkQuality(maxOf(txQuality, rxQuality)))
        }

        override fun onLocalVideoStateChanged(source: Constants.VideoSourceType?, state: Int, reason: Int) {
            if (state == Constants.LOCAL_VIDEO_STREAM_STATE_FAILED) _signals.tryEmit(StreamSignal.LocalPublishFailed("video_$reason"))
        }

        override fun onLocalAudioStateChanged(state: Int, reason: Int) {
            if (state == Constants.LOCAL_AUDIO_STREAM_STATE_FAILED) _signals.tryEmit(StreamSignal.LocalPublishFailed("audio_$reason"))
        }

        override fun onAudioVolumeIndication(speakers: Array<out AudioVolumeInfo>?, totalVolume: Int) {
            val active = speakers.orEmpty().filter { it.volume > 25 }.map { if (it.uid == 0) localUid else it.uid }.toSet()
            _speaking.value = active
        }

        override fun onChannelMediaRelayStateChanged(state: Int, code: Int) {
            _signals.tryEmit(
                StreamSignal.RelayState(
                    running = state == Constants.RELAY_STATE_RUNNING,
                    failed = state == Constants.RELAY_STATE_FAILURE,
                ),
            )
        }

        override fun onError(err: Int) { _signals.tryEmit(StreamSignal.Error(err)) }
    }

    private fun engine(): RtcEngine = engine ?: RtcEngine.create(
        RtcEngineConfig().apply {
            mContext = context.applicationContext
            mAppId = appId
            mEventHandler = handler
            mChannelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING
        },
    ).also {
        it.enableAudioVolumeIndication(400, 3, false)
        engine = it
    }

    private fun applyQuality(e: RtcEngine, q: StreamQuality) {
        e.setVideoEncoderConfiguration(
            VideoEncoderConfiguration(
                VideoEncoderConfiguration.VideoDimensions(q.width, q.height),
                when (q.fps) {
                    24 -> VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_24
                    else -> VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15
                },
                q.bitrateKbps,
                VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_FIXED_PORTRAIT,
            ),
        )
    }

    override fun startPreview(audioOnly: Boolean, quality: StreamQuality) {
        val e = engine()
        e.enableAudio()
        if (audioOnly) {
            e.disableVideo()
        } else {
            e.enableVideo()
            applyQuality(e, quality)
            e.startPreview()
        }
    }

    private fun options(publisher: Boolean, audioOnly: Boolean) = ChannelMediaOptions().apply {
        channelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING
        clientRoleType = if (publisher) Constants.CLIENT_ROLE_BROADCASTER else Constants.CLIENT_ROLE_AUDIENCE
        audienceLatencyLevel = Constants.AUDIENCE_LATENCY_LEVEL_LOW_LATENCY
        publishMicrophoneTrack = publisher
        publishCameraTrack = publisher && !audioOnly
        autoSubscribeAudio = true
        autoSubscribeVideo = !audioOnly
    }

    override fun join(params: JoinParams) {
        val e = engine()
        e.enableAudio()
        if (params.audioOnly) e.disableVideo() else {
            e.enableVideo()
            applyQuality(e, params.quality)
        }
        _connection.value = ConnectionState.CONNECTING
        val code = e.joinChannel(params.token, params.channel, params.uid, options(params.publisher, params.audioOnly))
        if (code < 0) {
            _connection.value = ConnectionState.FAILED
            _signals.tryEmit(StreamSignal.Error(code))
        }
    }

    override fun setPublisher(publisher: Boolean, audioOnly: Boolean, token: String?) {
        val e = engine
        if (e == null) return
        if (publisher && !audioOnly) {
            e.enableVideo()
            e.startPreview()
        }
        val opts = options(publisher, audioOnly)
        if (token != null) opts.token = token
        e.updateChannelMediaOptions(opts)
    }

    override fun renewToken(token: String) { engine?.renewToken(token) }

    override fun leave() {
        engine?.let {
            it.stopChannelMediaRelay()
            it.stopPreview()
            it.leaveChannel()
        }
        views.clear()
        _speaking.value = emptySet()
        _connection.value = ConnectionState.IDLE
    }

    override fun setMicMuted(muted: Boolean) { engine?.muteLocalAudioStream(muted) }

    override fun setCameraOff(off: Boolean) {
        engine?.let {
            it.muteLocalVideoStream(off)
            it.enableLocalVideo(!off)
        }
    }

    override fun switchCamera() { engine?.switchCamera() }

    override fun setBeauty(enabled: Boolean) {
        engine?.setBeautyEffectOptions(
            enabled,
            BeautyOptions().apply {
                lighteningContrastLevel = BeautyOptions.LIGHTENING_CONTRAST_NORMAL
                lighteningLevel = 0.4f
                smoothnessLevel = 0.5f
                rednessLevel = 0.1f
                sharpnessLevel = 0.3f
            },
        )
    }

    override fun setQuality(quality: StreamQuality) { engine?.let { applyQuality(it, quality) } }

    override fun createVideoView(uid: Int, local: Boolean): View {
        val e = engine()
        val view = views.getOrPut(uid) { TextureView(context) }
        if (local) e.setupLocalVideo(VideoCanvas(view, VideoCanvas.RENDER_MODE_HIDDEN, 0))
        else e.setupRemoteVideo(VideoCanvas(view, VideoCanvas.RENDER_MODE_HIDDEN, uid))
        return view
    }

    override fun releaseVideoView(uid: Int) {
        views.remove(uid)
        val e = engine ?: return
        if (uid == localUid) e.setupLocalVideo(VideoCanvas(null, VideoCanvas.RENDER_MODE_HIDDEN, 0))
        else e.setupRemoteVideo(VideoCanvas(null, VideoCanvas.RENDER_MODE_HIDDEN, uid))
    }

    override fun startRelay(sourceToken: String, destChannel: String, destToken: String, uid: Int) {
        val e = engine ?: return
        val cfg = ChannelMediaRelayConfiguration()
        cfg.setSrcChannelInfo(ChannelMediaInfo(null, sourceToken, 0))
        cfg.setDestChannelInfo(destChannel, ChannelMediaInfo(destChannel, destToken, uid))
        e.startOrUpdateChannelMediaRelay(cfg)
    }

    override fun stopRelay() { engine?.stopChannelMediaRelay() }

    override fun destroy() {
        leave()
        engine = null
        RtcEngine.destroy()
    }
}
