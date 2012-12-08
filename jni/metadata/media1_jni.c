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

#include <jni.h>
#include <android/log.h>
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/dict.h>

const char *TAG = "Java_net_sourceforge_servestream_media_MediaMetadataRetriever";
const char *DURATION = "duration";

static AVFormatContext *pFormatCtx = NULL;
static AVPacket album_art;
static int found_album_art = 0;

// Native function definitions
JNIEXPORT void JNICALL Java_net_sourceforge_servestream_media_MediaMetadataRetriever_native_1init(JNIEnv *env, jclass obj);
JNIEXPORT void JNICALL Java_net_sourceforge_servestream_media_MediaMetadataRetriever__1setDataSource(JNIEnv *env, jclass obj, jstring path);
JNIEXPORT jstring JNICALL Java_net_sourceforge_servestream_media_MediaMetadataRetriever_extractMetadata(JNIEnv *env, jclass obj, jstring jkey);
JNIEXPORT jstring JNICALL Java_net_sourceforge_servestream_media_MediaMetadataRetriever_getEmbeddedPicture(JNIEnv* env, jobject obj, jstring jpath);
JNIEXPORT jbyteArray JNICALL Java_net_sourceforge_servestream_media_MediaMetadataRetriever__1getEmbeddedPicture(JNIEnv* env, jobject obj);
JNIEXPORT void JNICALL Java_net_sourceforge_servestream_media_MediaMetadataRetriever_release(JNIEnv *env, jclass obj);

JNIEXPORT void JNICALL
Java_net_sourceforge_servestream_media_MediaMetadataRetriever_native_1init(JNIEnv *env, jclass obj) {
    __android_log_write(ANDROID_LOG_INFO, TAG, "native_init called");

    // Initialize libavformat and register all the muxers, demuxers and protocols.
    av_register_all();
}

void getDuration(AVFormatContext *ic, char * value) {
	int duration = 0;

	if (ic) {
		if (ic->duration != AV_NOPTS_VALUE) {
			duration = ((ic->duration / AV_TIME_BASE) * 1000);
		}
	}

	sprintf(value, "%d", duration); // %i
}

AVPacket getAlbumArt(AVFormatContext *pFormatCtx) {
	int i = 0;
	AVPacket pkt;

	if (!pFormatCtx) {
    	goto fail;
	}

    // read the format headers
    if (pFormatCtx->iformat->read_header(pFormatCtx) < 0) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Could not read the format header");
    	goto fail;
    }

    // find the first attached picture, if available
    for (i = 0; i < pFormatCtx->nb_streams; i++) {
        if (pFormatCtx->streams[i]->disposition & AV_DISPOSITION_ATTACHED_PIC) {
        	__android_log_print(ANDROID_LOG_INFO, TAG, "Found album art");
        	pkt = pFormatCtx->streams[i]->attached_pic;
        	found_album_art = 1;
        }
    }

    fail:

    return pkt;
}

JNIEXPORT void JNICALL
Java_net_sourceforge_servestream_media_MediaMetadataRetriever__1setDataSource(JNIEnv *env, jclass obj, jstring jpath) {
	//__android_log_write(ANDROID_LOG_INFO, TAG, "setDataSource");

	if (pFormatCtx) {
		avformat_close_input(&pFormatCtx);
	}

	char duration[30] = "0";
	//av_init_packet(&album_art);
	found_album_art = 0;

    const char *uri;

    uri = (*env)->GetStringUTFChars(env, jpath, NULL);

    //__android_log_write(ANDROID_LOG_INFO, TAG, uri);

    if (avformat_open_input(&pFormatCtx, uri, NULL, NULL) != 0) {
	    __android_log_write(ANDROID_LOG_INFO, TAG, "Metadata could not be retrieved");
	    avformat_close_input(&pFormatCtx);
        (*env)->ReleaseStringUTFChars(env, jpath, uri);
    	return;
    }

	if (avformat_find_stream_info(pFormatCtx, NULL) < 0) {
	    __android_log_write(ANDROID_LOG_INFO, TAG, "Metadata could not be retrieved");
	    avformat_close_input(&pFormatCtx);
        (*env)->ReleaseStringUTFChars(env, jpath, uri);
    	return;
	}

	getDuration(pFormatCtx, duration);
	av_dict_set(&pFormatCtx->metadata, DURATION, duration, 0);
    //album_art = getAlbumArt(fmt_ctx);

	__android_log_write(ANDROID_LOG_INFO, TAG, "Found metadata");
	/*AVDictionaryEntry *tag = NULL;
	while ((tag = av_dict_get(pFormatCtx->metadata, "", tag, AV_DICT_IGNORE_SUFFIX))) {
    	__android_log_write(ANDROID_LOG_INFO, TAG, tag->key);
    	__android_log_write(ANDROID_LOG_INFO, TAG, tag->value);
    }*/

    (*env)->ReleaseStringUTFChars(env, jpath, uri);
}

