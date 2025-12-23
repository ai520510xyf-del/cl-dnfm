# 快速打包APK - 3种方法

## 方法1: GitHub Actions自动打包 ⭐ 推荐

**优点**: 完全自动化，不需要配置本地环境

### 步骤：

1. **推送代码到GitHub**
```bash
# 如果还没有推送
git remote add origin https://github.com/你的用户名/cl-dnfm.git
git push -u origin main
```

2. **触发构建**
   - 访问GitHub仓库
   - 点击 "Actions" 标签
   - 点击 "Build Android APK"
   - 点击 "Run workflow"

3. **下载APK**
   - 等待构建完成（约30-40分钟）
   - 在Artifacts中下载APK

---

## 方法2: Google Colab在线打包 🚀 最快

**优点**: 免费，5-10分钟完成，不需要本地环境

### 步骤：

1. **打开Colab笔记本**
   访问: https://colab.research.google.com

2. **新建笔记本，运行以下代码**

```python
# Cell 1: 安装依赖
!apt-get update
!apt-get install -y git zip unzip openjdk-17-jdk
!pip install buildozer cython==0.29.36

# Cell 2: 克隆项目
!git clone https://github.com/你的用户名/cl-dnfm.git
%cd cl-dnfm/android

# Cell 3: 打包APK
!buildozer android debug

# Cell 4: 显示APK位置
!ls -lh bin/*.apk

# Cell 5: 下载APK到本地
from google.colab import files
files.download('bin/gamebotai-0.1-debug.apk')
```

---

## 方法3: 本地Docker打包 🐳

**优点**: 可重复，环境隔离

### 前置要求：
- 安装Docker Desktop: https://www.docker.com/products/docker-desktop/

### 步骤：

1. **创建打包脚本**（已提供）

2. **运行Docker打包**
```bash
# 进入项目目录
cd /Users/anker/Desktop/work/mydocuments/project/cl-dnfm

# 运行打包
docker run --rm -v "$(pwd)":/home/user/app \
  -w /home/user/app/android \
  ubuntu:22.04 \
  bash -c "
    apt-get update && \
    apt-get install -y git zip unzip openjdk-17-jdk python3 python3-pip && \
    pip3 install buildozer cython==0.29.36 && \
    buildozer android debug
  "
```

3. **获取APK**
```bash
# APK位置
ls android/bin/*.apk
```

---

## 方法4: Replit在线打包 🌐

**优点**: 在线IDE，一键运行

### 步骤：

1. 访问 https://replit.com
2. 创建新Repl（选择Python）
3. 导入GitHub仓库
4. 在Shell中运行：
```bash
cd android
buildozer android debug
```

---

## 对比

| 方法 | 速度 | 难度 | 费用 |
|------|------|------|------|
| GitHub Actions | 30-40分钟 | ⭐ 最简单 | 免费 |
| Google Colab | 5-10分钟 | ⭐⭐ 简单 | 免费 |
| Docker | 30-40分钟 | ⭐⭐⭐ 中等 | 免费 |
| Replit | 20-30分钟 | ⭐⭐ 简单 | 免费 |

---

## 推荐顺序

1. **如果代码已在GitHub**: 用GitHub Actions（最省心）
2. **如果想立即得到APK**: 用Google Colab（最快）
3. **如果有Docker**: 用Docker（最稳定）

---

## 我现在可以帮你

告诉我你想用哪种方法，我可以：

1. **GitHub Actions**: 帮你推送代码并触发构建
2. **Google Colab**: 生成完整的Colab笔记本
3. **Docker**: 安装Docker并运行打包
4. **其他**: 提供其他方案

---

## 预期结果

成功后你会得到：
- **文件名**: `gamebotai-0.1-debug.apk`
- **大小**: 约200MB（Kivy版本）
- **可以**: 直接安装到Android手机

## 注意事项

⚠️ Kivy打包的APK：
- 首次打包需要下载1-2GB的SDK/NDK
- 体积较大（200MB+）
- 性能一般（5-10 FPS）

如果需要性能更好的APK，建议使用Android Studio开发原生APP。
