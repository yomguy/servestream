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

#include <android/log.h>
#include <JNIHelper.h>
#include <jni_utils.h>
#include <ffmpeg_player.h>
#include <jni.h>
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/mathematics.h>
#include <pthread.h>

#define AUDIO_DATA_ID 1

#define TAG "FFmpegPlayer"

typedef struct PacketQueue {
	AVPacketList *first_pkt, *last_pkt;
	int nb_packets;
	int size;
} PacketQueue;

typedef struct AudioState {
	pthread_t decoder_thread;
	AVFormatContext *ic;
	int             audio_stream;
	double          audio_clock;
	AVStream        *audio_st;

	char            filename[1024];
	int             paused;
	int             stopped;
	int             quit;
	int             playstate;
} AudioState;

/* Since we only have one decoding thread, the Big Struct
   can be global in case we need it. */
AudioState *global_audio_state;

//audio
jbyteArray gAudioFrameRef; //reference to a java variable
jbyte* gAudioFrameRefBuffer;
int gAudioFrameRefBufferMaxSize;
jintArray gAudioFrameDataLengthRef; //reference to a java variable
int* gAudioFrameDataLengthRefBuffer;

static jmethodID post_event;
static jmethodID init_audio_track;
static jmethodID write_audio;
static jmethodID fill_buffer;

static JavaVM *m_vm;
static jobject gClassPathObject;
static const char *kClassPathName = "net/sourceforge/servestream/player/FFmpegPlayer";

jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "JNI_OnLoad()");

    JNIEnv *env;
    m_vm = vm;

    if ((*vm)->GetEnv(vm, (void**) &env, JNI_VERSION_1_6) != JNI_OK) {
    	__android_log_print(ANDROID_LOG_ERROR, TAG, "Failed to get the environment using GetEnv()");
    	return -1;
    }

    //audio
    gAudioFrameRef = NULL;
    gAudioFrameRefBuffer = NULL;
    gAudioFrameRefBufferMaxSize = 0;
    gAudioFrameDataLengthRef = NULL;
    gAudioFrameDataLengthRefBuffer = NULL;

    return JNI_VERSION_1_6;
}

void thread_notify(int msg, int ext1, int ext2, int obj1) {
	int status;
	JNIEnv *env;
	int isAttached = 0;
	status = (*m_vm)->GetEnv(m_vm, (void**)&env, JNI_VERSION_1_6);

	if (status < 0) {
		status = (*m_vm)->AttachCurrentThread(m_vm, &env, NULL);
		if (status < 0) {
			__android_log_print(ANDROID_LOG_DEBUG, TAG, "callback_handler: failed to attach current thread");
			return;
		}
		isAttached = 1;
	}

	jclass interfaceClass = (*env)->GetObjectClass(env, gClassPathObject);
	if (!interfaceClass) {
		__android_log_print(ANDROID_LOG_DEBUG, TAG, "callback_handler: failed to get class reference");
		if (isAttached) {
			(*m_vm)->DetachCurrentThread(m_vm);
		}
		return;
	}

	(*env)->CallStaticVoidMethod(env, interfaceClass, post_event, NULL, msg, ext1, ext2, NULL);

	if (isAttached) {
		(*m_vm)->DetachCurrentThread(m_vm);
	}
}

void notify(JNIEnv * env, jclass obj, int msg, int ext1, int ext2, int obj1) {
	jclass cls = (*env)->GetObjectClass(env, obj);
	(*env)->CallStaticVoidMethod(env, cls, post_event, NULL,
            msg, ext1, ext2, NULL);
}

void initClassHelper(JNIEnv *env, const char *path, jobject *objptr) {
	jclass cls = (*env)->FindClass(env, path);
	if(!cls) {
		return;
	}
	jmethodID constr = (*env)->GetMethodID(env, cls, "<init>", "()V");
	if(!constr) {
		return;
	}
	jobject obj = (*env)->NewObject(env, cls, constr);
	if(!obj) {
		return;
	}
	(*objptr) = (*env)->NewGlobalRef(env, obj);
}

