/*
 * ServeStream: A HTTP stream browser/player for Android
 * Copyright 2013 William Seemann
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

#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <ffmpeg_mediametadataretriever.h>

const char *DURATION = "duration";

const int SUCCESS = 0;
const int FAILURE = -1;

void getDuration(AVFormatContext *ic, char * value) {
	int duration = 0;

	if (ic) {
		if (ic->duration != AV_NOPTS_VALUE) {
			duration = ((ic->duration / AV_TIME_BASE) * 1000);
		}
	}

	sprintf(value, "%d", duration); // %i
}

int stream_component_open(State *s, int stream_index) {
	AVFormatContext *pFormatCtx = s->pFormatCtx;
	AVCodecContext *codecCtx;
	AVCodec *codec;

	if (stream_index < 0 || stream_index >= pFormatCtx->nb_streams) {
		return FAILURE;
	}

	// Get a pointer to the codec context for the stream
	codecCtx = pFormatCtx->streams[stream_index]->codec;

	printf("avcodec_find_decoder %s\n", codecCtx->codec_name);

	// Find the decoder for the audio stream
	codec = avcodec_find_decoder(codecCtx->codec_id);

	if(codec == NULL) {
	    printf("avcodec_find_decoder() failed to find audio decoder\n");
	    return FAILURE;
	}

	// Open the codec
    if (!codec || (avcodec_open2(codecCtx, codec, NULL) < 0)) {
	  	printf("avcodec_open2() failed\n");
		return FAILURE;
	}

	switch(codecCtx->codec_type) {
		case AVMEDIA_TYPE_AUDIO:
			s->audio_stream = stream_index;
		    s->audio_st = pFormatCtx->streams[stream_index];
			break;
		case AVMEDIA_TYPE_VIDEO:
			s->video_stream = stream_index;
		    s->video_st = pFormatCtx->streams[stream_index];
			break;
		default:
			break;
	}

	return SUCCESS;
}

int setDataSource(State **ps, const char* path) {
	printf("setDataSource\n");

	int video_index = -1;
	int audio_index = -1;
	int i;

	State *state = *ps;
	
	if (state && state->pFormatCtx) {
		avformat_close_input(&state->pFormatCtx);
	}

	if (!state) {
		state = av_mallocz(sizeof(State));
	}

	char duration[30] = "0";

    printf("Path: %s\n", path);

    if (avformat_open_input(&state->pFormatCtx, path, NULL, NULL) != 0) {
	    printf("Metadata could not be retrieved\n");
		*ps = NULL;
    	return FAILURE;
    }

	if (avformat_find_stream_info(state->pFormatCtx, NULL) < 0) {
	    printf("Metadata could not be retrieved\n");
	    avformat_close_input(&state->pFormatCtx);
		*ps = NULL;
    	return FAILURE;
	}

	getDuration(state->pFormatCtx, duration);
	av_dict_set(&state->pFormatCtx->metadata, DURATION, duration, 0);

    // Find the first audio and video stream
	for (i = 0; i < state->pFormatCtx->nb_streams; i++) {
		if (state->pFormatCtx->streams[i]->codec->codec_type == AVMEDIA_TYPE_VIDEO && video_index < 0) {
			video_index = i;
		}

		if (state->pFormatCtx->streams[i]->codec->codec_type == AVMEDIA_TYPE_AUDIO && audio_index < 0) {
			audio_index = i;
		}
	}

	/*if (audio_index >= 0) {
		stream_component_open(state, audio_index);
	}

	if (video_index >= 0) {
		stream_component_open(state, video_index);
	}

	if(state->video_stream < 0 || state->audio_stream < 0) {
	    avformat_close_input(&state->pFormatCtx);
		*ps = NULL;
		return FAILURE;
	}*/

	printf("Found metadata\n");
	AVDictionaryEntry *tag = NULL;
	while ((tag = av_dict_get(state->pFormatCtx->metadata, "", tag, AV_DICT_IGNORE_SUFFIX))) {
    	printf("Key %s: \n", tag->key);
    	printf("Value %s: \n", tag->value);
    }
	
	*ps = state;
	return SUCCESS;
}


const char* extractMetadata(State **ps, const char* key) {
	printf("extractMetadata\n");
    char* value = NULL;
	
	State *state = *ps;
    
	if (!state || !state->pFormatCtx) {
		goto fail;
	}

	if (key) {
		if (av_dict_get(state->pFormatCtx->metadata, key, NULL, AV_DICT_IGNORE_SUFFIX)) {
			value = av_dict_get(state->pFormatCtx->metadata, key, NULL, AV_DICT_IGNORE_SUFFIX)->value;
		}
	}

	fail:

	return value;
}

