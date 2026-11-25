#ifndef LOG_SINK_H
#define LOG_SINK_H

#include <android/log.h>
#include <jni.h>
#include <cstdarg>
#include <cstdio>

#define LOG_SINK_TAG "AirPlayNative"

/*
 * log sink writing to both app log UI and logcat. any thread may emit, but emitting can
 * be slow (JNI call)
 */
class LogSink {
public:
    // bind to Kotlin handler with `void onLog(String)` (bridge.LogListener)
    void bind(JNIEnv *env, jobject handler) {
        if (!env || !handler) return;
        env->GetJavaVM(&mVm);
        mObj = env->NewGlobalRef(handler);
        jclass cls = env->GetObjectClass(handler);
        mMethod = cls ? env->GetMethodID(cls, "onLog", "(Ljava/lang/String;)V") : nullptr;
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            mMethod = nullptr;
        }
        if (cls) env->DeleteLocalRef(cls);
    }

    // releases handler global ref; thread safe
    ~LogSink() {
        if (!mVm || !mObj) return;
        JNIEnv *env = nullptr;
        bool attached = false;
        jint st = mVm->GetEnv((void **)&env, JNI_VERSION_1_6);
        if (st == JNI_EDETACHED) {
            if (mVm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
            attached = true;
        } else if (st != JNI_OK || !env) {
            return;
        }
        env->DeleteGlobalRef(mObj);
        if (attached) mVm->DetachCurrentThread();
    }

    // log level only affects logcat, UI ignores it
    void info(const char *fmt, ...)  __attribute__((format(printf, 2, 3))) {
        va_list ap; va_start(ap, fmt); vemit(ANDROID_LOG_INFO, fmt, ap); va_end(ap);
    }
    void warn(const char *fmt, ...)  __attribute__((format(printf, 2, 3))) {
        va_list ap; va_start(ap, fmt); vemit(ANDROID_LOG_WARN, fmt, ap); va_end(ap);
    }
    void error(const char *fmt, ...) __attribute__((format(printf, 2, 3))) {
        va_list ap; va_start(ap, fmt); vemit(ANDROID_LOG_ERROR, fmt, ap); va_end(ap);
    }

private:
    void vemit(int prio, const char *fmt, va_list ap) {
        char buf[256];
        vsnprintf(buf, sizeof(buf), fmt, ap);
        __android_log_print(prio, LOG_SINK_TAG, "%s", buf);
        if (!mVm || !mObj || !mMethod) return;

        JNIEnv *env = nullptr;
        bool attached = false;
        jint st = mVm->GetEnv((void **)&env, JNI_VERSION_1_6);
        if (st == JNI_EDETACHED) {
            if (mVm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
            attached = true;
        } else if (st != JNI_OK || !env) {
            return;
        }
        if (env->ExceptionCheck()) {
            if (attached) mVm->DetachCurrentThread();
            return;
        }
        jstring s = env->NewStringUTF(buf);
        if (s) {
            env->CallVoidMethod(mObj, mMethod, s);
            env->DeleteLocalRef(s);
        }
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        if (attached) mVm->DetachCurrentThread();
    }

    JavaVM *mVm = nullptr;
    jobject mObj = nullptr;      // global ref to JNI handler
    jmethodID mMethod = nullptr; // onLog(String)
};

#endif  // LOG_SINK_H
