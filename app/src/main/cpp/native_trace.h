#ifndef LITECAST_NATIVE_TRACE_H
#define LITECAST_NATIVE_TRACE_H
#ifdef __cplusplus
extern "C" {
#endif
void litecast_trace_open(const char *path);
void litecast_trace(const char *stage);
/* Rate-limited caller; numbers only; buffered write avoids fsync in decoding. */
void litecast_trace_audio(int ct, int spf, int backlog, int cushion, int mean_us,
                          int max_us, int errors, unsigned packets, long long pts_ms);
void litecast_trace_request(const char *method, const char *url);
#ifdef __cplusplus
}
#endif
#endif
