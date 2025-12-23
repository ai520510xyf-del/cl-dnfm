"""
游戏控制器模块 - 模拟触摸和手势操作
Game Controller Module - Touch and Gesture Simulation
"""

import time
import random
from abc import ABC, abstractmethod
from typing import Optional, Tuple, List
import numpy as np


class BaseController(ABC):
    """游戏控制器基类"""

    def __init__(self, device):
        """
        Args:
            device: 设备对象 (来自capture模块)
        """
        self.device = device
        self.screen_width = 0
        self.screen_height = 0

    @abstractmethod
    def tap(self, x: int, y: int, duration: float = 0.05):
        """点击操作"""
        pass

    @abstractmethod
    def swipe(
        self,
        start_x: int,
        start_y: int,
        end_x: int,
        end_y: int,
        duration: float = 0.5
    ):
        """滑动操作"""
        pass

    @abstractmethod
    def long_press(self, x: int, y: int, duration: float = 1.0):
        """长按操作"""
        pass

    def set_screen_size(self, width: int, height: int):
        """设置屏幕尺寸"""
        self.screen_width = width
        self.screen_height = height


class AndroidController(BaseController):
    """Android游戏控制器"""

    def __init__(self, device):
        """
        Args:
            device: uiautomator2设备对象
        """
        super().__init__(device)

    def tap(self, x: int, y: int, duration: float = 0.05):
        """
        点击屏幕

        Args:
            x: X坐标
            y: Y坐标
            duration: 点击持续时间(秒)
        """
        try:
            # 添加随机偏移模拟人类操作
            x_offset = random.randint(-2, 2)
            y_offset = random.randint(-2, 2)

            self.device.click(x + x_offset, y + y_offset)
            time.sleep(duration)

        except Exception as e:
            print(f"✗ 点击失败 ({x}, {y}): {e}")

    def swipe(
        self,
        start_x: int,
        start_y: int,
        end_x: int,
        end_y: int,
        duration: float = 0.5
    ):
        """
        滑动操作

        Args:
            start_x, start_y: 起始坐标
            end_x, end_y: 结束坐标
            duration: 滑动持续时间(秒)
        """
        try:
            self.device.swipe(
                start_x, start_y,
                end_x, end_y,
                duration
            )

        except Exception as e:
            print(f"✗ 滑动失败: {e}")

    def long_press(self, x: int, y: int, duration: float = 1.0):
        """
        长按操作

        Args:
            x, y: 坐标
            duration: 长按持续时间(秒)
        """
        try:
            self.device.long_click(x, y, duration)

        except Exception as e:
            print(f"✗ 长按失败: {e}")

    def swipe_smooth(
        self,
        start_x: int,
        start_y: int,
        end_x: int,
        end_y: int,
        duration: float = 0.5,
        steps: int = 20
    ):
        """
        平滑滑动 - 模拟贝塞尔曲线

        Args:
            start_x, start_y: 起始坐标
            end_x, end_y: 结束坐标
            duration: 持续时间
            steps: 滑动步数
        """
        try:
            # 生成贝塞尔曲线控制点
            control_x = (start_x + end_x) / 2 + random.randint(-50, 50)
            control_y = (start_y + end_y) / 2 + random.randint(-50, 50)

            points = []
            for i in range(steps + 1):
                t = i / steps
                # 二次贝塞尔曲线
                x = (1 - t) ** 2 * start_x + 2 * (1 - t) * t * control_x + t ** 2 * end_x
                y = (1 - t) ** 2 * start_y + 2 * (1 - t) * t * control_y + t ** 2 * end_y
                points.append((int(x), int(y)))

            # 执行滑动
            step_duration = duration / steps
            for i in range(len(points) - 1):
                x1, y1 = points[i]
                x2, y2 = points[i + 1]
                self.device.swipe(x1, y1, x2, y2, step_duration)

        except Exception as e:
            print(f"✗ 平滑滑动失败: {e}")

    def multi_tap(self, positions: List[Tuple[int, int]], interval: float = 0.1):
        """
        多点连续点击

        Args:
            positions: 坐标列表 [(x1, y1), (x2, y2), ...]
            interval: 点击间隔(秒)
        """
        for x, y in positions:
            self.tap(x, y)
            time.sleep(interval)


class IOSController(BaseController):
    """iOS游戏控制器"""

    def __init__(self, device):
        """
        Args:
            device: WebDriverAgent客户端对象
        """
        super().__init__(device)

    def tap(self, x: int, y: int, duration: float = 0.05):
        """点击屏幕"""
        try:
            self.device.click(x, y)
            time.sleep(duration)

        except Exception as e:
            print(f"✗ 点击失败: {e}")

    def swipe(
        self,
        start_x: int,
        start_y: int,
        end_x: int,
        end_y: int,
        duration: float = 0.5
    ):
        """滑动操作"""
        try:
            self.device.swipe(start_x, start_y, end_x, end_y, duration)

        except Exception as e:
            print(f"✗ 滑动失败: {e}")

    def long_press(self, x: int, y: int, duration: float = 1.0):
        """长按操作"""
        try:
            self.device.press(x, y, duration)

        except Exception as e:
            print(f"✗ 长按失败: {e}")


