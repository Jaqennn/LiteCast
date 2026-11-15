#include "android_jni_thread.h"
#include <pthread.h>

static pthread_key_t vm_key;
static pthread_once_t key_once = PTHREAD_ONCE_INIT;
static int key_error;
/* Store the VM, never a session pointer: the session may be destroyed before TLS. */
static void detach(void *value) {
    JavaVM *vm = value;
    (*vm)->DetachCurrentThread(vm);
}
static void create_key(void) { key_error = pthread_key_create(&vm_key, detach); }
JNIEnv *litecast_jni_env(JavaVM *vm) {
    if (!vm) return NULL;
    JNIEnv *env = NULL;
    jint status = (*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6);
    if (status == JNI_OK) return env; /* Java-owned threads remain attached. */
    if (status != JNI_EDETACHED) return NULL;
    if (pthread_once(&key_once, create_key) != 0 || key_error != 0) return NULL;
#ifdef __ANDROID__
    if ((*vm)->AttachCurrentThread(vm, &env, NULL) != JNI_OK) return NULL;
#else
    if ((*vm)->AttachCurrentThread(vm, (void **)&env, NULL) != JNI_OK) return NULL;
#endif
    if (pthread_setspecific(vm_key, vm) != 0) {
        (*vm)->DetachCurrentThread(vm);
        return NULL;
    }
    return env;
}
