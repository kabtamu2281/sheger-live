package et.ethiolive.app.stream

import android.content.Context
import android.view.View
import et.ethiolive.core.StreamQuality
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Used when no Agora App ID is configured. Never shows a fake stream; the UI explains streaming is not set up. */
class UnconfiguredStreamEngine(private val context: Context) : StreamEngine {
    override val connection: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.IDLE)
    private val _signals = MutableSharedFlow<StreamSignal>(extraBufferCapacity = 8)
    override val signals = _signals.asSharedFlow()
    override val speaking: StateFlow<Set<Int>> = MutableStateFlow(emptySet())
    override val isAvailable = false

    override fun startPreview(audioOnly: Boolean, quality: StreamQuality) = Unit
    override fun join(params: JoinParams) { (connection as MutableStateFlow).value = ConnectionState.FAILED }
    override fun setPublisher(publisher: Boolean, audioOnly: Boolean, token: String?) = Unit
    override fun renewToken(token: String) = Unit
    override fun leave() { (connection as MutableStateFlow).value = ConnectionState.IDLE }
    override fun setMicMuted(muted: Boolean) = Unit
    override fun setCameraOff(off: Boolean) = Unit
    override fun switchCamera() = Unit
    override fun setBeauty(enabled: Boolean) = Unit
    override fun setQuality(quality: StreamQuality) = Unit
    override fun createVideoView(uid: Int, local: Boolean): View = View(context)
    override fun releaseVideoView(uid: Int) = Unit
    override fun startRelay(sourceToken: String, destChannel: String, destToken: String, uid: Int) = Unit
    override fun stopRelay() = Unit
    override fun destroy() = Unit
}