AVPacket* getEmbeddedPicture(State **ps) {
	printf("getEmbeddedPicture\n");
	int i = 0;
	AVPacket pkt, *packet = &pkt;
	packet = NULL;
	
	State *state = *ps;
	
	if (!state || !state->pFormatCtx) {
		goto fail;
	}

    // read the format headers
    if (state->pFormatCtx->iformat->read_header(state->pFormatCtx) < 0) {
    	printf("Could not read the format header\n");
    	goto fail;
    }

    // find the first attached picture, if available
    for (i = 0; i < state->pFormatCtx->nb_streams; i++) {
        if (state->pFormatCtx->streams[i]->disposition & AV_DISPOSITION_ATTACHED_PIC) {
        	printf("Found album art");
        	packet = &state->pFormatCtx->streams[i]->attached_pic;
        }
    }

	fail:
	return packet;
}

/*AVPacket* convert_to_jpeg(AVCodecContext *pCodecCtx, AVFrame *pFrame) {
	AVCodecContext *codecCtx;
	AVCodec *codec;
	uint8_t *Buffer;
	int BufSiz;
	int BufSizActual;
	int ImgFmt = PIX_FMT_YUVJ420P;
	FILE *JPEGFile;
	char JPEGFName[256];

	BufSiz = avpicture_get_size(ImgFmt, pCodecCtx->width, pCodecCtx->height);

	Buffer = (uint8_t *)malloc ( BufSiz );
	if ( Buffer == NULL )
		return ( 0 );
	memset ( Buffer, 0, BufSiz );

	codec = avcodec_find_encoder(CODEC_ID_MJPEG);
	if (!codec) {
	    printf("avcodec_find_decoder() failed to find audio decoder\n");
		free(Buffer);
		return NULL;
	}

    codecCtx = avcodec_alloc_context3(codec);
	if (!codecCtx) {
		printf("avcodec_alloc_context3 failed\n");
		free(Buffer);
		return NULL;
	}

	codecCtx->bit_rate = pCodecCtx->bit_rate;
	codecCtx->width = pCodecCtx->width;
	codecCtx->height = pCodecCtx->height;
	codecCtx->pix_fmt = ImgFmt;
	codecCtx->codec_type = AVMEDIA_TYPE_VIDEO;
	codecCtx->time_base.num = pCodecCtx->time_base.num;
	codecCtx->time_base.den = pCodecCtx->time_base.den;

	if (!codec || avcodec_open2(codecCtx, codec, NULL) < 0) {
	  	printf("avcodec_open2() failed\n");
		free(Buffer);
		return NULL;
	}

	codecCtx->mb_lmin = codecCtx->lmin = codecCtx->qmin * FF_QP2LAMBDA;
	codecCtx->mb_lmax = codecCtx->lmax = codecCtx->qmax * FF_QP2LAMBDA;
	codecCtx->flags = CODEC_FLAG_QSCALE;
	codecCtx->global_quality = codecCtx->qmin * FF_QP2LAMBDA;

	pFrame->pts = 1;
	pFrame->quality = codecCtx->global_quality;
	BufSizActual = avcodec_encode_video(codecCtx,Buffer,BufSiz,pFrame);

	sprintf(JPEGFName, "/home/wseemann/Desktop/one.jpg");
	JPEGFile = fopen(JPEGFName, "wb");
	fwrite(Buffer, 1, BufSizActual, JPEGFile);
	fclose(JPEGFile);

	avcodec_close(codecCtx);
	free(Buffer);
	return NULL;
}

AVPacket* decode_frame(State *state) {
	int frameFinished;
	AVFrame *pFrame;
	AVPacket packet;
	AVPacket *pkt = NULL;

	// Allocate video frame
	pFrame = avcodec_alloc_frame();

	// Read frames and save first five frames to disk
	while (av_read_frame(state->pFormatCtx, &packet) >= 0) {

		// Is this a packet from the video stream?
		if (packet.stream_index == state->videoStream) {
			// Decode video frame
			avcodec_decode_video2(state->video_st->codec, pFrame, &frameFinished, &packet);

			// Did we get a video frame?
			if (frameFinished) {
				pkt = convert_to_jpeg(state->video_st->codec, pFrame);
				break;
			}
		}

		// Free the packet that was allocated by av_read_frame
		av_free_packet(&packet);
	}

	// Free the frame
	av_free(pFrame);

	return pkt;
 }

AVPacket* getFrameAtTime(State **ps, long timeUs) {
	printf("getFrameAtTime\n");
	AVPacket *pkt = NULL;

    State *state = *ps;

	if (!state || !state->pFormatCtx) {
		goto fail;
	}

    int64_t seek_rel = timeUs * 1000;
    int64_t seek_target = timeUs * 1000;

    int64_t seek_min = seek_rel > 0 ? seek_target - seek_rel + 2: INT64_MIN;
    int64_t seek_max = seek_rel < 0 ? seek_target - seek_rel - 2: INT64_MAX;

    int ret = avformat_seek_file(state->pFormatCtx, -1, seek_min, seek_target, seek_max, AVSEEK_FLAG_FRAME);

    if (ret >= 0) {
    	pkt = decode_frame(state);
    }

	fail:
    return pkt;
}*/

void release(State **ps) {
	printf("release\n");

	State *state = *ps;
	
    if (state && state->pFormatCtx) {
        avformat_close_input(&state->pFormatCtx);
        ps = NULL;
    }
}
