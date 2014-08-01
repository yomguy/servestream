#!/bin/bash

if [ "$NDK" = "" ]; then
	echo NDK variable not set, assuming ${HOME}/android-ndk
	export NDK=${HOME}/android-ndk
fi

SYSROOT=$NDK/platforms/android-14/arch-x86
WORKING_DIR=`pwd`
# Expand the prebuilt/* path into the correct one
TOOLCHAIN=`echo $NDK/toolchains/x86-4.6/prebuilt/linux-x86*`
export PATH=$TOOLCHAIN/bin:$PATH

rm -rf build/ffmpeg
mkdir -p build/ffmpeg
cd ffmpeg

# Don't build any neon version for now
for version in x86; do

	DEST=$WORKING_DIR/build/ffmpeg
	FLAGS="--target-os=linux --cross-prefix=i686-linux-android- --arch=x86"
	FLAGS="$FLAGS --sysroot=$SYSROOT"
	FLAGS="$FLAGS --soname-prefix=/data/data/net.sourceforge.servestream/lib/"
	FLAGS="$FLAGS --enable-shared --disable-symver"
	FLAGS="$FLAGS --enable-small --optimization-flags=-O2"
	FLAGS="$FLAGS --disable-doc"
	FLAGS="$FLAGS --disable-ffmpeg"
	FLAGS="$FLAGS --disable-ffplay"
	FLAGS="$FLAGS --disable-ffprobe"
	FLAGS="$FLAGS --disable-ffserver"
	FLAGS="$FLAGS --disable-avdevice"
	FLAGS="$FLAGS --disable-postproc"
	FLAGS="$FLAGS --disable-avfilter"
	FLAGS="$FLAGS --disable-gpl"
        FLAGS="$FLAGS --disable-encoders"
	FLAGS="$FLAGS --disable-hwaccels"
	FLAGS="$FLAGS --disable-muxers"
	FLAGS="$FLAGS --disable-bsfs"
	FLAGS="$FLAGS --disable-protocols"
	FLAGS="$FLAGS --disable-indevs"
	FLAGS="$FLAGS --disable-outdevs"
	FLAGS="$FLAGS --disable-devices"
	FLAGS="$FLAGS --disable-filters"
	FLAGS="$FLAGS --enable-encoder=png"
	FLAGS="$FLAGS --enable-protocol=file,http,https,mmsh,mmst,pipe"
	FLAGS="$FLAGS --disable-debug"

	case "$version" in
		x86)
			EXTRA_CFLAGS=""
			EXTRA_LDFLAGS=""
			ABI="x86"
			;;
	esac
	DEST="$DEST/$ABI"
	FLAGS="$FLAGS --prefix=$DEST"

	mkdir -p $DEST
	echo $FLAGS --extra-cflags="$EXTRA_CFLAGS" --extra-ldflags="$EXTRA_LDFLAGS" > $DEST/info.txt
	./configure $FLAGS --extra-cflags="$EXTRA_CFLAGS" --extra-ldflags="$EXTRA_LDFLAGS" --extra-cflags='-mincoming-stack-boundary=4' | tee $DEST/configuration.txt
	[ $PIPESTATUS == 0 ] || exit 1
	make clean
	make -j4 || exit 1
	make install || exit 1

done

