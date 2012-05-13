LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := media1_jni
LOCAL_CFLAGS    := 
LOCAL_SRC_FILES := media1_jni.c
LOCAL_SHARED_LIBRARIES := libavformat libavutil libavdevice
LOCAL_EXPORT_C_INCLUDES := /home/wseemann/Desktop/FlacTest/jni/ffmpeg/ffmpeg/$(TARGET_ARCH_ABI)/include
LOCAL_LDLIBS    := -L/home/wseemann/Desktop/FlacTest/jni/ffmpeg/ffmpeg/$(TARGET_ARCH_ABI)/lib -llog -lavformat -lavutil -lavdevice

include $(BUILD_SHARED_LIBRARY)
