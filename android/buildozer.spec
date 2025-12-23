[app]

# APP基本信息
title = AI Game Bot
package.name = gamebotai
package.domain = com.gamebot

# 源代码目录
source.dir = .
source.include_exts = py,png,jpg,kv,atlas,yaml

# 版本信息
version = 0.1
version.regex = __version__ = ['"](.*)['"]
version.filename = %(source.dir)s/main.py

# 依赖库
requirements = python3,kivy,opencv,numpy,pillow

# Android特定配置
android.permissions = INTERNET,WRITE_EXTERNAL_STORAGE,READ_EXTERNAL_STORAGE,CAMERA,BIND_ACCESSIBILITY_SERVICE,SYSTEM_ALERT_WINDOW
android.api = 31
android.minapi = 26
android.ndk = 25b
android.sdk = 31
android.accept_sdk_license = True
android.arch = arm64-v8a

# 图标和启动画面
#icon.filename = %(source.dir)s/data/icon.png
#presplash.filename = %(source.dir)s/data/presplash.png

# 方向
orientation = landscape

# 全屏
fullscreen = 0

# Android服务
# services = accessibility_service:accessibility_service.py

[buildozer]

# 日志级别
log_level = 2

# 警告忽略
warn_on_root = 1
