#!/bin/bash

# Configuration
EMULATOR_PATH="/Users/aaftab/Library/Android/sdk/emulator/emulator"
ADB_PATH="/Users/aaftab/Library/Android/sdk/platform-tools/adb"
AVD_NAME="Pixel_8_Pro"
APK_PATH="./app/build/outputs/apk/debug/app-debug.apk"
PACKAGE_NAME="com.darksunTechnologies.justdoit"
MAIN_ACTIVITY=".SplashScreen"

echo "Checking if emulator is running..."
# Grep for domestic / local ADB devices matching 'emulator-'
RUNNING_EMULATOR=$($ADB_PATH devices | grep "emulator-" | head -n 1 | awk '{print $1}')

if [ -z "$RUNNING_EMULATOR" ]; then
    echo "Starting emulator [$AVD_NAME] in background..."
    nohup $EMULATOR_PATH -avd $AVD_NAME > /dev/null 2>&1 &
    
    echo "Waiting for emulator to connect to adb..."
    $ADB_PATH wait-for-device
    
    echo "Waiting for full boot-up (this may take a minute)..."
    while [ "$($ADB_PATH shell getprop sys.boot_completed | tr -d '\r')" != "1" ]; do
        sleep 2
    done
    echo "Emulator is ready!"
else
    echo "Emulator [$RUNNING_EMULATOR] is already running."
fi

echo "Installing APK: $APK_PATH"
$ADB_PATH install -r "$APK_PATH"

echo "Launching Just Do It ($PACKAGE_NAME)..."
$ADB_PATH shell am start -n "$PACKAGE_NAME/$MAIN_ACTIVITY"

echo "App launched successfully!"
