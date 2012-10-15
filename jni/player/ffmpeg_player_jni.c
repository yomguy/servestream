#include <jni.h>
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/mathematics.h>
#include <android/log.h>

#define AUDIO_DATA_ID 1

enum media_event_type {
    MEDIA_NOP               = 0, // interface test message
    MEDIA_PREPARED          = 1,
    MEDIA_PLAYBACK_COMPLETE = 2,
    MEDIA_BUFFERING_UPDATE  = 3,
    MEDIA_SEEK_COMPLETE     = 4,
    MEDIA_SET_VIDEO_SIZE    = 5,
    MEDIA_TIMED_TEXT        = 99,
    MEDIA_ERROR             = 100,
    MEDIA_INFO              = 200,
};

typedef struct PacketQueue {
	AVPacketList *first_pkt, *last_pkt;
	int nb_packets;
	int size;
} PacketQueue;

typedef struct AudioState {
	AVFormatContext *pFormatCtx;
	int             audioStream;

	int             av_sync_type;
	double          external_clock; /* external clock base */
	int64_t         external_clock_time;
	int             seek_req;
	int             seek_flags;
	int64_t         seek_pos;
	double          audio_clock;
	AVStream        *audio_st;
	PacketQueue     audioq;
	DECLARE_ALIGNED(16, uint8_t, audio_buf[(AVCODEC_MAX_AUDIO_FRAME_SIZE * 3) / 2]);
	unsigned int    audio_buf_size;
	unsigned int    audio_buf_index;
	AVPacket        audio_pkt;
	uint8_t         *audio_pkt_data;
	int             audio_pkt_size;
	int             audio_hw_buf_size;
	double          audio_diff_cum; /* used for AV difference average computation */
	double          audio_diff_avg_coef;
	double          audio_diff_threshold;
	int             audio_diff_avg_count;
	double          frame_timer;
	double          frame_last_pts;
	double          frame_last_delay;

	int             pictq_size, pictq_rindex, pictq_windex;
	char            filename[1024];
	int             quit;
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

typedef int bool;
#define true 1
#define false 0

static int m_sampleRateInHz = 0;
static int m_channelConfig = 0;
static int64_t m_currentPosition = -1;

static AVCodecContext *pCodecCtx;
static AVCodec *dec;

static jmethodID post_event;

jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    __android_log_print(ANDROID_LOG_INFO, "Java_net_sourceforge_servestream_player_FFmpegPlayer", "JNI_OnLoad()");

    // TODO:  new code
	//m_vm = vm;

    //audio
    pCodecCtx = NULL;
    gAudioFrameRef = NULL;
    gAudioFrameRefBuffer = NULL;
    gAudioFrameRefBufferMaxSize = 0;
    gAudioFrameDataLengthRef = NULL;
    gAudioFrameDataLengthRefBuffer = NULL;

    return JNI_VERSION_1_6;
}

void notify(JNIEnv * env, jclass obj, int msg, int ext1, int ext2, int obj1) {
	jclass cls = (*env)->GetObjectClass(env, obj);
	(*env)->CallStaticVoidMethod(env, cls, post_event, NULL,
            msg, ext1, ext2, NULL);
}

JNIEXPORT void JNICALL
Java_net_sourceforge_servestream_player_FFmpegPlayer_native_1init(JNIEnv * env, jclass obj) {
    __android_log_write(ANDROID_LOG_INFO, "Java_net_sourceforge_servestream_player_FFmpegPlayer", "native_init()");

    post_event = (*env)->GetStaticMethodID(env, obj, "postEventFromNative",
                                               "(Ljava/lang/Object;IIILjava/lang/Object;)V");

    global_audio_state = av_mallocz(sizeof(AudioState));

    // Initialize libavformat and register all the muxers, demuxers and protocols.
    avcodec_register_all();
    av_register_all();
}

