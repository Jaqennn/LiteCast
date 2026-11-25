#include "timeline_buffer.h"
#include <cassert>
#include <algorithm>
#include <iostream>

int main() {
    TimelineBuffer timeline(1000, 1, 1, 95);
    int16_t oldAudio[100], newAudio[20], output[20];
    std::fill_n(oldAudio, 100, 111);
    std::fill_n(newAudio, 20, 222);
    timeline.write(oldAudio, 100, 1000000000LL);
    timeline.requestFlush();
    // New samples arrive before the consumer gets around to the flush.
    timeline.write(newAudio, 20, 5000000000LL);
    timeline.read(output, 20);
    for (auto sample : output) assert(sample == 222);

    // Repeated flushes preserve only samples after the newest boundary.
    timeline.write(oldAudio, 100, 6000000000LL);
    timeline.requestFlush();
    timeline.write(oldAudio, 100, 7000000000LL);
    timeline.requestFlush();
    timeline.write(newAudio, 20, 8000000000LL);
    timeline.read(output, 20);
    for (auto sample : output) assert(sample == 222);

    // Empty/partly consumed queues never rewind the read position.
    timeline.requestFlush();
    timeline.read(output, 20);
    for (auto sample : output) assert(sample == 0);
    timeline.write(newAudio, 20, 9000000000LL);
    timeline.read(output, 20);
    for (auto sample : output) assert(sample == 222);

    // No flush: ordinary continuous playback keeps the original ordering.
    timeline.write(oldAudio, 100, 9020000000LL);
    timeline.write(newAudio, 20, 9120000000LL);
    for (int i = 0; i < 5; ++i) {
        timeline.read(output, 20);
        for (auto sample : output) assert(sample == 111);
    }
    timeline.read(output, 20);
    for (auto sample : output) assert(sample == 222);

    // Regression: DelayTracker must clamp huge arrival-PTS deltas in 64-bit (no negative OOB write)
    // and underrun must reanchor DelayTracker so clock shifts don't poison mBaseNs.
    {
        TimelineBuffer adaptive(44100, 2, 0, 95);
        int16_t pcm[256] = {1};
        int16_t out[256] = {0};
        adaptive.write(pcm, 256, 1'000'000'000LL);
        // Trigger underrun
        adaptive.read(out, 128);
        adaptive.read(out, 128);
        // Huge clock jump (> 124 days, which previously wrapped int32 bin negative in observe())
        adaptive.write(pcm, 256, 1'795'000'000'000'000'000LL);
        // Direct DelayTracker check with > INT32_MAX * BUCKET_NS delta without reanchor
        DelayTracker tracker(44100, 2, 0, 95);
        tracker.observe(1'795'000'000'000'000'000LL, 1'000'000'000LL, 10'000'000LL);
        tracker.observe(1'000'000'000LL, 2'000'000'000LL, 10'000'000LL);
        assert(tracker.target() <= tracker.ceil());
    }

    std::cout << "Timeline flush: stale audio discarded, new audio retained, continuous playback preserved\n";
}
