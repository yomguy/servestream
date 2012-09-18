/*
 * ServeStream: A HTTP stream browser/player for Android
 * Copyright 2012 William Seemann
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
#include <string.h>
#include <strings.h>
#include <android/log.h>
#include <libavformat/avformat.h>
#include <libavutil/dict.h>

#define AV_DICT_IGNORE_SUFFIX   2
const char DURATION[] = "duration";

static AVDictionary *metadata = NULL;
static long int duration = 0;

JNIEXPORT void JNICALL
Java_net_sourceforge_servestream_media_MediaMetadataRetriever_native_1init(JNIEnv * env, jclass obj) {
    __android_log_write(ANDROID_LOG_INFO, "Java_net_sourceforge_servestream_media_MediaMetadataRetriever", "native_init called");

    // Initialize libavformat and register all the muxers, demuxers and protocols.
    av_register_all();
}

long int getDuration(AVFormatContext *fmt_ctx) {
	int i = 0;
	int audioStream = -1;

	if (avformat_find_stream_info(fmt_ctx, NULL) >= 0) {
		// Find the first audio stream
		for (i = 0; i < fmt_ctx->nb_streams; i++) {
			if (fmt_ctx->streams[i]->codec->codec_type == AVMEDIA_TYPE_AUDIO) {
				audioStream = i;
				break;
			}
		}

		if (audioStream != -1) {
			double divideFactor = (double) 1 / (fmt_ctx->streams[audioStream]->time_base.num / (double) fmt_ctx->streams[audioStream]->time_base.den);
		    long int dur = (long int) (((double) fmt_ctx->streams[audioStream]->duration / divideFactor) * 1000);

		    if (dur == 2147483648) {
		    	return 0;
		    } else {
		        return dur;
		    }
		}
	}

	return duration;
}

JNIEXPORT void JNICALL
Java_net_sourceforge_servestream_media_MediaMetadataRetriever__1setDataSource(JNIEnv * env, jclass obj, jstring jpath) {
	//__android_log_write(ANDROID_LOG_INFO, "Java_net_sourceforge_servestream_media_MediaMetadataRetriever", "setDataSource called");

	metadata = NULL;
	duration = 0;

    AVFormatContext *fmt_ctx = NULL;
    const char * path;

    path = (*env)->GetStringUTFChars(env, jpath, NULL);

    //__android_log_write(ANDROID_LOG_INFO, "Java_net_sourceforge_servestream_media_MediaMetadataRetriever", path);

    if (avformat_open_input(&fmt_ctx, path, NULL, NULL)) {
	    __android_log_write(ANDROID_LOG_INFO, "Java_net_sourceforge_servestream_media_MediaMetadataRetriever", "Metadata could not be retrieved");
        av_free(fmt_ctx);
    	fmt_ctx = NULL;
    	return;
    }

    metadata = fmt_ctx->metadata;
    duration = getDuration(fmt_ctx);

	__android_log_write(ANDROID_LOG_INFO, "Java_net_sourceforge_servestream_media_MediaMetadataRetriever", "Found metadata");
	//AVDictionaryEntry *tag = NULL;
	//while ((tag = av_metadata_get(metadata, "", tag, AV_DICT_IGNORE_SUFFIX))) {
    //	__android_log_write(ANDROID_LOG_INFO, "Java_net_sourceforge_servestream_media_MediaMetadataRetriever", tag->key);
    //	__android_log_write(ANDROID_LOG_INFO, "Java_net_sourceforge_servestream_media_MediaMetadataRetriever", tag->value);
    //}

    av_free(fmt_ctx);
    fmt_ctx = NULL;
}

JNIEXPORT jstring JNICALL
Java_net_sourceforge_servestream_media_MediaMetadataRetriever_extractMetadata(JNIEnv * env, jclass obj, jstring jkey) {
	//__android_log_write(ANDROID_LOG_INFO, "Java_net_sourceforge_servestream_media_MediaMetadataRetriever", "extractMetadata called");

	AVDictionaryEntry *tag = NULL;
    const char * key;

    key = (*env)->GetStringUTFChars(env, jkey, NULL) ;

	if (!key) {
		return NULL;
	}

	if (strcmp(key, DURATION) == 0) {
		char stringDuration[30];
		sprintf(stringDuration, "%lu", duration);
    	jstring jstrBuf = (*env)->NewStringUTF(env, stringDuration);
    	return jstrBuf;
	} else {
	    tag = av_dict_get(metadata, key, NULL, AV_DICT_IGNORE_SUFFIX);

	    if (tag) {
	    	jstring jstrBuf = (*env)->NewStringUTF(env, tag->value);
	    	return jstrBuf;
	    } else {
	    	return NULL;
	    }
	}
}

JNIEXPORT void JNICALL
Java_net_sourceforge_servestream_media_MediaMetadataRetriever_release(JNIEnv * env, jclass obj) {
	__android_log_write(ANDROID_LOG_INFO, "Java_net_sourceforge_servestream_media_MediaMetadataRetriever", "release called");

    if (metadata) {
        metadata = NULL;
        //av_free(metadata);
        //av_dict_free(&metadata);
    }
}

