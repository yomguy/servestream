LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := media1_jni
LOCAL_CFLAGS := 
LOCAL_SRC_FILES := media1_jni.c
LOCAL_SHARED_LIBRARIES := libavformat libavutil
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/../ffmpeg/ffmpeg/$(TARGET_ARCH_ABI)/include
LOCAL_LDLIBS := -L$(LOCAL_PATH)/../ffmpeg/ffmpeg/$(TARGET_ARCH_ABI)/lib -llog -lavformat -lavutil

include $(BUILD_SHARED_LIBRARY)
