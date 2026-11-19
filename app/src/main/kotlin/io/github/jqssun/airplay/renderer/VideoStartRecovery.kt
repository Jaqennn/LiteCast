package io.github.jqssun.airplay.renderer

/** Caller serializes all methods and scheduled tasks with the renderer lock. */
internal class VideoStartRecovery<T>(
    private val schedule: (Long, () -> Unit) -> Unit,
    private val recover: (List<T>) -> Unit,
    private val failedAttempt: () -> Unit,
    private val exhausted: () -> Unit,
    private val maxBytes: Int = 4 * 1024 * 1024,
) {
    private val frames = ArrayList<T>()
    private var bytes = 0
    private var generation = 0L
    private var attempts = 0
    var pending = false; private set

    // The first item must contain a keyframe/configuration; retain it for retries
    // even when the sender sends no new frames while its screen is static.
    fun begin(frame: T, size: Int) {
        cancel()
        pending = true
        if (append(frame, size)) scheduleNext(generation)
    }

    fun append(frame: T, size: Int): Boolean {
        if (!pending) return false
        if (size > maxBytes - bytes || frames.size >= 180) {
            cancel()
            exhausted()
            return false
        }
        frames.add(frame)
        bytes += size
        return true
    }

    fun cancel() {
        generation++
        pending = false
        frames.clear()
        bytes = 0
        attempts = 0
    }

    private fun scheduleNext(token: Long) {
        schedule(300L) {
            if (token == generation && pending) {
                attempts++
                try {
                    recover(frames.toList())
                    cancel()
                } catch (_: Exception) {
                    failedAttempt()
                    if (attempts < 3) scheduleNext(token)
                    else {
                        cancel()
                        exhausted()
                    }
                }
            }
        }
    }
}
