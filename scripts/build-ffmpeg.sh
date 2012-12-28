#!/bin/sh

WORKING_DIR=`pwd`
export NDK=`grep ndk.dir local.properties | cut -d'=' -f2`

if [ "$NDK" = "" ] || [ ! -d $NDK ]; then
	echo "NDK variable not set or path to NDK is invalid, exiting..."
	exit 1
fi

if [ ! -d ffmpeg ]; then
    # Download FFmpeg
    wget http://sourceforge.net/p/servestream/code/1175/tree/ffmpeg/ffmpeg-0.11.1-android-2012-09-18.tar.gz?format=raw --output-document=ffmpeg-0.11.1-android-2012-09-18.tar.gz

    # Unpackage the FFmpeg archive
    tar -xvf ffmpeg-0.11.1-android-2012-09-18.tar.gz
    rm ffmpeg-0.11.1-android-2012-09-18.tar.gz
    mv ffmpeg-0.11.1-android-2012-09-18 ffmpeg

    # Prepare the FFmpeg archive for building
    cd ffmpeg
    ./extract.sh

    # Move the build scripts to the FFmpeg build folder
    cp ../scripts/*.sh .

    # Make the build scripts executable
    chmod +x arm-build.sh
    chmod +x x86-build.sh
    chmod +x mips-build.sh
fi

if [ ! -d ../jni/ffmpeg/ffmpeg/arm* ]; then
    # Build FFmpeg from ARM architecture and copy to the JNI folder
    ./arm-build.sh
    cp -r build/ffmpeg/* ../jni/ffmpeg/ffmpeg
fi

if [ ! -d ../jni/ffmpeg/ffmpeg/x86 ]; then
    # Build FFmpeg from x86 architecture and copy to the JNI folder
    ./x86-build.sh
    cp -r build/ffmpeg/* ../jni/ffmpeg/ffmpeg
fi

if [ ! -d ../jni/ffmpeg/ffmpeg/mips ]; then
    # Build FFmpeg from MIPS architecture and copy to the JNI folder
    ./mips-build.sh
    cp -r build/ffmpeg/* ../jni/ffmpeg/ffmpeg
fi

echo Native build complete, exiting...
exit
