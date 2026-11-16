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
    std::cout << "Timeline flush: stale audio discarded, new audio retained, continuous playback preserved\n";
}
