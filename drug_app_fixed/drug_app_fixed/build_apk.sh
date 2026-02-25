#!/bin/bash

# 药物血药浓度追踪器 - 构建脚本

echo "=========================================="
echo "  药物血药浓度追踪器 - APK 构建脚本"
echo "=========================================="
echo ""

# 检查 Java
if ! command -v java &> /dev/null; then
    echo "错误: 未找到 Java，请先安装 Java 17+"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
echo "Java 版本: $JAVA_VERSION"

# 检查 Android SDK
if [ -z "$ANDROID_HOME" ]; then
    echo "警告: ANDROID_HOME 环境变量未设置"
    echo "请设置 ANDROID_HOME 指向你的 Android SDK 目录"
    echo "例如: export ANDROID_HOME=$HOME/Android/Sdk"
    echo ""
fi

# 检查 Gradle Wrapper
if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    echo "Gradle Wrapper 不存在，正在下载..."
    mkdir -p gradle/wrapper
    curl -L -o gradle/wrapper/gradle-wrapper.jar \
        "https://raw.githubusercontent.com/gradle/gradle/v8.5.0/gradle/wrapper/gradle-wrapper.jar" || {
        echo "下载失败，请手动下载 gradle-wrapper.jar"
        exit 1
    }
fi

# 构建 Debug APK
echo ""
echo "开始构建 Debug APK..."
./gradlew assembleDebug

if [ $? -eq 0 ]; then
    echo ""
    echo "=========================================="
    echo "  构建成功!"
    echo "=========================================="
    echo ""
    echo "APK 位置: app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    echo "安装命令:"
    echo "  adb install app/build/outputs/apk/debug/app-debug.apk"
    echo ""
else
    echo ""
    echo "=========================================="
    echo "  构建失败!"
    echo "=========================================="
    echo ""
    echo "请检查错误信息并修复问题后重试"
    exit 1
fi