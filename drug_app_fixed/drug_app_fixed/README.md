# 药物血药浓度追踪器

## 项目概述

这是一个原生 Android + Kotlin 开发的药物血药浓度追踪应用。

## 功能特性

- **分层血药浓度图表**：今日活跃/功能性/维持类/全部 四个 Tab
- **峰值时机规划器**：反向计算最佳服药时间和剂量
- **活跃药物总览**：实时显示当前体内活跃药物浓度
- **服药记录管理**：完整的 CRUD 操作
- **浓度触发提醒**：WorkManager 后台提醒
- **数据持久化**：Room 数据库
- **数据导出**：CSV 导出和数据库备份

## 技术栈

- 原生 Android + Kotlin
- Room 数据库 (SQLite)
- WorkManager (后台任务)
- MPAndroidChart (图表)
- ViewModel + LiveData

## 构建说明

### 方法 1：使用 Android Studio（推荐）

1. 打开 Android Studio
2. 选择 "Open an existing Android Studio project"
3. 选择 `drug_tracker_app` 文件夹
4. 等待 Gradle 同步完成
5. 点击 Build → Build Bundle(s) / APK(s) → Build APK(s)
6. APK 将生成在 `app/build/outputs/apk/debug/app-debug.apk`

### 方法 2：使用命令行

需要安装：
- Android SDK (API 26+)
- Gradle 8.5+

```bash
cd drug_tracker_app

# 下载 Gradle Wrapper（如果缺少）
./gradlew wrapper

# 构建 Debug APK
./gradlew assembleDebug

# 构建 Release APK
./gradlew assembleRelease
```

## 项目结构

```
drug_tracker_app/
├── app/
│   ├── src/main/java/com/example/drugtracker/
│   │   ├── data/           # 数据层 (Entity, DAO, Database)
│   │   ├── logic/          # 业务逻辑 (计算, 提醒引擎)
│   │   ├── ui/             # UI 层 (ViewModel, 图表)
│   │   ├── receiver/       # 广播接收器
│   │   ├── worker/         # WorkManager Worker
│   │   ├── util/           # 工具类
│   │   └── *.kt            # Activity 类
│   └── src/main/res/       # 布局和资源文件
├── build.gradle            # 项目级构建配置
├── app/build.gradle        # 模块级构建配置
└── settings.gradle         # 项目设置
```

## 安装 APK

构建完成后，可以通过以下方式安装：

```bash
# 使用 adb
adb install app/build/outputs/apk/debug/app-debug.apk

# 或传输到手机后通过文件管理器安装
```

## 预设药物

包含 19 种预设药物：
- 草酸艾司西酞普兰、拉莫三嗪、丁螺环酮
- 优甲乐（左甲状腺素）- 关键药物
- 加巴喷丁、劳拉西泮、酒石酸唑吡坦
- 右佐匹克隆、布洛芬、对乙酰氨基酚
- 托莫西汀、哌甲酯、咖啡因
- 茶苯海明、褪黑素、茶氨酸
- 苏糖酸镁、茴拉西坦、长春西汀

## 自定义药物

在"药物管理"页面可以添加任意自定义药物，填写名称、半衰期、Tmax、单位等信息。

## 数据备份

- 设置页提供数据库备份和恢复功能
- 历史记录页支持 CSV 导出
- 覆盖安装不会丢失数据

## 开发者工具

设置页提供：
- 崩溃日志导出
- 数据备份/恢复
- 提醒阈值设置
- 优甲乐提醒时间设置