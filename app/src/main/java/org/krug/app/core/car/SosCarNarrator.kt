package org.krug.app.core.car

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.google.firebase.auth.FirebaseAuth
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.krug.app.core.circle.CircleRepository
import org.krug.app.core.sos.SosRepository
import timber.log.Timber

/**
 * Emituje TTS govor kad neko iz kruga trigger-uje SOS dok je user u Auto-u.
 *
 * Filter: notifie se samo za SOS koji su noviji od `startedAtMs` (start of session),
 * inače restart Auto-a bi ponovo pročitao stare SOS-eve.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SosCarNarrator(
    private val context: Context,
    private val auth: FirebaseAuth,
    private val circleRepository: CircleRepository,
    private val sosRepository: SosRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var tts: TextToSpeech? = null
    private val startedAtMs = System.currentTimeMillis()
    private val notifiedUids = java.util.Collections.synchronizedSet(HashSet<String>())

    fun start() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Srpski nije uvek dostupan; fallback na system default.
                val localeSrLatn = Locale.forLanguageTag("sr-Latn")
                val supported = tts?.isLanguageAvailable(localeSrLatn)
                if (supported == TextToSpeech.LANG_AVAILABLE ||
                    supported == TextToSpeech.LANG_COUNTRY_AVAILABLE
                ) {
                    tts?.language = localeSrLatn
                } else {
                    tts?.language = Locale.getDefault()
                }
                tts?.setSpeechRate(0.95f)
                Timber.d("SosCarNarrator: TTS ready")
            } else {
                Timber.w("SosCarNarrator: TTS init failed status=%d", status)
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {}
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Timber.w("SosCarNarrator: TTS onError uid=%s", utteranceId)
            }
        })
        val selfUid = auth.currentUser?.uid ?: return
        job = scope.launch {
            circleRepository.observeMyCircles(selfUid)
                .flatMapLatest { circles ->
                    val myCircleIds = circles.map { it.id }.toSet()
                    val others = circles.flatMap { it.memberIds }.toSet() - selfUid
                    if (others.isEmpty()) flowOf(myCircleIds to emptyList())
                    else combine(
                        others.map { uid ->
                            sosRepository.observe(uid).map { sos -> uid to sos }
                        },
                    ) { arr -> myCircleIds to arr.toList() }
                }
                .collectLatest { (myCircleIds, pairs) ->
                    pairs.forEach { (uid, sos) ->
                        if (sos == null) return@forEach
                        if (sos.triggeredAt <= startedAtMs) return@forEach
                        if (sos.circleId != null && sos.circleId !in myCircleIds) return@forEach
                        if (uid in notifiedUids) return@forEach
                        notifiedUids.add(uid)
                        val name = sos.senderName?.takeIf { it.isNotBlank() } ?: "Član"
                        speak(name)
                    }
                }
        }
    }

    fun stop() {
        job?.cancel()
        tts?.stop()
        tts?.shutdown()
        tts = null
        scope.cancel()
    }

    private fun speak(name: String) {
        val text = "Pažnja. $name traži pomoć. SOS aktiviran."
        Timber.i("SosCarNarrator: speaking '%s'", text)
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "sos-$name-${System.currentTimeMillis()}")
    }
}