JNIEXPORT jint JNICALL
Java_net_sourceforge_servestream_player_FFmpegPlayer_nativeSetDataSource(JNIEnv* env, jobject obj, jstring path) {
    __android_log_print(ANDROID_LOG_INFO, "Java_net_sourceforge_servestream_player_FFmpegPlayer", "nativeOpenFromFile()");

    AVFormatContext *pFormatCtx = NULL;
    global_audio_state->audioStream = -1;

    const char * uri;

    // URI is null
    if (path == NULL) {
    	return -1;
    }

    uri = (*env)->GetStringUTFChars(env, path, NULL);
    //(*env)->ReleaseStringUTFChars(env, mediafile, mfileName); //always release the java string reference
    __android_log_print(ANDROID_LOG_INFO, "Java_net_sourceforge_servestream_player_FFmpegPlayer", "opening %s", uri);

    if (avformat_open_input(&pFormatCtx, uri, NULL, NULL) != 0) {
        __android_log_print(ANDROID_LOG_ERROR, "Java_net_sourceforge_servestream_player_FFmpegPlayer", "avformat_open_input() failed");
    	return -1;
    }

    if (avformat_find_stream_info(pFormatCtx, NULL) < 0) {
        __android_log_print(ANDROID_LOG_ERROR, "Java_net_sourceforge_servestream_player_FFmpegPlayer", "avformat_find_stream_info() failed");
    	return -1;
    }

    global_audio_state->pFormatCtx = pFormatCtx;

    __android_log_print(ANDROID_LOG_ERROR, "Java_net_sourceforge_servestream_player_FFmpegPlayer", "nativeOpenFromFile() succeeded");
    return 0;
}

