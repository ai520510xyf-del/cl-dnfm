# Android Studio完整开发指南

恭喜！你已经拥有了一个**完整的Android原生项目**！🎉

## 📦 项目内容

已创建的文件：

```
android/
├── build.gradle.kts              # 项目级Gradle配置
├── settings.gradle.kts           # Gradle设置
├── app/
    ├── build.gradle.kts          # 模块级Gradle配置
    ├── proguard-rules.pro        # 混淆规则
    └── src/main/
        ├── AndroidManifest.xml   # Android清单文件
        ├── java/com/gamebot/ai/
        │   ├── MainActivity.kt                              # 主界面
        │   ├── controller/GameController.kt                 # 游戏控制器
        │   ├── detector/YoloDetector.kt                     # YOLO检测器
        │   ├── service/GameBotAccessibilityService.kt       # 无障碍服务
        │   └── strategy/GameStrategy.kt                     # 游戏策略
        ├── res/
        │   ├── layout/activity_main.xml        # 主界面布局
        │   ├── values/strings.xml              # 字符串资源
        │   ├── values/colors.xml               # 颜色资源
        │   ├── values/themes.xml               # 主题
        │   └── xml/
        │       ├── accessibility_service_config.xml  # 无障碍配置
        │       ├── backup_rules.xml
        │       └── data_extraction_rules.xml
        └── assets/                             # 资源文件（放TFLite模型）
```

---

## 🚀 快速开始（20分钟）

### 第1步：安装Android Studio（5分钟）

1. **下载Android Studio**
   - 访问：https://developer.android.com/studio
   - 选择适合你的macOS版本
   - 下载大小约1GB

2. **安装**
   - 打开DMG文件
   - 拖动到Applications文件夹
   - 首次启动会下载SDK（约2GB）

---

### 第2步：导入项目（2分钟）

1. **打开Android Studio**

2. **导入项目**：
   - 点击 "Open"
   - 选择目录：
     ```
     /Users/anker/Desktop/work/mydocuments/project/cl-dnfm/android
     ```
   - 点击 "Open"

3. **等待Gradle同步**（第一次会比较慢，需要下载依赖）
   - 底部会显示 "Gradle sync in progress..."
   - 等待完成（约3-5分钟）

---

### 第3步：转换模型（5分钟）

在项目根目录运行：

```bash
cd /Users/anker/Desktop/work/mydocuments/project/cl-dnfm

# 转换模型（如果你已经训练好了）
python android/convert_model.py \
  --model models/best.pt \
  --format tflite \
  --img-size 320
```

这会生成 `android/models/game_model_320.tflite`

---

### 第4步：添加模型到项目（1分钟）

```bash
# 复制模型到assets目录
mkdir -p android/app/src/main/assets
cp android/models/game_model_320.tflite android/app/src/main/assets/
```

---

### 第5步：连接手机（2分钟）

#### 方式A：真机调试（推荐）

1. **开启手机开发者选项**：
   - 设置 → 关于手机
   - 连续点击"版本号"7次
   - 返回设置，进入"开发者选项"

2. **开启USB调试**：
   - 开发者选项 → USB调试 → 开启

3. **连接手机**：
   - USB数据线连接手机和电脑
   - 手机上点击"允许USB调试"

4. **验证连接**：
   ```bash
   adb devices
   ```
   应该看到你的设备

#### 方式B：模拟器（备选）

1. 在Android Studio中：
   - 工具栏点击 "AVD Manager"
   - 点击 "Create Virtual Device"
   - 选择 "Pixel 6"
   - 下载并选择 "API 34"
   - 创建并启动

---

### 第6步：运行APP（5分钟）

1. **在Android Studio中**：
   - 点击顶部工具栏的绿色▶️按钮
   - 或按快捷键 Shift + F10

2. **选择设备**：
   - 选择你的手机或模拟器
   - 点击 "OK"

3. **等待安装**：
   - APK会自动编译并安装到手机
   - 首次编译需要5-10分钟

4. **APP启动**：
   - 安装完成后自动启动
   - 看到 "AI Game Bot" 界面

---

## 📱 使用APP

### 第1步：开启无障碍服务

1. **点击APP中的"开启无障碍服务"按钮**

2. **在设置中找到"AI Game Bot"**：
   - 设置 → 辅助功能 → 无障碍
   - 找到 "AI Game Bot"
   - 打开开关

3. **授权**：
   - 点击"允许"
   - 接受权限请求

### 第2步：启动机器人

1. **返回APP**

2. **点击"启动机器人"**：
   - APP开始加载YOLO模型
   - 状态显示"运行中"

3. **打开游戏**：
   - 切换到你的游戏
   - 机器人会自动开始工作！

### 第3步：监控状态

