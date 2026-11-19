#include "android_jni_thread.h"
#include <assert.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdio.h>
static atomic_int attached, detached;
static _Thread_local int present;
static int fail_attach;
static JNIEnv *fake_env = (JNIEnv *)0x1000;
static jint get_env(JavaVM *vm, void **env, jint version) {
    *env = present ? fake_env : NULL;
    return present ? JNI_OK : JNI_EDETACHED;
}
static jint attach(JavaVM *vm, void **env, void *args) {
    if (fail_attach) return JNI_ERR;
    present = 1; *env = fake_env; atomic_fetch_add(&attached, 1); return JNI_OK;
}
static jint detach(JavaVM *vm) {
    assert(present); present = 0; atomic_fetch_add(&detached, 1); return JNI_OK;
}
static const struct JNIInvokeInterface_ functions = {
    .GetEnv = get_env, .AttachCurrentThread = attach, .DetachCurrentThread = detach
};
static JavaVM vm = &functions;
static void *worker(void *unused) {
    assert(litecast_jni_env(&vm) == (fail_attach ? NULL : fake_env));
    assert(litecast_jni_env(&vm) == (fail_attach ? NULL : fake_env));
    return NULL;
}
int main(void) {
    pthread_t threads[16];
    for (int round = 0; round < 16; ++round) {
        for (int i = 0; i < 16; ++i) assert(!pthread_create(&threads[i], NULL, worker, NULL));
        for (int i = 0; i < 16; ++i) assert(!pthread_join(threads[i], NULL));
    }
    assert(attached == 256 && detached == 256);
    present = 1; /* Java-owned thread: no ownership transfer. */
    assert(litecast_jni_env(&vm) == fake_env);
    assert(attached == 256 && detached == 256);
    fail_attach = 1;
    assert(!pthread_create(&threads[0], NULL, worker, NULL));
    assert(!pthread_join(threads[0], NULL));
    assert(attached == 256 && detached == 256);
    assert(litecast_jni_env(NULL) == NULL);
    puts("PASS: 256 native threads attach/detach exactly once; Java-owned and failed attachment paths remain safe");
}
