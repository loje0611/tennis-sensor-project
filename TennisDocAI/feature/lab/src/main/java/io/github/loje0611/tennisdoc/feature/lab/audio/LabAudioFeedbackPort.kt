package io.github.loje0611.tennisdoc.feature.lab.audio

interface LabAudioFeedbackPort {
    val lastSpokenUtterance: String?
    fun speakCoaching(text: String)
    fun playImpactBeep()
    fun playCountdownTick(second: Int)
    fun playCountdownStart()
    fun release()
}

class DefaultLabAudioFeedbackPort : LabAudioFeedbackPort {
    private var _lastSpokenUtterance: String? = null
    override val lastSpokenUtterance: String? get() = _lastSpokenUtterance

    override fun speakCoaching(text: String) {
        _lastSpokenUtterance = text
    }

    override fun playImpactBeep() {}
    override fun playCountdownTick(second: Int) {}
    override fun playCountdownStart() {}
    override fun release() {
        _lastSpokenUtterance = null
    }
}