- 可以随时切回APP查看：
  - FPS（帧率）
  - 处理的帧数
  - 运行状态

### 第4步：停止

- 点击"停止机器人"按钮

---

## 🔧 自定义开发

### 修改检测类别

编辑 `YoloDetector.kt` 中的类别列表：

```kotlin
private fun getClassName(classId: Int): String {
    val defaultNames = listOf(
        "enemy",          // 0
        "skill_button",   // 1
        "start_button",   // 2
        "claim_button",   // 3
        // 添加你自己的类别...
    )
    // ...
}
```

### 修改游戏策略

编辑 `GameStrategy.kt`:

```kotlin
fun makeDecision(screenshot: Bitmap?, detections: List<Detection>): GameAction {
    // 自定义你的决策逻辑
    when {
        hasDetection(detections, "你的类别") -> {
            // 你的操作
        }
    }
}
```

### 调整性能

在 `GameBotAccessibilityService.kt` 中：

```kotlin
// 修改FPS限制
val frameTime = 33L // 30 FPS
// 改为 50L = 20 FPS（更省电）
// 改为 16L = 60 FPS（更流畅但更耗电）
```

---

## 🐛 调试技巧

### 查看日志

在Android Studio底部的 "Logcat" 面板：

```
过滤: GameBotService
```

会看到：
- 检测结果
- FPS信息
- 执行的操作
- 错误信息

### 常见问题

**Q: APP安装后闪退**
```bash
# 查看崩溃日志
adb logcat | grep AndroidRuntime
```

**Q: 无法检测到目标**
- 检查模型文件是否在assets目录
- 检查模型输入尺寸是否匹配（320x320）
- 查看Logcat确认模型加载成功

**Q: FPS很低**
- 降低检测频率
- 使用更小的模型
- 降低输入图像尺寸

**Q: 无障碍服务无法开启**
- 重启手机
- 重新安装APP
- 检查是否有其他安全软件拦截

---

## 📊 性能优化

### 1. 模型优化

```python
# 使用INT8量化
python android/convert_model.py \
  --model models/best.pt \
  --format tflite \
  --img-size 320  # 已包含INT8量化
```

### 2. 使用GPU加速

在 `YoloDetector.kt` 中取消注释：

```kotlin
val options = Interpreter.Options().apply {
    // 取消注释这行
    addDelegate(GpuDelegate())
}
```

需要添加依赖（已包含）：
```kotlin
implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")
```

### 3. 降低分辨率

```kotlin
// 在YoloDetector中修改
val resizedBitmap = Bitmap.createScaledBitmap(
    bitmap,
    160,  // 从320改为160
    160,
    true
)
```

---

## 📦 打包发布

### Debug版本（测试用）

```bash
# 在Android Studio中
Build → Build Bundle(s) / APK(s) → Build APK(s)

# 或命令行
cd android
./gradlew assembleDebug

# 输出: app/build/outputs/apk/debug/app-debug.apk
```

### Release版本（正式发布）

1. **生成签名密钥**：
```bash
keytool -genkey -v -keystore release-key.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias gamebot
```

2. **配置签名** (在 `app/build.gradle.kts`):
```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("../release-key.jks")
            storePassword = "你的密码"
            keyAlias = "gamebot"
            keyPassword = "你的密码"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            // ...
        }
    }
}
```

3. **构建**:
```bash
./gradlew assembleRelease

# 输出: app/build/outputs/apk/release/app-release.apk
```

---

## 🎓 学习资源

### Android开发基础
- 官方文档: https://developer.android.com
- Kotlin文档: https://kotlinlang.org/docs/home.html

### TensorFlow Lite
- 官方指南: https://www.tensorflow.org/lite/android

### 无障碍服务
- 开发指南: https://developer.android.com/guide/topics/ui/accessibility/service

---

## 📋 项目清单

在Android Studio中完成这些任务：

- [ ] 导入项目并同步Gradle
- [ ] 转换YOLO模型为TFLite
- [ ] 将模型文件放入assets目录
- [ ] 连接真机或创建模拟器
- [ ] 编译并运行APP
- [ ] 开启无障碍服务
- [ ] 测试机器人功能
- [ ] 根据你的游戏调整策略
- [ ] 优化性能
- [ ] 打包发布APK

---

## ✅ 完成后

你现在拥有：

✅ 完整的Android原生项目
✅ YOLO目标检测功能
✅ 无障碍服务控制
✅ 可自定义的游戏策略
✅ 性能优秀的APK（30-50MB）
✅ 25-30 FPS的运行速度

---

## 🆘 需要帮助？

如果遇到问题：

1. **查看错误日志**（Logcat）
2. **阅读相关文档**
3. **告诉我具体错误**，我会帮你解决

现在开始你的Android开发之旅吧！🚀