JNIEXPORT void JNICALL
Java_net_sourceforge_servestream_player_FFmpegPlayer_native_1init(JNIEnv * env, jclass obj) {
    __android_log_write(ANDROID_LOG_INFO, TAG, "native_init()");

    initClassHelper(env, kClassPathName, &gClassPathObject);

    post_event = (*env)->GetStaticMethodID(env, obj, "postEventFromNative",
                                               "(Ljava/lang/Object;IIILjava/lang/Object;)V");

    init_audio_track = (*env)->GetStaticMethodID(env, obj, "initAudioTrack", "(II)V");
    write_audio = (*env)->GetStaticMethodID(env, obj, "nativeWriteCallback", "()V");
    fill_buffer = (*env)->GetStaticMethodID(env, obj, "fillBuffer", "(I)I");

    // Initialize libavformat and register all the muxers, demuxers and protocols.
    avcodec_register_all();
    av_register_all();
}

JNIEXPORT void JNICALL
Java_net_sourceforge_servestream_player_FFmpegPlayer_bind_1variables(JNIEnv* env, jobject obj, jbyteArray audioframe, jintArray audioframelength) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "bind_variables()");

    if (gAudioFrameRef) {
    	__android_log_print(ANDROID_LOG_ERROR, TAG, "call nativeCloseAudio() before calling this function");
    	notify(env, obj, MEDIA_ERROR, 0, 0, 0);
    	return;
    }

    if ((*env)->IsSameObject(env, audioframe, NULL)) {
    	__android_log_print(ANDROID_LOG_ERROR, TAG, "invalid arguments");
    	notify(env, obj, MEDIA_ERROR, 0, 0, 0);
    	return;
    }

    //audio frame buffer
    gAudioFrameRef = (*env)->NewGlobalRef(env, audioframe); //lock the array preventing the garbage collector from destructing it
    if (gAudioFrameRef == NULL) {
    	__android_log_print(ANDROID_LOG_ERROR, TAG, "NewGlobalRef() for audioframe failed");
    	notify(env, obj, MEDIA_ERROR, 0, 0, 0);
    	return;
    }

    jboolean test;
    gAudioFrameRefBuffer = (*env)->GetByteArrayElements(env, gAudioFrameRef, &test);
    if (gAudioFrameRefBuffer == 0 || test == JNI_TRUE) {
    	__android_log_print(ANDROID_LOG_ERROR, TAG, "failed to get audio frame reference or reference copied");
    	notify(env, obj, MEDIA_ERROR, 0, 0, 0);
    	return;
    }

    gAudioFrameRefBufferMaxSize = (*env)->GetArrayLength(env, gAudioFrameRef);
    if (gAudioFrameRefBufferMaxSize < AVCODEC_MAX_AUDIO_FRAME_SIZE) {
    	__android_log_print(ANDROID_LOG_ERROR, TAG, "failed to read or incorrect buffer length: %d", gAudioFrameRefBufferMaxSize);
    	notify(env, obj, MEDIA_ERROR, 0, 0, 0);
    	return;
    }

    __android_log_print(ANDROID_LOG_INFO, TAG, "buffer length: %d", gAudioFrameRefBufferMaxSize);

    //audio frame data size
    gAudioFrameDataLengthRef = (*env)->NewGlobalRef(env, audioframelength); //lock the variable preventing the garbage collector from destructing it
    if (gAudioFrameDataLengthRef == NULL) {
    	__android_log_print(ANDROID_LOG_ERROR, TAG, "NewGlobalRef() for audioframelength failed");
    	notify(env, obj, MEDIA_ERROR, 0, 0, 0);
    	return;
    }

    gAudioFrameDataLengthRefBuffer = (*env)->GetIntArrayElements(env, gAudioFrameDataLengthRef, &test);
    if (gAudioFrameDataLengthRefBuffer == 0 || test == JNI_TRUE) {
    	__android_log_print(ANDROID_LOG_ERROR, TAG, "failed to get audio data length reference or reference copied");
    	notify(env, obj, MEDIA_ERROR, 0, 0, 0);
    	return;
    }

    int audioDataLength = (*env)->GetArrayLength(env, gAudioFrameDataLengthRef);
    if (audioDataLength != 1) {
    	__android_log_print(ANDROID_LOG_ERROR, TAG, "failed to read or incorrect size of the audio data length reference: %d", audioDataLength);
    	notify(env, obj, MEDIA_ERROR, 0, 0, 0);
    	return;
    }

    __android_log_print(ANDROID_LOG_INFO, TAG, "size of the audio data length reference: %d", audioDataLength);
}

