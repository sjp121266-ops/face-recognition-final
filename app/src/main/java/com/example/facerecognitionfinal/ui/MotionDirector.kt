package com.example.facerecognitionfinal.ui

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView

class MotionDirector(private val context: Context) {

    private var entranceSet: AnimatorSet? = null
    private var fullScreenSet: AnimatorSet? = null

    private val reduceMotion: Boolean
        get() = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)

    fun playMainEntrance(header: View, vararg cards: View) {
        entranceSet?.cancel()
        if (reduceMotion) {
            (listOf(header) + cards).forEach { resetFinalState(it) }
            return
        }

        val animations = mutableListOf<Animator>()
        animations += reveal(header, fromY = -18f, delay = 40L, duration = 420L)
        cards.forEachIndexed { index, view ->
            animations += reveal(
                view = view,
                fromY = 26f,
                delay = 120L + index * 70L,
                duration = 460L
            )
        }
        entranceSet = AnimatorSet().apply {
            playTogether(animations)
            start()
        }
    }

    fun bindButtonFeedback(vararg buttons: View) {
        buttons.forEach { button ->
            button.setOnTouchListener { view, event ->
                if (reduceMotion || !view.isEnabled) return@setOnTouchListener false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> view.animate()
                        .scaleX(0.975f)
                        .scaleY(0.975f)
                        .setDuration(90L)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(180L)
                        .setInterpolator(OvershootInterpolator(1.8f))
                        .start()
                }
                false
            }
        }
    }

    fun animateStatusChange(textView: TextView, text: String) {
        if (textView.text == text) return
        textView.animate().cancel()
        if (reduceMotion) {
            textView.text = text
            textView.alpha = 1f
            textView.translationY = 0f
            return
        }

        textView.animate()
            .alpha(0f)
            .translationY(10f)
            .setDuration(130L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                textView.text = text
                textView.translationY = -8f
                textView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(240L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            .start()
    }

    fun transitionPages(outgoing: View, incoming: View, incomingFromRight: Boolean, onShown: () -> Unit) {
        if (outgoing == incoming) {
            onShown()
            return
        }
        outgoing.animate().cancel()
        incoming.animate().cancel()
        if (reduceMotion) {
            outgoing.visibility = View.GONE
            incoming.visibility = View.VISIBLE
            incoming.alpha = 1f
            incoming.translationX = 0f
            onShown()
            return
        }

        val direction = if (incomingFromRight) 1f else -1f
        incoming.visibility = View.VISIBLE
        incoming.alpha = 0f
        incoming.translationX = 42f * direction
        outgoing.animate()
            .alpha(0f)
            .translationX(-24f * direction)
            .setDuration(170L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                outgoing.visibility = View.GONE
                outgoing.alpha = 1f
                outgoing.translationX = 0f
                incoming.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(260L)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction { onShown() }
                    .start()
            }
            .start()
    }

    fun setCloudConfigVisible(view: View, visible: Boolean) {
        view.animate().cancel()
        if (reduceMotion) {
            view.visibility = if (visible) View.VISIBLE else View.GONE
            view.alpha = 1f
            view.translationY = 0f
            return
        }
        if (visible) {
            if (view.visibility == View.VISIBLE) return
            view.visibility = View.VISIBLE
            view.alpha = 0f
            view.translationY = -12f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(260L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            if (view.visibility != View.VISIBLE) return
            view.animate()
                .alpha(0f)
                .translationY(-10f)
                .setDuration(160L)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    view.visibility = View.GONE
                    view.alpha = 1f
                    view.translationY = 0f
                }
                .start()
        }
    }

    fun enterFullScreen(root: View, preview: View, title: View, panel: View) {
        fullScreenSet?.cancel()
        if (reduceMotion) {
            root.visibility = View.VISIBLE
            listOf(root, preview, title, panel).forEach { resetFinalState(it) }
            return
        }
        root.animate().cancel()
        root.visibility = View.VISIBLE
        root.alpha = 0f
        preview.scaleX = 1.035f
        preview.scaleY = 1.035f
        title.alpha = 0f
        title.translationY = -28f
        panel.alpha = 0f
        panel.translationY = 36f

        fullScreenSet = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(root, View.ALPHA, 0f, 1f).setDuration(180L),
                ObjectAnimator.ofFloat(preview, View.SCALE_X, 1.035f, 1f).setDuration(520L),
                ObjectAnimator.ofFloat(preview, View.SCALE_Y, 1.035f, 1f).setDuration(520L),
                reveal(title, fromY = -28f, delay = 130L, duration = 340L),
                reveal(panel, fromY = 36f, delay = 230L, duration = 380L)
            )
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    fun exitFullScreen(root: View, title: View, panel: View, onHidden: () -> Unit) {
        fullScreenSet?.cancel()
        if (reduceMotion) {
            root.visibility = View.GONE
            onHidden()
            return
        }
        title.animate().cancel()
        panel.animate().cancel()
        root.animate().cancel()
        title.animate().alpha(0f).translationY(-16f).setDuration(140L).start()
        panel.animate().alpha(0f).translationY(24f).setDuration(140L).start()
        root.animate()
            .alpha(0f)
            .setDuration(210L)
            .setStartDelay(70L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                root.visibility = View.GONE
                root.alpha = 1f
                title.alpha = 1f
                title.translationY = 0f
                panel.alpha = 1f
                panel.translationY = 0f
                onHidden()
            }
            .start()
    }

    private fun reveal(view: View, fromY: Float, delay: Long, duration: Long): AnimatorSet {
        view.alpha = 0f
        view.translationY = fromY
        return AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, fromY, 0f)
            )
            startDelay = delay
            this.duration = duration
            interpolator = DecelerateInterpolator()
        }
    }

    private fun resetFinalState(view: View) {
        view.visibility = View.VISIBLE
        view.alpha = 1f
        view.translationX = 0f
        view.translationY = 0f
        view.scaleX = 1f
        view.scaleY = 1f
    }

    fun cancelAll() {
        entranceSet?.cancel()
        fullScreenSet?.cancel()
    }
}
