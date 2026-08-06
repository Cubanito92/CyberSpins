#include <jni.h>
#include <string>
#include "AudioEngine.h"

static AudioEngine gAudioEngine;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_audio_NativeAudioEngine_nativeStartEngine(JNIEnv *env, jobject thiz) {
    return gAudioEngine.startRecordingAndPlay() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeStopEngine(JNIEnv *env, jobject thiz) {
    gAudioEngine.stopRecordingAndPlay();
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeSetMasterVolume(JNIEnv *env, jobject thiz, jfloat volume) {
    gAudioEngine.setMasterVolume(volume);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeSetMicGain(JNIEnv *env, jobject thiz, jfloat gain) {
    gAudioEngine.setMicGain(gain);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeSetMusicVolume(JNIEnv *env, jobject thiz, jfloat volume) {
    gAudioEngine.setMusicVolume(volume);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativePlaySoundEffect(JNIEnv *env, jobject thiz, jint effectId) {
    gAudioEngine.playSoundboardEffect(effectId);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_example_audio_NativeAudioEngine_nativeGetVuMeter(JNIEnv *env, jobject thiz) {
    return gAudioEngine.getPeakVuMeter();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_audio_NativeAudioEngine_nativeIsEngineLive(JNIEnv *env, jobject thiz) {
    return gAudioEngine.isLive() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_audio_NativeAudioEngine_nativeGetAudioApiName(JNIEnv *env, jobject thiz) {
    return env->NewStringUTF(gAudioEngine.getAudioApiName());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_audio_NativeAudioEngine_nativeConnectStream(
        JNIEnv *env, jobject thiz,
        jstring host_, jint port, jstring mount_, jstring pass_, jint bitrate) {
    const char *host = env->GetStringUTFChars(host_, nullptr);
    const char *mount = env->GetStringUTFChars(mount_, nullptr);
    const char *pass = env->GetStringUTFChars(pass_, nullptr);

    bool res = gAudioEngine.connectStream(host, port, mount, pass, bitrate);

    env->ReleaseStringUTFChars(host_, host);
    env->ReleaseStringUTFChars(mount_, mount);
    env->ReleaseStringUTFChars(pass_, pass);

    return res ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeDisconnectStream(JNIEnv *env, jobject thiz) {
    gAudioEngine.disconnectStream();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_audio_NativeAudioEngine_nativeGetStreamStatus(JNIEnv *env, jobject thiz) {
    return gAudioEngine.getStreamStatus();
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeUpdateMetadata(JNIEnv *env, jobject thiz, jstring title_) {
    // NOTE: Intentionally a no-op for now. Updating ICY/"now playing" metadata on Icecast
    // requires a separate authenticated HTTP GET to the server's admin endpoint
    // (/admin/metadata?mount=...&mode=updinfo&song=...) using *admin* credentials, which
    // this app doesn't currently collect (only the SOURCE mount password). Wire this up
    // once you add an admin-user/admin-password field to StreamConfig.
}

