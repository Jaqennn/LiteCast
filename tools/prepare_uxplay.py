#!/usr/bin/env python3
"""Apply checked Android lifecycle changes to build-local copies of pinned UxPlay.
The upstream submodule stays untouched. Fail closed if upstream code changes.
"""
from pathlib import Path
import sys
source, target = map(Path, sys.argv[1:])
target.mkdir(parents=True, exist_ok=True)
def replace(text, old, new):
    if text.count(old) != 1:
        raise RuntimeError(f'Upstream patch context changed: {old[:80]}')
    return text.replace(old, new, 1)
r = (source / 'raop.c').read_text()
r = '#include "native_trace.h"\n' + r
r = replace(r, '    conn = calloc(1, sizeof(raop_conn_t));', '    litecast_trace("connection.init.begin");\n    conn = calloc(1, sizeof(raop_conn_t));')
r = replace(r, '    return conn;\n}', '    litecast_trace("connection.init.ready");\n    return conn;\n}')
r = replace(r, '    const char *protocol = http_request_get_protocol(request);', '    const char *protocol = http_request_get_protocol(request);\n    litecast_trace_request(method, url);')
r = replace(r, '    raop_conn_t *conn = ptr;\n    raop_t *raop = conn->raop;\n    logger_log(raop->logger, LOGGER_DEBUG, "Destroying connection");', '    litecast_trace("connection.destroy.begin");\n    raop_conn_t *conn = ptr;\n    raop_t *raop = conn->raop;\n    logger_log(raop->logger, LOGGER_DEBUG, "Destroying connection");')
r = replace(r, '    free(conn);\n}\n\nraop_t *', '    free(conn);\n    litecast_trace("connection.destroy.done");\n}\n\nraop_t *')
r = replace(r, '        raop_destroy_airplay_video(raop, -1);\n        raop_stop_httpd(raop);', '        /* Join request callbacks before freeing the sessions they access. */\n        raop_stop_httpd(raop);\n        raop_destroy_airplay_video(raop, -1);')
r = replace(r, '    httpd_stop(raop->httpd);', '    if (raop->httpd) httpd_stop(raop->httpd);')
(target / 'raop.c').write_text(r)
h = (source / 'httpd.c').read_text()
h = '#include "native_trace.h"\n' + h
h = replace(h, '    user_data = httpd->callbacks.conn_init(', '    litecast_trace("connection.accepted");\n    user_data = httpd->callbacks.conn_init(')
h = replace(h, '    if (!httpd->running || httpd->joined) {', '    /* A worker that exited itself still needs joining before freeing its owner. */\n    if (httpd->joined) {')
h = replace(h, '    THREAD_CREATE(httpd->thread, httpd_thread, httpd);', '''    if (pthread_create(&httpd->thread, NULL, httpd_thread, httpd) != 0) {
        httpd->running = 0;
        httpd->joined = 1;
        if (httpd->server_fd4 != -1) CLOSESOCKET(httpd->server_fd4);
        if (httpd->server_fd6 != -1) CLOSESOCKET(httpd->server_fd6);
        httpd->server_fd4 = httpd->server_fd6 = -1;
        MUTEX_UNLOCK(httpd->run_mutex);
        return -3;
    }''')
(target / 'httpd.c').write_text(h)
