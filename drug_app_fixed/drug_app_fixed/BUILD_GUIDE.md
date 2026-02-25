# 构建指南 - 药物血药浓度追踪器

## 快速构建步骤

### 第一步：安装 Android Studio

1. 下载 Android Studio：https://developer.android.com/studio
2. 安装时选择：
   - Android SDK
   - Android SDK Platform-Tools
   - Android SDK Build-Tools
   - Android Emulator（可选）

### 第二步：打开项目

1. 启动 Android Studio
2. 点击 "Open" 或 "Open an existing Android Studio project"
3. 选择 `drug_tracker_app` 文件夹
4. 等待 Gradle 同步（首次可能需要几分钟下载依赖）

### 第三步：构建 APK

**Debug 版本（测试用）：**
1. 菜单栏选择：Build → Build Bundle(s) / APK(s) → Build APK(s)
2. 构建完成后右下角会显示提示，点击 "locate" 找到 APK 文件
3. APK 路径：`app/build/outputs/apk/debug/app-debug.apk`

**Release 版本（发布用）：**
1. 菜单栏选择：Build → Generate Signed Bundle / APK
2. 选择 APK
3. 创建或选择密钥库（keystore）
4. 填写密钥信息
5. 选择 release 构建类型
6. 点击 Finish

### 第四步：安装 APK

**方法 1 - 使用 adb：**
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

**方法 2 - 手动安装：**
1. 将 APK 文件传输到手机
2. 在手机上打开文件管理器
3. 点击 APK 文件安装
4. 可能需要开启 "允许安装未知来源应用"

## 常见问题

### Gradle 同步失败

**问题：** `Could not find com.github.PhilJay:MPAndroidChart`

**解决：** 确保 `settings.gradle` 中包含 jitpack 仓库：
```gradle
maven { url 'https://jitpack.io' }
```

### 编译错误

**问题：** `Cannot resolve symbol 'R'`

**解决：**
1. Build → Clean Project
2. Build → Rebuild Project
3. File → Invalidate Caches / Restart

### 安装失败

**问题：** `INSTALL_FAILED_ALREADY_EXISTS`

**解决：**
```bash
adb uninstall com.example.drugtracker
adb install app-debug.apk
```

## 命令行构建（高级用户）

### 环境要求

- Java 17+
- Android SDK（设置 ANDROID_HOME 环境变量）
- Gradle 8.5+

### 设置环境变量

**Linux/Mac:**
```bash
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools
```

**Windows:**
```cmd
set ANDROID_HOME=C:\Users\%USERNAME%\AppData\Local\Android\Sdk
set PATH=%PATH%;%ANDROID_HOME%\tools;%ANDROID_HOME%\platform-tools
```

### 构建命令

```bash
cd drug_tracker_app

# 下载 Gradle Wrapper（首次）
gradle wrapper

# 构建 Debug APK
./gradlew assembleDebug

# 构建 Release APK（需要签名配置）
./gradlew assembleRelease

# 清理构建
./gradlew clean

# 运行测试
./gradlew test
```

## 签名 Release APK

### 创建密钥库

```bash
keytool -genkey -v -keystore drugtracker.keystore -alias drugtracker -keyalg RSA -keysize 2048 -validity 10000
```

### 配置签名（app/build.gradle）

```gradle
android {
    signingConfigs {
        release {
            storeFile file("drugtracker.keystore")
            storePassword "your_password"
            keyAlias "drugtracker"
            keyPassword "your_password"
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.release
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
}
```

## 项目依赖

主要依赖库版本：
- Kotlin: 1.9.22
- Android Gradle Plugin: 8.2.2
- Compile SDK: 34
- Min SDK: 26
- Target SDK: 34

第三方库：
- MPAndroidChart: 3.1.0
- Room: 2.6.1
- WorkManager: 2.9.0
- Lifecycle: 2.7.0