class ControllerManager:
    """控制器管理器 - 统一接口"""

    def __init__(self, platform: str, device):
        """
        Args:
            platform: 平台类型 "android" 或 "ios"
            device: 设备对象
        """
        self.platform = platform.lower()

        if self.platform == "android":
            self.controller = AndroidController(device)
        elif self.platform == "ios":
            self.controller = IOSController(device)
        else:
            raise ValueError(f"不支持的平台: {platform}")

    def tap(self, x: int, y: int, duration: float = 0.05):
        """点击"""
        self.controller.tap(x, y, duration)

    def tap_random(self, x: int, y: int, radius: int = 5, duration: float = 0.05):
        """
        随机点击 - 在目标点周围随机偏移

        Args:
            x, y: 目标点
            radius: 随机半径
            duration: 点击持续时间
        """
        offset_x = random.randint(-radius, radius)
        offset_y = random.randint(-radius, radius)
        self.tap(x + offset_x, y + offset_y, duration)

    def swipe(
        self,
        start_x: int,
        start_y: int,
        end_x: int,
        end_y: int,
        duration: float = 0.5
    ):
        """滑动"""
        self.controller.swipe(start_x, start_y, end_x, end_y, duration)

    def long_press(self, x: int, y: int, duration: float = 1.0):
        """长按"""
        self.controller.long_press(x, y, duration)

    def swipe_smooth(self, start_x: int, start_y: int, end_x: int, end_y: int, duration: float = 0.5):
        """平滑滑动 (仅Android)"""
        if isinstance(self.controller, AndroidController):
            self.controller.swipe_smooth(start_x, start_y, end_x, end_y, duration)
        else:
            self.swipe(start_x, start_y, end_x, end_y, duration)

    def multi_tap(self, positions: List[Tuple[int, int]], interval: float = 0.1):
        """多点连续点击 (仅Android)"""
        if isinstance(self.controller, AndroidController):
            self.controller.multi_tap(positions, interval)
        else:
            for x, y in positions:
                self.tap(x, y)
                time.sleep(interval)

    def set_screen_size(self, width: int, height: int):
        """设置屏幕尺寸"""
        self.controller.set_screen_size(width, height)

    # 便捷方法
    def tap_relative(self, rx: float, ry: float, duration: float = 0.05):
        """
        相对坐标点击

        Args:
            rx: 相对X坐标 (0.0-1.0)
            ry: 相对Y坐标 (0.0-1.0)
            duration: 点击持续时间
        """
        x = int(rx * self.controller.screen_width)
        y = int(ry * self.controller.screen_height)
        self.tap(x, y, duration)

    def swipe_direction(self, direction: str, distance: int = 300, duration: float = 0.3):
        """
        方向滑动

        Args:
            direction: 方向 "up", "down", "left", "right"
            distance: 滑动距离(像素)
            duration: 持续时间
        """
        cx = self.controller.screen_width // 2
        cy = self.controller.screen_height // 2

        if direction == "up":
            self.swipe(cx, cy, cx, cy - distance, duration)
        elif direction == "down":
            self.swipe(cx, cy, cx, cy + distance, duration)
        elif direction == "left":
            self.swipe(cx, cy, cx - distance, cy, duration)
        elif direction == "right":
            self.swipe(cx, cy, cx + distance, cy, duration)
        else:
            print(f"✗ 未知方向: {direction}")

    def wait_random(self, min_sec: float = 0.5, max_sec: float = 1.5):
        """随机等待 - 模拟人类操作间隔"""
        wait_time = random.uniform(min_sec, max_sec)
        time.sleep(wait_time)


# 测试代码
if __name__ == "__main__":
    print("=== 测试游戏控制器 ===\n")

    # 示例: 连接设备并测试控制
    try:
        import uiautomator2 as u2

        device = u2.connect()
        controller = ControllerManager("android", device)

        # 获取屏幕尺寸
        width, height = device.window_size()
        controller.set_screen_size(width, height)

        print(f"✓ 设备已连接")
        print(f"  屏幕尺寸: {width}x{height}\n")

        # 测试点击
        print("测试点击屏幕中心...")
        controller.tap(width // 2, height // 2)
        time.sleep(1)

        # 测试滑动
        print("测试向上滑动...")
        controller.swipe_direction("up", distance=200)
        time.sleep(1)

        print("\n✓ 测试完成")

    except Exception as e:
        print(f"✗ 测试失败: {e}")
        print("请确保设备已连接并开启USB调试")
