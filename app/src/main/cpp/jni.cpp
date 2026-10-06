// JNI shim for `object Core` (Core.kt). Strings in, strings/arrays out; no object marshalling.
// One restroom::Core per process, created by init().
#include "restroom/core.h"

#include <android/log.h>
#include <jni.h>

#include <memory>
#include <mutex>
#include <string>

namespace {

std::mutex g_mutex;
std::unique_ptr<restroom::Core> g_core;

std::string str(JNIEnv* env, jstring s) {
    if (!s) return {};
    const char* chars = env->GetStringUTFChars(s, nullptr);
    std::string out(chars ? chars : "");
    if (chars) env->ReleaseStringUTFChars(s, chars);
    return out;
}

/// Raw UTF-8 bytes: JNI's modified UTF-8 cannot carry 4-byte sequences (emoji in Refuge comments).
std::string bytes(JNIEnv* env, jbyteArray a) {
    if (!a) return {};
    const jsize n = env->GetArrayLength(a);
    std::string out(static_cast<std::size_t>(n), '\0');
    if (n) env->GetByteArrayRegion(a, 0, n, reinterpret_cast<jbyte*>(&out[0]));
    return out;
}

jbyteArray toBytes(JNIEnv* env, const std::string& s) {
    jbyteArray out = env->NewByteArray(static_cast<jsize>(s.size()));
    if (!s.empty()) env->SetByteArrayRegion(out, 0, static_cast<jsize>(s.size()), reinterpret_cast<const jbyte*>(s.data()));
    return out;
}

void warn(const char* what, const std::exception& e) {
    __android_log_print(ANDROID_LOG_WARN, "restroomcore", "%s: %s", what, e.what());
}

restroom::Core& core() {
    if (!g_core) g_core = std::make_unique<restroom::Core>("");
    return *g_core;
}

}  // namespace

extern "C" {

JNIEXPORT void JNICALL Java_com_kevinpostal_restroom_Core_init(JNIEnv* env, jobject, jstring cachePath, jlong nowSec) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_core = std::make_unique<restroom::Core>(str(env, cachePath));
    g_core->cache.load(static_cast<long>(nowSec));
}

JNIEXPORT jint JNICALL Java_com_kevinpostal_restroom_Core_setPins(JNIEnv* env, jobject, jbyteArray json) {
    std::lock_guard<std::mutex> lock(g_mutex);
    try {
        core().pins = restroom::parsePottyPins(bytes(env, json));
        return static_cast<jint>(core().pins.size());
    } catch (const std::exception& e) {
        warn("setPins", e);
        return -1;
    }
}

JNIEXPORT jint JNICALL Java_com_kevinpostal_restroom_Core_pinsCached(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return static_cast<jint>(core().pins.size());
}

JNIEXPORT jint JNICALL Java_com_kevinpostal_restroom_Core_store(JNIEnv* env, jobject, jint x, jint y, jbyteArray refugeJson,
                                                                jbyteArray overpassJson, jlong nowSec) {
    std::lock_guard<std::mutex> lock(g_mutex);
    std::vector<restroom::Place> page;
    try {
        page = restroom::parseRefuge(bytes(env, refugeJson));
    } catch (const std::exception& e) {
        warn("parseRefuge", e);
        return -1;
    }
    if (overpassJson) {
        try {
            auto extras = restroom::parseOverpass(bytes(env, overpassJson));
            page.insert(page.end(), std::make_move_iterator(extras.begin()), std::make_move_iterator(extras.end()));
        } catch (const std::exception& e) {
            warn("parseOverpass", e);   // parks are a bonus
        }
    }
    const auto size = static_cast<jint>(page.size());
    core().cache.store(restroom::Cell{x, y}, std::move(page), static_cast<long>(nowSec));
    return size;
}

JNIEXPORT void JNICALL Java_com_kevinpostal_restroom_Core_erase(JNIEnv*, jobject, jint x, jint y) {
    std::lock_guard<std::mutex> lock(g_mutex);
    core().cache.erase(restroom::Cell{x, y});
}

JNIEXPORT jintArray JNICALL Java_com_kevinpostal_restroom_Core_cellOf(JNIEnv* env, jobject, jdouble lat, jdouble lon) {
    const auto c = restroom::Cell::of(lat, lon);
    const jint xy[2] = {c.x, c.y};
    jintArray out = env->NewIntArray(2);
    env->SetIntArrayRegion(out, 0, 2, xy);
    return out;
}

JNIEXPORT jdoubleArray JNICALL Java_com_kevinpostal_restroom_Core_cellCenter(JNIEnv* env, jobject, jint x, jint y) {
    const restroom::Cell c{x, y};
    const jdouble ll[2] = {c.centerLat(), c.centerLon()};
    jdoubleArray out = env->NewDoubleArray(2);
    env->SetDoubleArrayRegion(out, 0, 2, ll);
    return out;
}

JNIEXPORT jboolean JNICALL Java_com_kevinpostal_restroom_Core_isFresh(JNIEnv*, jobject, jint x, jint y, jlong nowSec) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return core().cache.isFresh(restroom::Cell{x, y}, static_cast<long>(nowSec)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_com_kevinpostal_restroom_Core_isServable(JNIEnv*, jobject, jint x, jint y, jlong nowSec) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return core().cache.isServable(restroom::Cell{x, y}, static_cast<long>(nowSec)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_com_kevinpostal_restroom_Core_ringServable(JNIEnv*, jobject, jint x, jint y, jlong nowSec) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return core().cache.ringServable(restroom::Cell{x, y}, static_cast<long>(nowSec)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jintArray JNICALL Java_com_kevinpostal_restroom_Core_missingRing(JNIEnv* env, jobject, jint x, jint y, jlong nowSec) {
    std::lock_guard<std::mutex> lock(g_mutex);
    const auto missing = core().cache.missingRing(restroom::Cell{x, y}, static_cast<long>(nowSec));
    std::vector<jint> flat;
    flat.reserve(missing.size() * 2);
    for (const auto& c : missing) { flat.push_back(c.x); flat.push_back(c.y); }
    jintArray out = env->NewIntArray(static_cast<jsize>(flat.size()));
    if (!flat.empty()) env->SetIntArrayRegion(out, 0, static_cast<jsize>(flat.size()), flat.data());
    return out;
}

JNIEXPORT jbyteArray JNICALL Java_com_kevinpostal_restroom_Core_publish(JNIEnv* env, jobject, jdouble lat, jdouble lon, jlong nowSec) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return toBytes(env, core().publishJson(lat, lon, static_cast<long>(nowSec)));
}

}  // extern "C"
