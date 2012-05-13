/*
 * ServeStream: A HTTP stream browser/player for Android
 * Copyright 2010 William Seemann
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <stdio.h>
#include <jni.h>
#include <android/log.h>
#include <libavformat/avformat.h>
#include <libavdevice/avdevice.h>

#define AV_DICT_IGNORE_SUFFIX   2

typedef struct {
    char *key;
    char *value;
} AVDictionaryEntry;

static AVMetadata *metadata = NULL;

JNIEXPORT void JNICALL
Java_net_sourceforge_servestream_media_MediaMetadataRetriever_nativeInit(JNIEnv * env, jclass obj) {
    __android_log_write(ANDROID_LOG_ERROR, "Java_net_sourceforge_servestream_media_MediaMetadataRetriever", "nativeInit called");

    // Initialize libavformat and register all the muxers, demuxers and protocols.
    // avdevice_register_all();
    av_register_all();
}

JNIEXPORT void JNICALL
Java_net_sourceforge_servestream_media_MediaMetadataRetriever_setDataSource(JNIEnv * env, jclass obj, jstring jpath) {
	//__android_log_write(ANDROID_LOG_ERROR, "Java_net_sourceforge_servestream_media_MediaMetadataRetriever", "setDataSource called");

    AVFormatContext *fmt_ctx = NULL;
	AVDictionaryEntry *tag = NULL;
    char * path;

    path = (*env)->GetStringUTFChars(env, jpath , NULL ) ;

    //__android_log_write(ANDROID_LOG_ERROR, "Java_net_sourceforge_servestream_media_MediaMetadataRetriever", path);

    if (av_open_input_file(&fmt_ctx, path, NULL, 0, NULL)) {
	__android_log_write(ANDROID_LOG_ERROR, "Java_net_sourceforge_servestream_media_MediaMetadataRetriever", "Metadata could not be retrieved");
        av_free(fmt_ctx);
    	fmt_ctx = NULL;
    	return;
    }

    metadata = fmt_ctx->metadata;
    av_free(fmt_ctx);
    fmt_ctx = NULL;
    
	__android_log_write(ANDROID_LOG_ERROR, "Java_net_sourceforge_servestream_media_MediaMetadataRetriever", "Found metadata");
    //while ((tag = av_metadata_get(metadata, "", tag, AV_DICT_IGNORE_SUFFIX))) {
    //	__android_log_write(ANDROID_LOG_ERROR, "Java_net_sourceforge_servestream_media_MediaMetadataRetriever", tag->key);
    //	__android_log_write(ANDROID_LOG_ERROR, "Java_net_sourceforge_servestream_media_MediaMetadataRetriever", tag->value);
    //}
}


JNIEXPORT jstring JNICALL
Java_net_sourceforge_servestream_media_MediaMetadataRetriever_extractMetadata(JNIEnv * env, jclass obj, jstring jkey) {
	//__android_log_write(ANDROID_LOG_ERROR, "Java_net_sourceforge_servestream_media_MediaMetadataRetriever", "extractMetadata called");

	AVDictionaryEntry *tag = NULL;
    char * key;

    key = (*env)->GetStringUTFChars(env, jkey , NULL ) ;

	if (!key) {
		return NULL;
	}

	tag = av_metadata_get(metadata, key, NULL, AV_DICT_IGNORE_SUFFIX);

	if (tag) {
        jstring jstrBuf = (*env)->NewStringUTF(env, tag->value);
        return jstrBuf;
	} else {
		return NULL;
	}
}

JNIEXPORT void JNICALL
Java_net_sourceforge_servestream_media_MediaMetadataRetriever_release(JNIEnv * env, jclass obj) {
	__android_log_write(ANDROID_LOG_ERROR, "Java_net_sourceforge_servestream_media_MediaMetadataRetriever", "release called");

    if (metadata) {
        metadata = NULL;
        //av_free(metadata);
    }
}
