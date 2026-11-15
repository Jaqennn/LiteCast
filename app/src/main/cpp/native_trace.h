#ifndef LITECAST_NATIVE_TRACE_H
#define LITECAST_NATIVE_TRACE_H
#ifdef __cplusplus
extern "C" {
#endif
void litecast_trace_open(const char *path);
void litecast_trace(const char *stage);
void litecast_trace_request(const char *method, const char *url);
#ifdef __cplusplus
}
#endif
#endif