JNIEXPORT void JNICALL
Java_net_sourceforge_servestream_player_FFmpegPlayer__1setDataSource(JNIEnv* env, jobject obj, jstring path) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "setDataSource()");

    const char *uri;

    if (!path) {
    	jniThrowException(env, "java/lang/IllegalArgumentException", NULL);
        //global_audio_state->playstate = MEDIA_PLAYER_STATE_ERROR;
    	return;
    }

    global_audio_state = av_mallocz(sizeof(AudioState));
    global_audio_state->playstate = MEDIA_PLAYER_IDLE;

    if (!global_audio_state) {
    	jniThrowException(env, "java/io/IOException", NULL);
        global_audio_state->playstate = MEDIA_PLAYER_STATE_ERROR;
        return;
    }

    uri = (*env)->GetStringUTFChars(env, path, NULL);
    strncpy(global_audio_state->filename, uri, sizeof(global_audio_state->filename));
    (*env)->ReleaseStringUTFChars(env, path, uri);

    __android_log_print(ANDROID_LOG_ERROR, TAG, "setDataSource() succeeded");
}

void player_prepare(void * data) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "nativeOpenAudio()");

    AVFormatContext *pFormatCtx = NULL;
	int is_attached;
	JNIEnv *env;
	jclass interface_class;
	int i = 0;
	int stream_index = -1;
    AVCodecContext *avctx;
    AVCodec *codec;

    __android_log_print(ANDROID_LOG_INFO, TAG, "opening %s", global_audio_state->filename);
    if (avformat_open_input(&pFormatCtx, global_audio_state->filename, NULL, NULL) != 0) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "avformat_open_input() failed");
        jniThrowException(env, "java/io/IOException", NULL);
        global_audio_state->playstate = MEDIA_PLAYER_STATE_ERROR;
    	return;
    }

    if (avformat_find_stream_info(pFormatCtx, NULL) < 0) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Unable to locate stream information");
        global_audio_state->playstate = MEDIA_PLAYER_STATE_ERROR;
    	return;
    }

    global_audio_state->ic = pFormatCtx;
    global_audio_state->playstate = MEDIA_PLAYER_INITIALIZED;

    // Find the first audio stream
    for (i = 0; i < global_audio_state->ic->nb_streams; i++) {
    	if (global_audio_state->ic->streams[i]->codec->codec_type == AVMEDIA_TYPE_AUDIO) {
    		stream_index = i;
    		break;
    	}
    }

    if (stream_index == -1) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "audio stream not found");
        global_audio_state->playstate = MEDIA_PLAYER_STATE_ERROR;
        thread_notify(MEDIA_ERROR, 0, 0, 0);
		return;
    }

    // Get a pointer to the codec context for the audio stream
    avctx = global_audio_state->ic->streams[stream_index]->codec;

    // Find the decoder for the audio stream
    codec = avcodec_find_decoder(avctx->codec_id);

    if(codec == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "avcodec_find_decoder() failed to find audio decoder");
        global_audio_state->playstate = MEDIA_PLAYER_STATE_ERROR;
        thread_notify(MEDIA_ERROR, 0, 0, 0);
		return;
    }

    // Open the codec
    if (avcodec_open2(avctx, codec, NULL) < 0) {
    	__android_log_print(ANDROID_LOG_ERROR, TAG, "avcodec_open2() failed");
    	global_audio_state->playstate = MEDIA_PLAYER_STATE_ERROR;
    	thread_notify(MEDIA_ERROR, 0, 0, 0);
		return;
    }

    switch(avctx->codec_type) {
    	case AVMEDIA_TYPE_AUDIO:
    		global_audio_state->audio_stream = stream_index;
    		global_audio_state->audio_st = global_audio_state->ic->streams[stream_index];
    		break;
    	default:
    	    break;
    }

	attach_to_current_thread(m_vm, gClassPathObject, &is_attached, &env, &interface_class);

	if (is_attached) {
	    // set the sample rate and channels since these
	    // will be required when creating the AudioTrack object
		(*env)->CallStaticVoidMethod(env, interface_class, init_audio_track, avctx->sample_rate, avctx->channels);

		// fill the inital buffer
		AVPacket packet;
		memset(&packet, 0, sizeof(packet)); //make sure we can safely free it

		int i, ret;

		do {
			for (i = 0; i < global_audio_state->ic->nb_streams; ++i) {
				//av_init_packet(&packet);
				ret = av_read_frame(global_audio_state->ic, &packet);

				if (ret < 0) {
					if (ret == AVERROR_EOF || url_feof(global_audio_state->ic->pb)) {
						break;
				    }
				}

				ret = decodeFrameFromPacket(&packet);
				av_free_packet(&packet);
			}
		} while ((*env)->CallStaticIntMethod(env, interface_class, fill_buffer, ret) != 0);
    }

	detach_from_current_thread(m_vm, is_attached);

    global_audio_state->playstate = MEDIA_PLAYER_PREPARED;
	thread_notify(MEDIA_PREPARED, 0, 0, 0);
}

