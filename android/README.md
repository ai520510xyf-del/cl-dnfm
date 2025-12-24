# 🎮 DNF Mobile Game AI Bot

一个能自动玩地下城与勇士(DNF)手游的AI助手

基于YOLOv8目标检测 + Android无障碍服务

---

## 🚀 快速开始

### 🟢 5分钟上手
👉 **[QUICK_USER_GUIDE.md](QUICK_USER_GUIDE.md)** - 快速入门

### 📖 完整教程
👉 **[COMPLETE_WORKFLOW_GUIDE.md](COMPLETE_WORKFLOW_GUIDE.md)** - 详细流程

---

## 📋 核心流程

```
收集游戏截图 → 标注敌人/道具 → Colab训练模型 → 下载部署 → AI自动玩游戏
```

---

## 📚 文档导航

### 用户文档
- 🟢 [QUICK_USER_GUIDE.md](QUICK_USER_GUIDE.md) - 5分钟快速上手
- 🟢 [COMPLETE_WORKFLOW_GUIDE.md](COMPLETE_WORKFLOW_GUIDE.md) - 完整教程

### 开发文档
- 🔵 [BUILD_APK.md](BUILD_APK.md) - 构建APK指南
- 🔵 [SECURITY_FIXES_REPORT.md](SECURITY_FIXES_REPORT.md) - 安全修复报告

### 测试文档
- 🟣 [CODE_CHECK_OK.md](CODE_CHECK_OK.md) - 代码检查确认
- 🟣 [WORKFLOW_TEST_REPORT.md](WORKFLOW_TEST_REPORT.md) - 测试报告

---

## ✨ 核心功能

- ✅ 智能数据收集 - 自动截取游戏画面
- ✅ 可视化标注 - 直观的拖拽标注界面
- ✅ 云端训练 - Google Colab免费GPU训练
- ✅ 实时检测 - YOLOv8目标检测 (8-12 FPS)
- ✅ 自动操作 - 智能决策和游戏操作

---

## 🛠️ 技术栈

- **Android**: Kotlin + Material Design 3
- **AI**: YOLOv8 + TensorFlow Lite
- **云**: Supabase + Google Colab
- **架构**: MVVM + Coroutines

---

## 📈 性能指标

- **FPS**: 8-12 帧/秒
- **准确率**: 85-95%
- **模型大小**: 6MB (YOLOv8n)
- **内存**: ~200MB

---

## ⚠️ 免责声明

本项目仅供学习和研究使用，不得用于任何商业用途。使用本工具可能违反游戏服务条款，使用风险自负。

---

## 🎯 推荐阅读顺序

1. **首次使用**: [QUICK_USER_GUIDE.md](QUICK_USER_GUIDE.md)
2. **详细了解**: [COMPLETE_WORKFLOW_GUIDE.md](COMPLETE_WORKFLOW_GUIDE.md)
3. **开发构建**: [BUILD_APK.md](BUILD_APK.md)

---

**最后更新**: 2025-12-24
**代码状态**: ✅ 已测试通过，可以使用
