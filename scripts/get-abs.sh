#!/bin/sh

WORKING_DIR=`pwd`
ABS_DIR=$WORKING_DIR/ActionBarSherlock-4.2.0
ABS_LIBRARY_DIR=$WORKING_DIR/ActionBarSherlock-4.2.0/library

if [ ! -d $ABS_DIR ]; then
    # Download ActionBarSherlock
    wget https://github.com/JakeWharton/ActionBarSherlock/archive/4.2.0.tar.gz

    # Unpackage the ActionBarSherlock archive
    tar -xvf 4.2.0.tar.gz
    rm 4.2.0.tar.gz

    # Prepare the ActionBarSherlock archive for building
    cd $ABS_LIBRARY_DIR
    android update project -p .
    ant clean debug

    cd $WORKING_DIR

    # Add a reference to the library in project.properties
    echo "android.library.reference.1=./ActionBarSherlock-4.2.0/library" >> project.properties
fi
