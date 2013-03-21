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

int setDataSource(AVFormatContext** ps, const char* path) {
	//printf("setDataSource\n");

	AVFormatContext *pFormatCtx = *ps;
	
	if (pFormatCtx) {
		avformat_close_input(&pFormatCtx);
	}

	char duration[30] = "0";

    //printf("Path: %s\n", path);

    if (avformat_open_input(&pFormatCtx, path, NULL, NULL) != 0) {
	    //printf("Metadata could not be retrieved\n");
		*ps = NULL;
    	return FAILURE;
    }

	if (avformat_find_stream_info(pFormatCtx, NULL) < 0) {
	    //printf("Metadata could not be retrieved\n");
	    avformat_close_input(&pFormatCtx);
		*ps = NULL;
    	return FAILURE;
	}

	getDuration(pFormatCtx, duration);
	av_dict_set(&pFormatCtx->metadata, DURATION, duration, 0);

	/*printf("Found metadata\n");
	AVDictionaryEntry *tag = NULL;
	while ((tag = av_dict_get(pFormatCtx->metadata, "", tag, AV_DICT_IGNORE_SUFFIX))) {
    	printf("Key %s: \n", tag->key);
    	printf("Value %s: \n", tag->value);
    }*/
	
	*ps = pFormatCtx;
	return SUCCESS;
}


const char* extractMetadata(AVFormatContext** ps, const char* key) {
	//printf("extractMetadata\n");
    char* value = NULL;
	
	AVFormatContext *pFormatCtx = *ps;
    
	if (!pFormatCtx) {
		goto fail;
	}

	if (key) {
		if (av_dict_get(pFormatCtx->metadata, key, NULL, AV_DICT_IGNORE_SUFFIX)) {
			value = av_dict_get(pFormatCtx->metadata, key, NULL, AV_DICT_IGNORE_SUFFIX)->value;
		}
	}

	fail:

	return value;
}

AVPacket* getEmbeddedPicture(AVFormatContext** ps) {
	//printf("getEmbeddedPicture\n");
	int i = 0;
	AVPacket packet;
	AVPacket *pkt = NULL;
	
	AVFormatContext *pFormatCtx = *ps;
	
	if (!pFormatCtx) {
		goto fail;
	}

    // read the format headers
    if (pFormatCtx->iformat->read_header(pFormatCtx) < 0) {
    	//printf("Could not read the format header\n");
    	goto fail;
    }

    // find the first attached picture, if available
    for (i = 0; i < pFormatCtx->nb_streams; i++) {
        if (pFormatCtx->streams[i]->disposition & AV_DISPOSITION_ATTACHED_PIC) {
        	//printf("Found album art");
        	packet = pFormatCtx->streams[i]->attached_pic;
        	pkt = (AVPacket *) malloc(sizeof(packet));
        	pkt->data = packet.data;
        	pkt->size = packet.size;
        }
    }

	fail:
	return pkt;
}

void release(AVFormatContext** ps) {
	//printf("release\n");

	AVFormatContext *pFormatCtx = *ps;
	
    if (pFormatCtx) {
        avformat_close_input(&pFormatCtx);
        ps = NULL;
    }
}
