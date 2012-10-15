LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := ffmpeg_player_jni
LOCAL_CFLAGS := 
LOCAL_SRC_FILES := ffmpeg_player_jni.c
LOCAL_SHARED_LIBRARIES := libavformat libavcodec
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/../ffmpeg/ffmpeg/$(TARGET_ARCH_ABI)/include
LOCAL_LDLIBS := -L$(LOCAL_PATH)/../ffmpeg/ffmpeg/$(TARGET_ARCH_ABI)/lib -llog -lavcodec -lavformat -lavutil

include $(BUILD_SHARED_LIBRARY)