JNIEXPORT jstring JNICALL
Java_net_sourceforge_servestream_media_MediaMetadataRetriever_extractMetadata(JNIEnv *env, jclass obj, jstring jkey) {
	//__android_log_write(ANDROID_LOG_INFO, TAG, "extractMetadata");

    const char *key;
    jstring value = NULL;

    key = (*env)->GetStringUTFChars(env, jkey, NULL) ;

	if (key) {
		if (av_dict_get(pFormatCtx->metadata, key, NULL, AV_DICT_IGNORE_SUFFIX)) {
			value = (*env)->NewStringUTF(env, av_dict_get(pFormatCtx->metadata, key, NULL, AV_DICT_IGNORE_SUFFIX)->value);
		}
	}

    (*env)->ReleaseStringUTFChars(env, jkey, key);

	return value;
}

JNIEXPORT jstring JNICALL
Java_net_sourceforge_servestream_media_MediaMetadataRetriever_getEmbeddedPicture(JNIEnv* env, jobject obj, jstring jpath) {
	//__android_log_write(ANDROID_LOG_INFO, TAG, "getEmbeddedPicture");

	jstring art_path = NULL;
    const char *path;

    path = (*env)->GetStringUTFChars(env, jpath, NULL);

	if (album_art.size > 0) {
        FILE *picture = fopen(path, "wb");

        if (picture) {
        	int ret = fwrite(album_art.data, album_art.size, 1, picture);
        	fclose(picture);

        	if (ret > 0) {
        		art_path = (*env)->NewStringUTF(env, path);
        	}
        }
    }

    (*env)->ReleaseStringUTFChars(env, jpath, path);

	return art_path;
}

JNIEXPORT jbyteArray JNICALL
Java_net_sourceforge_servestream_media_MediaMetadataRetriever__1getEmbeddedPicture(JNIEnv* env, jobject obj) {
	//__android_log_write(ANDROID_LOG_INFO, TAG, "getEmbeddedPicture");

	jbyteArray array = NULL;

	if (found_album_art) {
		int len = album_art.size;

        array = (*env)->NewByteArray(env, len);
        if (!array) {  // OutOfMemoryError exception has already been thrown.
        	__android_log_print(ANDROID_LOG_ERROR, TAG, "getEmbeddedPicture: OutOfMemoryError is thrown.");
        } else {
        	jbyte* bytes = (*env)->GetByteArrayElements(env, array, NULL);
            if (bytes != NULL) {
            	memcpy(bytes, album_art.data, len);
                (*env)->ReleaseByteArrayElements(env, array, bytes, 0);
            }
        }
    }

	av_init_packet(&album_art);

	return array;
}

JNIEXPORT void JNICALL
Java_net_sourceforge_servestream_media_MediaMetadataRetriever_release(JNIEnv *env, jclass obj) {
	//__android_log_write(ANDROID_LOG_INFO, TAG, "release");

    //av_free_packet(&album_art);

    if (pFormatCtx) {
        avformat_close_input(&pFormatCtx);
    }
}


