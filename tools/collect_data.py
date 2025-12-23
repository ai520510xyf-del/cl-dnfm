"""
数据收集工具 - 从游戏中自动截图
Data Collection Tool - Auto Screenshot from Game
"""

import cv2
import time
import argparse
from pathlib import Path
from datetime import datetime
import sys

sys.path.append(str(Path(__file__).parent.parent))

from src.capture.screen_capture import CaptureManager


def collect_screenshots(
    platform: str = "android",
    device_id: str = None,
    output_dir: str = "data/images",
    interval: float = 2.0,
    count: int = 100
):
    """
    自动收集游戏截图

    Args:
        platform: 平台类型
        device_id: 设备ID
        output_dir: 输出目录
        interval: 截图间隔(秒)
        count: 截图数量
    """
    # 创建输出目录
    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)

    print("=== 游戏数据收集工具 ===\n")
    print(f"平台: {platform}")
    print(f"输出目录: {output_path}")
    print(f"截图间隔: {interval}秒")
    print(f"目标数量: {count}张\n")

    # 连接设备
    capture_manager = CaptureManager(platform=platform, device_id=device_id)

    if not capture_manager.connect():
        print("✗ 设备连接失败")
        return

    try:
        print("开始收集数据...\n")
        print("提示: 在游戏中进行各种操作，包括:")
        print("  - 菜单界面")
        print("  - 战斗场景")
        print("  - 不同角色/敌人")
        print("  - 各种UI状态\n")
        print("按 Ctrl+C 停止收集\n")

        collected = 0

        while collected < count:
            # 获取截图
            frame = capture_manager.get_frame()

            if frame is not None:
                # 生成文件名
                timestamp = datetime.now().strftime("%Y%m%d_%H%M%S_%f")
                filename = f"game_{timestamp}.jpg"
                filepath = output_path / filename

                # 保存截图
                cv2.imwrite(str(filepath), frame)
                collected += 1

                print(f"✓ 已保存 [{collected}/{count}]: {filename}")

                # 显示预览 (可选)
                # cv2.imshow("Preview", frame)
                # cv2.waitKey(1)

            else:
                print("✗ 截图失败，跳过")

            # 等待间隔
            time.sleep(interval)

        print(f"\n✓ 数据收集完成! 共收集 {collected} 张截图")
        print(f"  保存位置: {output_path}")
        print("\n下一步:")
        print("1. 使用labelImg或Roboflow标注数据")
        print("2. 将标注后的数据划分为train/val/test")
        print("3. 运行训练脚本")

    except KeyboardInterrupt:
        print(f"\n\n用户中断，已收集 {collected} 张截图")

    finally:
        capture_manager.disconnect()
        cv2.destroyAllWindows()


def main():
    parser = argparse.ArgumentParser(description='游戏数据收集工具')

    parser.add_argument('--platform', type=str, default='android',
                        choices=['android', 'ios'],
                        help='平台类型 (默认: android)')
    parser.add_argument('--device', type=str, default=None,
                        help='设备ID (可选)')
    parser.add_argument('--output', type=str, default='data/images',
                        help='输出目录 (默认: data/images)')
    parser.add_argument('--interval', type=float, default=2.0,
                        help='截图间隔(秒) (默认: 2.0)')
    parser.add_argument('--count', type=int, default=100,
                        help='截图数量 (默认: 100)')

    args = parser.parse_args()

    collect_screenshots(
        platform=args.platform,
        device_id=args.device,
        output_dir=args.output,
        interval=args.interval,
        count=args.count
    )


if __name__ == "__main__":
    main()
