#ifndef LITECAST_JNI_THREAD_H
#define LITECAST_JNI_THREAD_H
#include <jni.h>
#ifdef __cplusplus
extern "C" {
#endif
JNIEnv *litecast_jni_env(JavaVM *vm);
#ifdef __cplusplus
}
#endif
#endif
