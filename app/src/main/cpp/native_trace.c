#include "native_trace.h"
#include <pthread.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <time.h>

static pthread_mutex_t trace_lock = PTHREAD_MUTEX_INITIALIZER;
static int trace_fd = -1;
static char previous[96];
void litecast_trace_open(const char *path) {
    pthread_mutex_lock(&trace_lock);
    if (trace_fd >= 0) close(trace_fd);
    trace_fd = open(path, O_WRONLY | O_CREAT | O_APPEND | O_CLOEXEC, 0600);
    previous[0] = 0;
    pthread_mutex_unlock(&trace_lock);
}
/* Fixed stage labels only: no URLs, addresses, keys, or sender-provided strings.
 * Written before risky operations, never from a signal handler. This is a bounded
 * breadcrumb trail, not proof that an interrupted session necessarily crashed. */
void litecast_trace(const char *stage) {
    pthread_mutex_lock(&trace_lock);
    if (trace_fd >= 0 && strcmp(previous, stage)) {
        struct stat info;
        if (fstat(trace_fd, &info) == 0 && info.st_size > 24000) ftruncate(trace_fd, 0);
        struct timespec now;
        clock_gettime(CLOCK_REALTIME, &now);
        char line[192];
        int size = snprintf(line, sizeof(line), "%lld.%03ld tid=%ld %s\n",
            (long long)now.tv_sec, now.tv_nsec / 1000000, (long)syscall(SYS_gettid), stage);
        if (size > 0 && size < (int)sizeof(line) && write(trace_fd, line, size) == size) {
            fsync(trace_fd);
            snprintf(previous, sizeof(previous), "%s", stage);
        }
    }
    pthread_mutex_unlock(&trace_lock);
}
void litecast_trace_audio(int ct, int spf, int backlog, int cushion, int mean_us,
                          int max_us, int errors, unsigned packets, long long pts_ms) {
    pthread_mutex_lock(&trace_lock);
    if (trace_fd >= 0) {
        struct stat info;
        if (fstat(trace_fd, &info) == 0 && info.st_size > 24000) ftruncate(trace_fd, 0);
        struct timespec now;
        clock_gettime(CLOCK_REALTIME, &now);
        char line[256];
        int n = snprintf(line, sizeof(line),
            "%lld.%03ld audio ct=%d spf=%d queue_ms=%d cushion_ms=%d decode_us=%d/%d errors=%d packets=%u pts_ms=%lld\n",
            (long long)now.tv_sec, now.tv_nsec / 1000000, ct, spf, backlog, cushion,
            mean_us, max_us, errors, packets, pts_ms);
        if (n > 0 && n < (int)sizeof(line)) (void)write(trace_fd, line, n);
    }
    pthread_mutex_unlock(&trace_lock);
}
void litecast_trace_request(const char *method, const char *url) {
    if (!method || !url) { litecast_trace("request.invalid"); return; }
    if (!strcmp(method, "SETUP")) litecast_trace("request.setup");
    else if (!strcmp(method, "TEARDOWN")) litecast_trace("request.teardown");
    else if (!strcmp(method, "RECORD")) litecast_trace("request.record");
    else if (!strncmp(url, "/info", 5)) litecast_trace("request.info");
    else if (!strcmp(url, "/server-info")) litecast_trace("request.server-info");
    else if (!strcmp(url, "/pair-setup")) litecast_trace("request.pair-setup");
    else if (!strcmp(url, "/pair-verify")) litecast_trace("request.pair-verify");
    else if (!strcmp(url, "/fp-setup")) litecast_trace("request.fp-setup");
    else if (!strcmp(url, "/play")) litecast_trace("request.play");
    else if (!strcmp(url, "/playback-info")) litecast_trace("request.playback-info");
    else if (!strcmp(url, "/stop")) litecast_trace("request.stop");
    else litecast_trace("request.other");
}
