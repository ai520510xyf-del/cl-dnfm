"""
快速启动脚本 - 测试基础功能
Quick Start Script - Test Basic Functions
"""

import sys
from pathlib import Path

sys.path.append(str(Path(__file__).parent.parent))

from src.capture.screen_capture import CaptureManager
import cv2
import time


def test_connection():
    """测试设备连接"""
    print("=== 测试设备连接 ===\n")

    manager = CaptureManager(platform="android")

    if manager.connect():
        print("✓ 设备连接成功\n")

        # 获取屏幕信息
        width, height = manager.get_screen_size()
        print(f"屏幕尺寸: {width}x{height}\n")

        # 测试截图
        print("测试截图...")
        for i in range(5):
            frame = manager.get_frame()
            if frame is not None:
                print(f"✓ 截图 {i+1}: {frame.shape}")

                # 显示预览
                cv2.imshow("Preview", frame)
                cv2.waitKey(500)
            else:
                print(f"✗ 截图 {i+1} 失败")

            time.sleep(0.5)

        cv2.destroyAllWindows()
        manager.disconnect()

        print("\n✓ 基础功能测试通过!")
        print("\n下一步:")
        print("1. 收集游戏截图: python tools/collect_data.py")
        print("2. 使用labelImg标注数据")
        print("3. 划分数据集: python tools/split_dataset.py")
        print("4. 训练模型: python tools/train_yolo.py")
        print("5. 运行机器人: python main.py")

    else:
        print("✗ 设备连接失败\n")
        print("检查清单:")
        print("1. 手机是否通过USB连接?")
        print("2. 是否开启USB调试?")
        print("3. 是否安装adb? 运行: adb devices")
        print("4. 是否安装uiautomator2? 运行: pip install uiautomator2")
        print("5. (首次使用) 是否初始化uiautomator2? 运行:")
        print("   python -m uiautomator2 init")


if __name__ == "__main__":
    test_connection()
