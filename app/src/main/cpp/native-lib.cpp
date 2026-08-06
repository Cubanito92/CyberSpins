#include <jni.h>
#include <string>
#include <vector>
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
Java_com_example_audio_NativeAudioEngine_nativeSetDuckingEnabled(JNIEnv *env, jobject thiz, jboolean enabled) {
    gAudioEngine.setDuckingEnabled(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeSetEqGains(JNIEnv *env, jobject thiz, jfloat lowDb, jfloat midDb, jfloat highDb) {
    gAudioEngine.setEqGains(lowDb, midDb, highDb);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeSetVoiceEffects(JNIEnv *env, jobject thiz, jfloat reverb, jfloat pitchSemitones, jfloat gateDb) {
    gAudioEngine.setVoiceEffects(reverb, pitchSemitones, gateDb);
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
        jstring host_, jint port, jstring mount_, jstring pass_, jint bitrate,
        jint protocol, jstring stationName_) {
    const char *host = env->GetStringUTFChars(host_, nullptr);
    const char *mount = env->GetStringUTFChars(mount_, nullptr);
    const char *pass = env->GetStringUTFChars(pass_, nullptr);
    const char *stationName = env->GetStringUTFChars(stationName_, nullptr);

    bool res = gAudioEngine.connectStream(host, port, mount, pass, bitrate, protocol, stationName);

    env->ReleaseStringUTFChars(host_, host);
    env->ReleaseStringUTFChars(mount_, mount);
    env->ReleaseStringUTFChars(pass_, pass);
    env->ReleaseStringUTFChars(stationName_, stationName);

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

extern "C" JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeFeedMusicPcm(
        JNIEnv *env, jobject thiz, jshortArray pcm_, jint frames, jint channels, jint sampleRate) {
    jshort *pcm = env->GetShortArrayElements(pcm_, nullptr);
    gAudioEngine.feedMusicPcm(reinterpret_cast<const int16_t*>(pcm), frames, channels, sampleRate);
    env->ReleaseShortArrayElements(pcm_, pcm, JNI_ABORT);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeSetMusicPlaying(JNIEnv *env, jobject thiz, jboolean playing) {
    gAudioEngine.setMusicPlaying(playing == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_audio_NativeAudioEngine_nativeClearMusicBuffer(JNIEnv *env, jobject thiz) {
    gAudioEngine.clearMusicBuffer();
}
