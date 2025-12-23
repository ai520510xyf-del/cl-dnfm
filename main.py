"""
AI Game Bot - 主程序入口
AI Game Bot - Main Entry Point
"""

import argparse
import time
import yaml
import cv2
from pathlib import Path

from src.capture.screen_capture import CaptureManager
from src.detector.yolo_detector import YOLODetector
from src.controller.game_controller import ControllerManager
from src.strategy.base_strategy import SimpleStrategy
from src.utils.logger import setup_logger


class GameBot:
    """游戏机器人主类"""

    def __init__(self, config_path: str = "config/default_config.yaml"):
        """
        初始化游戏机器人

        Args:
            config_path: 配置文件路径
        """
        # 加载配置
        self.config = self._load_config(config_path)

        # 设置日志
        log_dir = self.config['logging']['log_dir']
        log_file = Path(log_dir) / self.config['logging']['log_file']
        self.logger = setup_logger(log_file=str(log_file))

        self.logger.info("="*60)
        self.logger.info("AI Game Bot 启动")
        self.logger.info("="*60)

        # 初始化组件
        self.capture_manager = None
        self.detector = None
        self.controller = None
        self.strategy = None

        self.is_running = False
        self.frame_count = 0
        self.fps = 0

    def _load_config(self, config_path: str) -> dict:
        """加载配置文件"""
        with open(config_path, 'r', encoding='utf-8') as f:
            config = yaml.safe_load(f)
        return config

    def initialize(self) -> bool:
        """初始化所有组件"""
        try:
            # 初始化屏幕捕获
            self.logger.info("初始化屏幕捕获...")
            platform = self.config['device']['platform']
            device_id = self.config['device']['device_id']

            self.capture_manager = CaptureManager(
                platform=platform,
                device_id=device_id
            )

            if not self.capture_manager.connect():
                self.logger.error("设备连接失败")
                return False

            # 初始化YOLO检测器
            self.logger.info("初始化YOLO检测器...")
            model_path = self.config['model']['path']

            if not Path(model_path).exists():
                self.logger.error(f"模型文件不存在: {model_path}")
                self.logger.info("请先训练模型或下载预训练模型")
                return False

            self.detector = YOLODetector(
                model_path=model_path,
                conf_threshold=self.config['model']['conf_threshold'],
                iou_threshold=self.config['model']['iou_threshold'],
                use_gpu=self.config['model']['use_gpu']
            )

            # 初始化控制器
            self.logger.info("初始化游戏控制器...")
            device = self.capture_manager.capture.device

            self.controller = ControllerManager(platform=platform, device=device)

            screen_width, screen_height = self.capture_manager.get_screen_size()
            self.controller.set_screen_size(screen_width, screen_height)

            # 初始化策略
            self.logger.info("初始化游戏策略...")
            self.strategy = SimpleStrategy()
            self.strategy.action_cooldown = self.config['strategy']['action_cooldown']

            self.logger.info("✓ 所有组件初始化完成\n")
            return True

        except Exception as e:
            self.logger.error(f"初始化失败: {e}")
            return False

    def run(self):
        """运行游戏机器人"""
        if not self.initialize():
            self.logger.error("初始化失败，退出程序")
            return

        self.is_running = True
        self.logger.info("开始运行游戏机器人...")
        self.logger.info("按 Ctrl+C 停止\n")

        fps_limit = self.config['runtime']['fps_limit']
        frame_time = 1.0 / fps_limit

        enable_viz = self.config['runtime']['enable_visualization']
        save_screenshots = self.config['runtime']['save_screenshots']
        screenshot_interval = self.config['runtime']['screenshot_interval']

        last_time = time.time()

        try:
            while self.is_running:
                loop_start = time.time()

                # 获取游戏画面
                frame = self.capture_manager.get_frame()

                if frame is None:
                    self.logger.warning("获取画面失败")
                    time.sleep(0.5)
                    continue

                # YOLO检测
                detections = self.detector.detect(frame)

                # 策略决策
                decision = self.strategy.update(frame, detections)

                # 执行操作
                if decision:
                    self._execute_action(decision)

                # 可视化
                if enable_viz:
                    viz_frame = self.detector.draw_detections(frame, detections)

                    # 显示FPS和状态信息
                    info_text = [
                        f"FPS: {self.fps:.1f}",
                        f"Frame: {self.frame_count}",
                        f"Detections: {len(detections)}",
                        f"State: {self.strategy.current_state.value}"
                    ]

                    y_offset = 30
                    for text in info_text:
                        cv2.putText(
                            viz_frame, text, (10, y_offset),
                            cv2.FONT_HERSHEY_SIMPLEX, 0.6,
                            (0, 255, 0), 2
                        )
                        y_offset += 30

                    cv2.imshow("Game Bot", viz_frame)
                    cv2.waitKey(1)

                # 保存截图
                if save_screenshots and self.frame_count % screenshot_interval == 0:
                    screenshot_dir = Path("logs/screenshots")
                    screenshot_dir.mkdir(parents=True, exist_ok=True)
                    screenshot_path = screenshot_dir / f"frame_{self.frame_count:06d}.jpg"
                    cv2.imwrite(str(screenshot_path), frame)

                # 计算FPS
                self.frame_count += 1
                current_time = time.time()
                self.fps = 1.0 / (current_time - last_time)
                last_time = current_time

                # 日志输出
                if self.frame_count % 30 == 0:
                    self.logger.info(
                        f"Frame {self.frame_count} | FPS: {self.fps:.1f} | "
                        f"Detections: {len(detections)} | "
                        f"State: {self.strategy.current_state.value}"
                    )

                # FPS限制
                elapsed = time.time() - loop_start
                if elapsed < frame_time:
                    time.sleep(frame_time - elapsed)

        except KeyboardInterrupt:
            self.logger.info("\n用户中断")

        finally:
            self.stop()

    def _execute_action(self, decision: dict):
        """执行决策动作"""
        action = decision.get('action')
        params = decision.get('params', {})

        if action == 'tap':
            x = params.get('x')
            y = params.get('y')
            if x is not None and y is not None:
                self.controller.tap_random(x, y)
                self.logger.debug(f"执行点击: ({x}, {y})")

        elif action == 'swipe':
            start_x = params.get('start_x')
            start_y = params.get('start_y')
            end_x = params.get('end_x')
            end_y = params.get('end_y')
            if all(v is not None for v in [start_x, start_y, end_x, end_y]):
                self.controller.swipe(start_x, start_y, end_x, end_y)
                self.logger.debug(f"执行滑动: ({start_x},{start_y}) -> ({end_x},{end_y})")

        elif action == 'wait':
            pass

        else:
            self.logger.warning(f"未知动作: {action}")

    def stop(self):
        """停止游戏机器人"""
        self.is_running = False
        self.logger.info("\n停止游戏机器人...")

        if self.capture_manager:
            self.capture_manager.disconnect()

        cv2.destroyAllWindows()

        self.logger.info(f"总运行帧数: {self.frame_count}")
        self.logger.info("程序已退出")


def main():
    parser = argparse.ArgumentParser(description='AI Game Bot')

    parser.add_argument(
        '--config',
        type=str,
        default='config/default_config.yaml',
        help='配置文件路径 (默认: config/default_config.yaml)'
    )

    args = parser.parse_args()

    # 创建并运行机器人
    bot = GameBot(config_path=args.config)
    bot.run()


if __name__ == "__main__":
    main()
