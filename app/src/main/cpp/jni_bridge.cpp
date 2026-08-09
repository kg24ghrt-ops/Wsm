#include <jni.h>
#include <string>
#include "editor.h"

static EditorBuffer buffer;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_personalcustomide_EditorNative_loadFile(JNIEnv* env, jobject thiz, jstring path) {
    const char* utfPath = env->GetStringUTFChars(path, nullptr);
    bool ok = buffer.loadFile(utfPath);
    env->ReleaseStringUTFChars(path, utfPath);
    return ok;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_personalcustomide_EditorNative_getText(JNIEnv* env, jobject thiz) {
    std::string text = buffer.getText();
    return env->NewStringUTF(text.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_personalcustomide_EditorNative_insertText(JNIEnv* env, jobject thiz, jint pos, jstring text) {
    const char* utfText = env->GetStringUTFChars(text, nullptr);
    buffer.insertText(pos, utfText);
    env->ReleaseStringUTFChars(text, utfText);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_personalcustomide_EditorNative_deleteText(JNIEnv* env, jobject thiz, jint pos, jint len) {
    buffer.deleteText(pos, len);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_personalcustomide_EditorNative_saveFile(JNIEnv* env, jobject thiz, jstring path) {
    const char* utfPath = env->GetStringUTFChars(path, nullptr);
    bool ok = buffer.saveFile(utfPath);
    env->ReleaseStringUTFChars(path, utfPath);
    return ok;
}