JNIEXPORT void JNICALL
Java_net_sourceforge_servestream_player_FFmpegPlayer_prepare(JNIEnv* env, jobject obj) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "prepare()");
    player_prepare(NULL);

}

JNIEXPORT void JNICALL
Java_net_sourceforge_servestream_player_FFmpegPlayer_prepareAsync(JNIEnv* env, jobject obj) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "prepareAsync()");

	pthread_t prepare_thread;
	pthread_create(&prepare_thread, NULL, (void *) &player_prepare, NULL);
}

JNIEXPORT void JNICALL
Java_net_sourceforge_servestream_player_FFmpegPlayer__1release(JNIEnv* env, jobject obj) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "release()");

    // global audio state is null, nothing to do
    if (!global_audio_state) {
    	return;
    }

    AVFormatContext *ic = global_audio_state->ic;
    AVCodecContext *avctx;

    if (!ic || global_audio_state->audio_stream < 0 || global_audio_state->audio_stream >= ic->nb_streams) {
        return;
    }

    avctx = ic->streams[global_audio_state->audio_stream]->codec;

    ic->streams[global_audio_state->audio_stream]->discard = AVDISCARD_ALL;
    avcodec_close(avctx);
    switch (avctx->codec_type) {
    case AVMEDIA_TYPE_AUDIO:
    	global_audio_state->audio_st = NULL;
    	global_audio_state->audio_stream = -1;
        break;
    default:
        break;
    }
}

JNIEXPORT void JNICALL
Java_net_sourceforge_servestream_player_FFmpegPlayer__1reset(JNIEnv* env, jobject obj) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "reset()");

}

int decodeFrameFromPacket(AVPacket *aPacket) {
	int n;
	AVPacket *pkt = aPacket;

    if (aPacket->stream_index == global_audio_state->audio_stream) {
        int dataLength = gAudioFrameRefBufferMaxSize;
        if (avcodec_decode_audio3(global_audio_state->audio_st->codec, (int16_t*)gAudioFrameRefBuffer, &dataLength, aPacket) <= 0) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "avcodec_decode_audio3() decoded no frame");
            gAudioFrameDataLengthRefBuffer[0] = 0;
            return -2;
        }

        // TODO add this call back!
        //*pts_ptr = pts;
        n = 2 * global_audio_state->audio_st->codec->channels;
        global_audio_state->audio_clock += (double)dataLength /
        		(double)(n * global_audio_state->audio_st->codec->sample_rate);

        /* if update, update the audio clock w/pts */
        if(pkt->pts != AV_NOPTS_VALUE) {
        	global_audio_state->audio_clock = av_q2d(global_audio_state->audio_st->time_base) * pkt->pts;
        }

        gAudioFrameDataLengthRefBuffer[0] = dataLength;
        return AUDIO_DATA_ID;
    }

    return 0;
}

void player_decode(void * data) {
	int ret;
	int eof = 0;

	global_audio_state->paused = 0;
	global_audio_state->stopped = 0;

	for (;;) {

		if (global_audio_state->paused || global_audio_state->stopped) {
			global_audio_state->playstate = MEDIA_PLAYER_PAUSED;
			return;
		}

		global_audio_state->playstate = MEDIA_PLAYER_STARTED;

		AVPacket packet;
		memset(&packet, 0, sizeof(packet)); //make sure we can safely free it

		int i;
		for (i = 0; i < global_audio_state->ic->nb_streams; ++i) {
			//av_init_packet(&packet);
			ret = av_read_frame(global_audio_state->ic, &packet);

	        if (ret < 0) {
	            if (ret == AVERROR_EOF || url_feof(global_audio_state->ic->pb)) {
	                eof = 1;
	                break;
	            }
	        }

			ret = decodeFrameFromPacket(&packet);
			av_free_packet(&packet);

			if (ret != 0) { //an error or a frame decoded
				int is_attached;
				JNIEnv *env;
				jclass interface_class;

				attach_to_current_thread(m_vm, gClassPathObject, &is_attached, &env, &interface_class);
        		if (is_attached) {
        			(*env)->CallStaticVoidMethod(env, interface_class, write_audio);
                }

        		detach_from_current_thread(m_vm, is_attached);
			}
		}

		if (eof) {
			break;
		}
	}

	if (eof) {
		global_audio_state->playstate = MEDIA_PLAYER_PLAYBACK_COMPLETE;
		thread_notify(MEDIA_PLAYBACK_COMPLETE, 0, 0, 0);
	}
}

