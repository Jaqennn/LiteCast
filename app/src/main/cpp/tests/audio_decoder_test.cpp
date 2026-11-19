// Debug APK only. Exercise the production decoder with known AAC packets.
#include "audio_decoder.h"
#include <vector>

extern "C" JNIEXPORT jint JNICALL
Java_io_github_litecast_AudioDecodeTest_decodePackets(JNIEnv *env, jobject, jint ct, jint spf,
                                                    jobjectArray packets) {
    TimelineBuffer timeline(44100, 2, 1, 95);
    LogSink log;
    LatencyReporter latency("test", log);
    auto decoder = makeDecoder(ct, spf, 44100, 2, timeline, latency, log, true, false, false);
    if (!decoder) return -1;
    int nonzero = 0;
    std::vector<int16_t> pcm(4096);
    const jsize count = env->GetArrayLength(packets);
    for (jsize i = 0; i < count; ++i) {
        auto packet = (jbyteArray)env->GetObjectArrayElement(packets, i);
        const jsize len = env->GetArrayLength(packet);
        std::vector<uint8_t> data(len);
        env->GetByteArrayRegion(packet, 0, len, (jbyte *)data.data());
        env->DeleteLocalRef(packet);
        decoder->decode(data.data(), data.size(), 0);
        timeline.read(pcm.data(), 2048);
        for (auto sample : pcm) if (sample != 0) ++nonzero;
    }
    return nonzero;
}