JNIEXPORT void JNICALL
Java_net_sourceforge_servestream_player_FFmpegPlayer_nativeOpenAudio(JNIEnv* env, jobject obj, jbyteArray audioframe, jintArray audioframelength) {
    __android_log_print(ANDROID_LOG_INFO, "Java_net_sourceforge_servestream_player_FFmpegPlayer", "nativeOpenAudio()");

	int i = 0;
	int audio_stream_index = -1;

	//important
	if (gAudioFrameRef) {
		__android_log_print(ANDROID_LOG_ERROR, "Java_net_sourceforge_servestream_player_FFmpegPlayer", "call nativeCloseAudio() before calling this function");
		notify(env, obj, MEDIA_ERROR, 0, 0, 0);
		return;
	}

	if ((*env)->IsSameObject(env, audioframe, NULL)) {
		__android_log_print(ANDROID_LOG_ERROR, "Java_net_sourceforge_servestream_player_FFmpegPlayer", "invalid arguments");
		notify(env, obj, MEDIA_ERROR, 0, 0, 0);
		return;
	}

	//audio frame buffer
	gAudioFrameRef = (*env)->NewGlobalRef(env, audioframe); //lock the array preventing the garbage collector from destructing it
	if (gAudioFrameRef == NULL) {
		__android_log_print(ANDROID_LOG_ERROR, "Java_net_sourceforge_servestream_player_FFmpegPlayer", "NewGlobalRef() for audioframe failed");
		notify(env, obj, MEDIA_ERROR, 0, 0, 0);
		return;
	}

	jboolean test;
	gAudioFrameRefBuffer = (*env)->GetByteArrayElements(env, gAudioFrameRef, &test);
	if (gAudioFrameRefBuffer == 0 || test == JNI_TRUE) {
		__android_log_print(ANDROID_LOG_ERROR, "Java_net_sourceforge_servestream_player_FFmpegPlayer", "failed to get audio frame reference or reference copied");
		notify(env, obj, MEDIA_ERROR, 0, 0, 0);
		return;
	}

	gAudioFrameRefBufferMaxSize = (*env)->GetArrayLength(env, gAudioFrameRef);
	if (gAudioFrameRefBufferMaxSize < AVCODEC_MAX_AUDIO_FRAME_SIZE) {
		__android_log_print(ANDROID_LOG_ERROR, "Java_net_sourceforge_servestream_player_FFmpegPlayer", "failed to read or incorrect buffer length: %d", gAudioFrameRefBufferMaxSize);
		notify(env, obj, MEDIA_ERROR, 0, 0, 0);
		return;
	}

	__android_log_print(ANDROID_LOG_INFO, "Java_net_sourceforge_servestream_player_FFmpegPlayer", "buffer length: %d", gAudioFrameRefBufferMaxSize);

	//audio frame data size
	gAudioFrameDataLengthRef = (*env)->NewGlobalRef(env, audioframelength); //lock the variable preventing the garbage collector from destructing it
	if (gAudioFrameDataLengthRef == NULL) {
		__android_log_print(ANDROID_LOG_ERROR, "Java_net_sourceforge_servestream_player_FFmpegPlayer", "NewGlobalRef() for audioframelength failed");
		notify(env, obj, MEDIA_ERROR, 0, 0, 0);
		return;
	}

	gAudioFrameDataLengthRefBuffer = (*env)->GetIntArrayElements(env, gAudioFrameDataLengthRef, &test);
	if (gAudioFrameDataLengthRefBuffer == 0 || test == JNI_TRUE) {
		__android_log_print(ANDROID_LOG_ERROR, "Java_net_sourceforge_servestream_player_FFmpegPlayer", "failed to get audio data length reference or reference copied");
		notify(env, obj, MEDIA_ERROR, 0, 0, 0);
		return;
	}

	int audioDataLength = (*env)->GetArrayLength(env, gAudioFrameDataLengthRef);
	if (audioDataLength != 1) {
		__android_log_print(ANDROID_LOG_ERROR, "Java_net_sourceforge_servestream_player_FFmpegPlayer", "failed to read or incorrect size of the audio data length reference: %d", audioDataLength);
		notify(env, obj, MEDIA_ERROR, 0, 0, 0);
		return;
	}

	__android_log_print(ANDROID_LOG_INFO, "Java_net_sourceforge_servestream_player_FFmpegPlayer", "size of the audio data length reference: %d", audioDataLength);

    // Find the first audio stream
    for (i = 0; i < global_audio_state->pFormatCtx->nb_streams; i++) {
    	if (global_audio_state->pFormatCtx->streams[i]->codec->codec_type == AVMEDIA_TYPE_AUDIO) {
    		audio_stream_index = i;
    		break;
    	}
    }

    if (audio_stream_index == -1) {
        __android_log_print(ANDROID_LOG_ERROR, "Java_net_sourceforge_servestream_player_FFmpegPlayer", "audio stream not found");
		notify(env, obj, MEDIA_ERROR, 0, 0, 0);
		return;
    }

    // Get a pointer to the codec context for the audio stream
    pCodecCtx = global_audio_state->pFormatCtx->streams[audio_stream_index]->codec;

    // Find the decoder for the audio stream
    dec = avcodec_find_decoder(pCodecCtx->codec_id);

    if(dec == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, "Java_net_sourceforge_servestream_player_FFmpegPlayer", "avcodec_find_decoder() failed to find audio decoder");
		notify(env, obj, MEDIA_ERROR, 0, 0, 0);
		return;
    }

    // Open the codec
    if (avcodec_open2(pCodecCtx, dec, NULL) < 0) {
    	__android_log_print(ANDROID_LOG_ERROR, "Java_net_sourceforge_servestream_player_FFmpegPlayer", "avcodec_open2() failed");
		notify(env, obj, MEDIA_ERROR, 0, 0, 0);
		return;
    }

    switch(pCodecCtx->codec_type) {
    	case AVMEDIA_TYPE_AUDIO:
    		global_audio_state->audioStream = audio_stream_index;
    		global_audio_state->audio_st = global_audio_state->pFormatCtx->streams[audio_stream_index];
    		global_audio_state->audio_buf_size = 0;
    		global_audio_state->audio_buf_index = 0;

    		/* averaging filter for audio sync */
    		//global_audio_state->audio_diff_avg_coef = exp(log(0.01 / AUDIO_DIFF_AVG_NB));
    		global_audio_state->audio_diff_avg_count = 0;
    		/* Correct audio only if larger error than this */
    		//global_audio_state->audio_diff_threshold = 2.0 * SDL_AUDIO_BUFFER_SIZE / pCodecCtx->sample_rate;

    		memset(&global_audio_state->audio_pkt, 0, sizeof(global_audio_state->audio_pkt));
    		//packet_queue_init(&global_audio_state->audioq);
    		break;
    	default:
    	    break;
    }

    // set the sample rate and channels since these
    // will be required when creating the AudioTrack object
    m_sampleRateInHz = pCodecCtx->sample_rate;
    m_channelConfig = pCodecCtx->channels;

	notify(env, obj, MEDIA_PREPARED, 0, 0, 0);
}

