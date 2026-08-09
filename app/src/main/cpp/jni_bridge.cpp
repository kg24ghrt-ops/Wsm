#include <jni.h>
#include <string>
#include <vector>
#include "editor.h"
#include "lexer.h"

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

// Returns a String "type,length;type,length;..."
extern "C" JNIEXPORT jstring JNICALL
Java_com_example_personalcustomide_EditorNative_getLineTokens(JNIEnv* env, jobject thiz, jint line) {
    std::string full = buffer.getText();
    std::vector<std::string> lines;
    size_t start = 0, end;
    while ((end = full.find('\n', start)) != std::string::npos) {
        lines.push_back(full.substr(start, end - start));
        start = end + 1;
    }
    if (start < full.size()) lines.push_back(full.substr(start));

    if (line < 0 || line >= (int)lines.size()) {
        return env->NewStringUTF("");
    }

    auto tokens = Lexer::tokenize(lines[line]);
    std::string result;
    for (auto& token : tokens) {
        if (!result.empty()) result += ";";
        result += std::to_string(token.first) + "," + std::to_string(token.second);
    }
    return env->NewStringUTF(result.c_str());
}