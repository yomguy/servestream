LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := media1_jni
LOCAL_CFLAGS := 
LOCAL_SRC_FILES := MediaMetadataRetriever.cpp \
        ffmpeg_mediametadataretriever.c  \
	mediametadataretriever.cpp 
LOCAL_SHARED_LIBRARIES := libavcodec libavformat libavutil
LOCAL_C_INCLUDES := $(LOCAL_PATH)/../ffmpeg/ffmpeg/$(TARGET_ARCH_ABI)/include
LOCAL_LDLIBS := -llog

include $(BUILD_SHARED_LIBRARY)