JNIEXPORT void JNICALL
Java_net_sourceforge_servestream_player_FFmpegPlayer_nativeCloseAudio(JNIEnv* env, jobject obj) {
    __android_log_print(ANDROID_LOG_INFO, "Java_net_sourceforge_servestream_player_FFmpegPlayer", "nativeCloseAudio()");

    if (pCodecCtx) {
        avcodec_close(pCodecCtx);
        pCodecCtx = NULL;
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

    global_audio_state->audioStream = -1;
}

JNIEXPORT void JNICALL
Java_net_sourceforge_servestream_player_FFmpegPlayer_nativeClose(JNIEnv* env, jobject obj) {
    __android_log_print(ANDROID_LOG_INFO, "Java_net_sourceforge_servestream_player_FFmpegPlayer", "nativeClose()");

    Java_net_sourceforge_servestream_player_FFmpegPlayer_nativeCloseAudio(env, obj);

    // is this right?
    if (global_audio_state->pFormatCtx) {
    	AVFormatContext *pFormatCtx = global_audio_state->pFormatCtx;
    	avformat_close_input(&pFormatCtx);
    	global_audio_state->pFormatCtx = NULL;
    }
}

int decodeFrameFromPacket(AVPacket* aPacket) {
	int data_size, n;
	AVPacket *pkt = aPacket;
	m_currentPosition = aPacket->pts;

    if (aPacket->stream_index == global_audio_state->audioStream) {
        int dataLength = gAudioFrameRefBufferMaxSize;
        if (avcodec_decode_audio3(pCodecCtx, (int16_t*)gAudioFrameRefBuffer, &dataLength, aPacket) <= 0) {
            __android_log_print(ANDROID_LOG_ERROR, "Java_net_sourceforge_servestream_player_FFmpegPlayer", "avcodec_decode_audio4() decoded no frame");
            gAudioFrameDataLengthRefBuffer[0] = 0;
            return -2;
        }

        // TODO add this call back!
        //*pts_ptr = pts;
        n = 2 * global_audio_state->audio_st->codec->channels;
        global_audio_state->audio_clock += (double)data_size /
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

JNIEXPORT jint JNICALL
Java_net_sourceforge_servestream_player_FFmpegPlayer_nativeDecodeFrameFromFile(JNIEnv* env, jobject obj) {
    AVPacket packet;
    memset(&packet, 0, sizeof(packet)); //make sure we can safely free it

    int i;
    for (i = 0; i < global_audio_state->pFormatCtx->nb_streams; ++i) {
        //av_init_packet(&packet);
        if (av_read_frame(global_audio_state->pFormatCtx, &packet) != 0) {
            __android_log_print(ANDROID_LOG_ERROR, "Java_net_sourceforge_servestream_player_FFmpegPlayer", "av_read_frame() failed");
            return -1;
        }

        int ret = decodeFrameFromPacket(&packet);
        av_free_packet(&packet);

        if (ret != 0) { //an error or a frame decoded
        	return ret;
        }
    }

    return 0;
}

JNIEXPORT int JNICALL
Java_net_sourceforge_servestream_player_FFmpegPlayer_getCurrentPosition(JNIEnv* env, jobject obj) {
	// TODO add some error checking for these values
    __android_log_print(ANDROID_LOG_INFO, "Java_net_sourceforge_servestream_player_FFmpegPlayer", "getCurrentPosition(), current position is: %f", global_audio_state->audio_clock);

	return (global_audio_state->audio_clock * 1000);
}

JNIEXPORT int JNICALL
Java_net_sourceforge_servestream_player_FFmpegPlayer_getDuration(JNIEnv* env, jobject obj) {
	if (global_audio_state->pFormatCtx == NULL) {
		return 0;
	}

	if (global_audio_state->pFormatCtx && (global_audio_state->pFormatCtx->duration != AV_NOPTS_VALUE)) {
		int secs;
		secs = global_audio_state->pFormatCtx->duration / AV_TIME_BASE;
		return (secs * 1000);
	}

	return 0;
}

JNIEXPORT void JNICALL
Java_net_sourceforge_servestream_player_FFmpegPlayer__1seekTo(JNIEnv* env, jobject obj, int msec) {
	int64_t seek_target = 10000 ;// is->seek_pos;

	if (global_audio_state->audioStream >= 0) {
	    seek_target = av_rescale_q(seek_target, AV_TIME_BASE_Q, global_audio_state->audio_st->time_base);
	}

	if(av_seek_frame(global_audio_state->pFormatCtx, global_audio_state->audioStream, seek_target, AVSEEK_FLAG_BACKWARD) < 0) {
		__android_log_print(ANDROID_LOG_ERROR, "Java_net_sourceforge_servestream_player_FFmpegPlayer", "error while seeking");
	} else {
		__android_log_print(ANDROID_LOG_INFO, "Java_net_sourceforge_servestream_player_FFmpegPlayer", "seek was successful");
	}
}

