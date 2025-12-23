"""
屏幕捕获模块 - 支持Android和iOS设备
Screen Capture Module - Support for Android and iOS devices
"""

import time
import numpy as np
from abc import ABC, abstractmethod
from typing import Optional, Tuple
import cv2


class BaseCapture(ABC):
    """屏幕捕获基类"""

    def __init__(self, device_id: Optional[str] = None):
        self.device_id = device_id
        self.is_connected = False
        self.screen_size: Optional[Tuple[int, int]] = None

    @abstractmethod
    def connect(self) -> bool:
        """连接设备"""
        pass

    @abstractmethod
    def disconnect(self):
        """断开设备连接"""
        pass

    @abstractmethod
    def get_screenshot(self) -> Optional[np.ndarray]:
        """获取屏幕截图"""
        pass

    @abstractmethod
    def get_screen_size(self) -> Tuple[int, int]:
        """获取屏幕尺寸"""
        pass


class AndroidCapture(BaseCapture):
    """Android设备屏幕捕获"""

    def __init__(self, device_id: Optional[str] = None):
        super().__init__(device_id)
        self.device = None

    def connect(self) -> bool:
        """连接Android设备"""
        try:
            import uiautomator2 as u2

            if self.device_id:
                self.device = u2.connect(self.device_id)
            else:
                self.device = u2.connect()

            self.is_connected = True
            self.screen_size = self.get_screen_size()
            print(f"✓ Android设备已连接: {self.device.device_info.get('productName', 'Unknown')}")
            print(f"  屏幕尺寸: {self.screen_size}")
            return True

        except Exception as e:
            print(f"✗ Android设备连接失败: {e}")
            self.is_connected = False
            return False

    def disconnect(self):
        """断开连接"""
        if self.device:
            self.device = None
        self.is_connected = False
        print("✓ 设备已断开")

    def get_screenshot(self) -> Optional[np.ndarray]:
        """获取屏幕截图 - 返回BGR格式的numpy数组"""
        if not self.is_connected or not self.device:
            print("✗ 设备未连接")
            return None

        try:
            # 使用uiautomator2截图
            screenshot = self.device.screenshot(format='opencv')
            return screenshot

        except Exception as e:
            print(f"✗ 截图失败: {e}")
            return None

    def get_screen_size(self) -> Tuple[int, int]:
        """获取屏幕尺寸 (width, height)"""
        if self.device:
            info = self.device.window_size()
            return (info[0], info[1])
        return (0, 0)

    def get_screenshot_fast(self) -> Optional[np.ndarray]:
        """快速截图 - 使用minicap (需要额外配置)"""
        # TODO: 实现minicap快速截图
        return self.get_screenshot()


class IOSCapture(BaseCapture):
    """iOS设备屏幕捕获 (需要WebDriverAgent)"""

    def __init__(self, device_id: Optional[str] = None, wda_port: int = 8100):
        super().__init__(device_id)
        self.wda_port = wda_port
        self.client = None

    def connect(self) -> bool:
        """连接iOS设备"""
        try:
            import wda

            # 连接到WebDriverAgent
            wda_url = f"http://localhost:{self.wda_port}"
            self.client = wda.Client(wda_url)

            # 测试连接
            status = self.client.status()

            self.is_connected = True
            self.screen_size = self.get_screen_size()
            print(f"✓ iOS设备已连接")
            print(f"  屏幕尺寸: {self.screen_size}")
            return True

        except Exception as e:
            print(f"✗ iOS设备连接失败: {e}")
            print("  提示: 确保WebDriverAgent已启动")
            self.is_connected = False
            return False

    def disconnect(self):
        """断开连接"""
        if self.client:
            self.client = None
        self.is_connected = False
        print("✓ 设备已断开")

    def get_screenshot(self) -> Optional[np.ndarray]:
        """获取屏幕截图"""
        if not self.is_connected or not self.client:
            print("✗ 设备未连接")
            return None

        try:
            # 获取截图 (PIL Image)
            screenshot = self.client.screenshot(format='opencv')
            return screenshot

        except Exception as e:
            print(f"✗ 截图失败: {e}")
            return None

    def get_screen_size(self) -> Tuple[int, int]:
        """获取屏幕尺寸"""
        if self.client:
            window_size = self.client.window_size()
            return (window_size.width, window_size.height)
        return (0, 0)


class CaptureManager:
    """屏幕捕获管理器 - 统一接口"""

    def __init__(self, platform: str = "android", device_id: Optional[str] = None):
        """
        初始化捕获管理器

        Args:
            platform: 平台类型 "android" 或 "ios"
            device_id: 设备ID (可选)
        """
        self.platform = platform.lower()
        self.capture: Optional[BaseCapture] = None

        if self.platform == "android":
            self.capture = AndroidCapture(device_id)
        elif self.platform == "ios":
            self.capture = IOSCapture(device_id)
        else:
            raise ValueError(f"不支持的平台: {platform}")

    def connect(self) -> bool:
        """连接设备"""
        return self.capture.connect()

    def disconnect(self):
        """断开设备"""
        if self.capture:
            self.capture.disconnect()

    def get_frame(self) -> Optional[np.ndarray]:
        """获取单帧画面"""
        return self.capture.get_screenshot()

    def get_screen_size(self) -> Tuple[int, int]:
        """获取屏幕尺寸"""
        return self.capture.get_screen_size()

    def is_connected(self) -> bool:
        """检查连接状态"""
        return self.capture.is_connected if self.capture else False


# 测试代码
if __name__ == "__main__":
    # 测试Android设备
    print("=== 测试屏幕捕获模块 ===\n")

    manager = CaptureManager(platform="android")

    if manager.connect():
        print("\n开始截图测试...")

        for i in range(3):
            frame = manager.get_frame()
            if frame is not None:
                print(f"✓ 截图 {i+1}: {frame.shape}")
                # 保存测试截图
                cv2.imwrite(f"test_screenshot_{i+1}.png", frame)
            time.sleep(1)

        manager.disconnect()
    else:
        print("设备连接失败，请检查:")
        print("1. 手机是否通过USB连接")
        print("2. 是否开启USB调试")
        print("3. 是否安装adb工具")
        print("4. 运行: adb devices 检查设备")
