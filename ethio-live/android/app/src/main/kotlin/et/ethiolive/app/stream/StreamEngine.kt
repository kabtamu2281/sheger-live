package et.ethiolive.app.stream

import android.view.View
import et.ethiolive.core.StreamQuality
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

enum class ConnectionState { IDLE, CONNECTING, CONNECTED, RECONNECTING, FAILED, DISCONNECTED }

sealed interface StreamSignal {
    data class Joined(val uid: Int) : StreamSignal
    data class RemoteJoined(val uid: Int) : StreamSignal
    data class RemoteLeft(val uid: Int) : StreamSignal
    data class RemoteVideo(val uid: Int, val on: Boolean) : StreamSignal
    data object TokenExpiring : StreamSignal
    data class NetworkQuality(val score: Int) : StreamSignal
    data class LocalPublishFailed(val reason: String) : StreamSignal
    data class Error(val code: Int) : StreamSignal
    data class RelayState(val running: Boolean, val failed: Boolean) : StreamSignal
}

data class JoinParams(
    val appId: String,
    val token: String,
    val channel: String,
    val uid: Int,
    val publisher: Boolean,
    val audioOnly: Boolean,
    val quality: StreamQuality,
)

/**
 * Real-time media boundary. [AgoraStreamEngine] is the production implementation; [UnconfiguredStreamEngine]
 * is used when the build has no Agora App ID and fails every join with a clear error rather than faking video.
 */
interface StreamEngine {
    val connection: StateFlow<ConnectionState>
    val signals: SharedFlow<StreamSignal>
    /** uids currently speaking (volume above threshold); used for audio-room speaking rings. */
    val speaking: StateFlow<Set<Int>>
    val isAvailable: Boolean

    fun startPreview(audioOnly: Boolean, quality: StreamQuality)
    fun join(params: JoinParams)
    fun setPublisher(publisher: Boolean, audioOnly: Boolean, token: String?)
    fun renewToken(token: String)
    fun leave()
    fun setMicMuted(muted: Boolean)
    fun setCameraOff(off: Boolean)
    fun switchCamera()
    fun setBeauty(enabled: Boolean)
    fun setQuality(quality: StreamQuality)
    fun createVideoView(uid: Int, local: Boolean): View
    fun releaseVideoView(uid: Int)
    fun startRelay(sourceToken: String, destChannel: String, destToken: String, uid: Int)
    fun stopRelay()
    fun destroy()
}