JNIEXPORT void JNICALL
Java_net_sourceforge_servestream_player_FFmpegPlayer__1pause(JNIEnv* env, jobject obj) {
	global_audio_state->paused = !global_audio_state->paused;
}

JNIEXPORT void JNICALL
Java_net_sourceforge_servestream_player_FFmpegPlayer__1start(JNIEnv* env, jobject obj) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "start()");

    // if the decoder thread is NULL lets start a new one, otherwise
    // lets resume playback
	pthread_create(&global_audio_state->decoder_thread, NULL, (void *) &player_decode, NULL);
}

JNIEXPORT void JNICALL
Java_net_sourceforge_servestream_player_FFmpegPlayer__1stop(JNIEnv* env, jobject obj) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "stop()");

    global_audio_state->stopped = 1;
}

JNIEXPORT int JNICALL
Java_net_sourceforge_servestream_player_FFmpegPlayer_getCurrentPosition(JNIEnv* env, jobject obj) {
    //__android_log_print(ANDROID_LOG_INFO, TAG, "getCurrentPosition(), current position is: %f", global_audio_state->audio_clock);

	return (global_audio_state->audio_clock * 1000);
}

JNIEXPORT int JNICALL
Java_net_sourceforge_servestream_player_FFmpegPlayer_getDuration(JNIEnv* env, jobject obj) {
	if (global_audio_state->ic && (global_audio_state->ic->duration != AV_NOPTS_VALUE)) {
		return (global_audio_state->ic->duration / AV_TIME_BASE) * 1000;
	}

	return 0;
}

JNIEXPORT void JNICALL
Java_net_sourceforge_servestream_player_FFmpegPlayer__1seekTo(JNIEnv* env, jobject obj, int msec) {

}

void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
	int status;
	JNIEnv *env;
	int isAttached = 0;
	status = (*m_vm)->GetEnv(m_vm, (void**)&env, JNI_VERSION_1_6);

	if (status < 0) {
		status = (*m_vm)->AttachCurrentThread(m_vm, &env, NULL);
		if (status < 0) {
			__android_log_print(ANDROID_LOG_DEBUG, TAG, "callback_handler: failed to attach current thread");
			return;
		}
		isAttached = 1;
	}

	jclass interfaceClass = (*env)->GetObjectClass(env, gClassPathObject);
	if (!interfaceClass) {
		__android_log_print(ANDROID_LOG_DEBUG, TAG, "callback_handler: failed to get class reference");
		if (isAttached) {
			(*m_vm)->DetachCurrentThread(m_vm);
		}
		return;
	}

	if (gAudioFrameRef) {
		if (gAudioFrameRefBuffer) {
			(*env)->ReleaseByteArrayElements(env, gAudioFrameRef, gAudioFrameRefBuffer, 0);
			gAudioFrameRefBuffer = NULL;
		}

		(*env)->DeleteGlobalRef(env, gAudioFrameRef);
		gAudioFrameRef = NULL;
	}

	gAudioFrameRefBufferMaxSize = 0;

	if (gAudioFrameDataLengthRef) {
		if (gAudioFrameDataLengthRefBuffer) {
			(*env)->ReleaseIntArrayElements(env, gAudioFrameDataLengthRef, gAudioFrameDataLengthRefBuffer, 0);
			gAudioFrameDataLengthRefBuffer = NULL;
		}

		(*env)->DeleteGlobalRef(env, gAudioFrameDataLengthRef);
		gAudioFrameDataLengthRef = NULL;
	}

	if (isAttached) {
		(*m_vm)->DetachCurrentThread(m_vm);
	}
}
