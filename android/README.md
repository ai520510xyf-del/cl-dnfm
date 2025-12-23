# Android原生APP开发指南

将AI游戏机器人打包为Android APK的完整方案。

## 架构设计

```
Android APP
├── 无障碍服务 (Accessibility Service)
│   ├── 屏幕截图
│   └── 模拟点击/滑动
├── TensorFlow Lite
│   └── YOLO模型推理
├── 游戏策略引擎
│   └── 决策逻辑
└── 用户界面
    ├── 启动/停止
    └── 配置管理
```

## 技术栈

- **语言**: Kotlin (推荐) 或 Java
- **ML框架**: TensorFlow Lite / ONNX Runtime Mobile
- **UI**: Jetpack Compose 或 XML布局
- **权限**: Accessibility Service + MediaProjection

## 开发步骤

### 第1步: 模型转换

将YOLO模型转换为TFLite格式：

```python
# convert_to_tflite.py
from ultralytics import YOLO

# 加载训练好的模型
model = YOLO("models/best.pt")

# 导出为TFLite
model.export(
    format="tflite",
    imgsz=320,  # 降低分辨率以提升手机性能
    int8=True,  # INT8量化，减小模型体积
)

# 输出: best_saved_model/best_int8.tflite
```

### 第2步: 创建Android项目

使用Android Studio创建新项目：

```
项目名称: GameBotAI
包名: com.gamebot.ai
最低SDK: API 26 (Android 8.0)
```

### 第3步: 添加依赖

在 `app/build.gradle.kts` 添加：

```kotlin
dependencies {
    // TensorFlow Lite
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    // 或使用ONNX Runtime
    // implementation("com.microsoft.onnxruntime:onnxruntime-android:1.16.0")

    // OpenCV (可选)
    implementation("org.opencv:opencv:4.8.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
}
```

### 第4步: 配置权限

在 `AndroidManifest.xml` 添加：

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- 必需权限 -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.GameBotAI">

        <!-- 主Activity -->
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- 无障碍服务 -->
        <service
            android:name=".service.GameBotAccessibilityService"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_service_config" />
        </service>

    </application>
</manifest>
```

### 第5步: 核心代码实现

我将为你创建完整的Android项目结构和代码...

## 快速原型方案（Python转APK）

如果你想快速验证，可以先用Python + Kivy打包：

### 1. 安装打包工具

```bash
pip install kivy buildozer python-for-android
```

### 2. 创建Kivy界面

我会为你创建一个简化版的Kivy应用...

### 3. 打包APK

```bash
# 在Linux环境（或WSL）
buildozer android debug

# 输出: bin/GameBot-0.1-debug.apk
```

## 方案对比

| 特性 | Python+Kivy | Android原生 |
|------|------------|------------|
| 开发难度 | ⭐⭐ | ⭐⭐⭐⭐ |
| 性能 | ⭐⭐ | ⭐⭐⭐⭐⭐ |
| APK大小 | 200MB+ | 30-50MB |
| 用户体验 | ⭐⭐ | ⭐⭐⭐⭐⭐ |
| 推荐度 | 原型测试 | 正式使用 |

## 推荐路线

**短期（1-2天）**:
- 使用当前PC控制方案测试功能
- 确认游戏兼容性

**中期（1周）**:
- 转换模型为TFLite
- 开发Android原生APP
- 实现无障碍服务

**长期**:
- 优化性能
- 完善UI
- 发布到应用商店

## 接下来我可以帮你

1. ✅ **创建完整的Android项目代码**（推荐）
2. ✅ **创建Kivy快速原型**
3. ✅ **提供模型转换脚本**
4. ✅ **详细的无障碍服务教程**

你想先做哪一个？
