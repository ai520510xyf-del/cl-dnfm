"""
YOLO目标检测模块 - 游戏元素识别
YOLO Object Detection Module - Game Element Recognition
"""

import cv2
import numpy as np
from typing import List, Dict, Optional, Tuple
from pathlib import Path
import time


class Detection:
    """检测结果类"""

    def __init__(
        self,
        class_id: int,
        class_name: str,
        confidence: float,
        bbox: Tuple[int, int, int, int],
        center: Tuple[int, int]
    ):
        """
        Args:
            class_id: 类别ID
            class_name: 类别名称
            confidence: 置信度
            bbox: 边界框 (x1, y1, x2, y2)
            center: 中心点 (cx, cy)
        """
        self.class_id = class_id
        self.class_name = class_name
        self.confidence = confidence
        self.bbox = bbox
        self.center = center

    def __repr__(self):
        return (f"Detection(class={self.class_name}, "
                f"conf={self.confidence:.2f}, "
                f"bbox={self.bbox}, "
                f"center={self.center})")


class YOLODetector:
    """YOLO检测器"""

    def __init__(
        self,
        model_path: str,
        conf_threshold: float = 0.25,
        iou_threshold: float = 0.45,
        use_gpu: bool = True
    ):
        """
        初始化YOLO检测器

        Args:
            model_path: 模型路径 (.pt 或 .onnx)
            conf_threshold: 置信度阈值
            iou_threshold: NMS的IOU阈值
            use_gpu: 是否使用GPU
        """
        self.model_path = Path(model_path)
        self.conf_threshold = conf_threshold
        self.iou_threshold = iou_threshold
        self.use_gpu = use_gpu

        self.model = None
        self.class_names: List[str] = []
        self.model_type = self._detect_model_type()

        self._load_model()

    def _detect_model_type(self) -> str:
        """检测模型类型"""
        suffix = self.model_path.suffix.lower()
        if suffix == '.pt':
            return 'ultralytics'
        elif suffix == '.onnx':
            return 'onnx'
        else:
            raise ValueError(f"不支持的模型格式: {suffix}")

    def _load_model(self):
        """加载模型"""
        if not self.model_path.exists():
            raise FileNotFoundError(f"模型文件不存在: {self.model_path}")

        print(f"正在加载模型: {self.model_path}")

        if self.model_type == 'ultralytics':
            self._load_ultralytics_model()
        elif self.model_type == 'onnx':
            self._load_onnx_model()

        print(f"✓ 模型加载成功")
        print(f"  类别数量: {len(self.class_names)}")
        print(f"  类别列表: {self.class_names}")

    def _load_ultralytics_model(self):
        """加载Ultralytics YOLO模型"""
        try:
            from ultralytics import YOLO

            self.model = YOLO(str(self.model_path))

            # 设置设备
            if self.use_gpu:
                import torch
                if torch.cuda.is_available():
                    self.model.to('cuda')
                    print("  使用GPU加速")
                else:
                    print("  GPU不可用，使用CPU")
            else:
                print("  使用CPU")

            # 获取类别名称
            self.class_names = list(self.model.names.values())

        except ImportError:
            raise ImportError("请安装ultralytics: pip install ultralytics")

    def _load_onnx_model(self):
        """加载ONNX模型"""
        try:
            import onnxruntime as ort

            # 设置会话选项
            providers = ['CUDAExecutionProvider', 'CPUExecutionProvider'] if self.use_gpu else ['CPUExecutionProvider']

            self.model = ort.InferenceSession(
                str(self.model_path),
                providers=providers
            )

            print(f"  使用提供者: {self.model.get_providers()}")

            # TODO: 从配置文件加载类别名称
            self.class_names = []

        except ImportError:
            raise ImportError("请安装onnxruntime: pip install onnxruntime-gpu")

    def detect(
        self,
        image: np.ndarray,
        filter_classes: Optional[List[str]] = None
    ) -> List[Detection]:
        """
        检测图像中的目标

        Args:
            image: 输入图像 (BGR格式)
            filter_classes: 过滤特定类别 (可选)

        Returns:
            检测结果列表
        """
        if self.model is None:
            raise RuntimeError("模型未加载")

        if self.model_type == 'ultralytics':
            return self._detect_ultralytics(image, filter_classes)
        elif self.model_type == 'onnx':
            return self._detect_onnx(image, filter_classes)

    def _detect_ultralytics(
        self,
        image: np.ndarray,
        filter_classes: Optional[List[str]] = None
    ) -> List[Detection]:
        """使用Ultralytics模型检测"""
        # 推理
        results = self.model(
            image,
            conf=self.conf_threshold,
            iou=self.iou_threshold,
            verbose=False
        )[0]

        detections = []

        # 解析结果
        for box in results.boxes:
            class_id = int(box.cls)
            class_name = self.class_names[class_id]
            confidence = float(box.conf)

            # 过滤类别
            if filter_classes and class_name not in filter_classes:
                continue

            # 边界框
            x1, y1, x2, y2 = map(int, box.xyxy[0].tolist())
            bbox = (x1, y1, x2, y2)

            # 中心点
            cx = (x1 + x2) // 2
            cy = (y1 + y2) // 2
            center = (cx, cy)

            detection = Detection(
                class_id=class_id,
                class_name=class_name,
                confidence=confidence,
                bbox=bbox,
                center=center
            )
            detections.append(detection)

        return detections

    def _detect_onnx(
        self,
        image: np.ndarray,
        filter_classes: Optional[List[str]] = None
    ) -> List[Detection]:
        """使用ONNX模型检测"""
        # TODO: 实现ONNX推理
        raise NotImplementedError("ONNX推理尚未实现")

    def draw_detections(
        self,
        image: np.ndarray,
        detections: List[Detection],
        show_conf: bool = True
    ) -> np.ndarray:
        """
        在图像上绘制检测结果

        Args:
            image: 原始图像
            detections: 检测结果
            show_conf: 是否显示置信度

        Returns:
            绘制后的图像
        """
        result_image = image.copy()

        for det in detections:
            x1, y1, x2, y2 = det.bbox
            cx, cy = det.center

            # 绘制边界框
            color = self._get_class_color(det.class_id)
            cv2.rectangle(result_image, (x1, y1), (x2, y2), color, 2)

            # 绘制中心点
            cv2.circle(result_image, (cx, cy), 5, color, -1)

            # 绘制标签
            label = det.class_name
            if show_conf:
                label += f" {det.confidence:.2f}"

            # 文字背景
            (label_w, label_h), _ = cv2.getTextSize(label, cv2.FONT_HERSHEY_SIMPLEX, 0.5, 1)
            cv2.rectangle(result_image, (x1, y1 - label_h - 10), (x1 + label_w, y1), color, -1)

            # 文字
            cv2.putText(
                result_image,
                label,
                (x1, y1 - 5),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.5,
                (255, 255, 255),
                1
            )

        return result_image

    def _get_class_color(self, class_id: int) -> Tuple[int, int, int]:
        """获取类别颜色"""
        colors = [
            (255, 0, 0),    # 蓝色
            (0, 255, 0),    # 绿色
            (0, 0, 255),    # 红色
            (255, 255, 0),  # 青色
            (255, 0, 255),  # 品红
            (0, 255, 255),  # 黄色
        ]
        return colors[class_id % len(colors)]

    def get_detections_by_class(
        self,
        detections: List[Detection],
        class_name: str
    ) -> List[Detection]:
        """获取指定类别的检测结果"""
        return [det for det in detections if det.class_name == class_name]

    def find_nearest_detection(
        self,
        detections: List[Detection],
        point: Tuple[int, int]
    ) -> Optional[Detection]:
        """查找距离指定点最近的检测目标"""
        if not detections:
            return None

        min_dist = float('inf')
        nearest = None

        px, py = point

        for det in detections:
            cx, cy = det.center
            dist = np.sqrt((cx - px) ** 2 + (cy - py) ** 2)

            if dist < min_dist:
                min_dist = dist
                nearest = det

        return nearest


class DetectionTracker:
    """检测目标跟踪器 - 用于多帧目标关联"""

    def __init__(self, max_frames: int = 30):
        """
        Args:
            max_frames: 目标最大丢失帧数
        """
        self.max_frames = max_frames
        self.tracked_objects: Dict[int, List[Detection]] = {}
        self.next_id = 0

    def update(self, detections: List[Detection]) -> Dict[int, Detection]:
        """
        更新跟踪器

        Args:
            detections: 当前帧的检测结果

        Returns:
            跟踪ID到检测结果的映射
        """
        # TODO: 实现简单的IOU跟踪算法
        # 这里提供一个简化版本
        tracked = {}
        for det in detections:
            tracked[self.next_id] = det
            self.next_id += 1
        return tracked


# 测试代码
if __name__ == "__main__":
    print("=== 测试YOLO检测器 ===\n")

    # 示例: 加载模型并检测
    model_path = "models/best.pt"

    if Path(model_path).exists():
        detector = YOLODetector(model_path, conf_threshold=0.3)

        # 测试图像
        test_image = cv2.imread("test_image.png")

        if test_image is not None:
            print("开始检测...")
            start_time = time.time()

            detections = detector.detect(test_image)

            elapsed = time.time() - start_time
            fps = 1.0 / elapsed

            print(f"✓ 检测完成: {len(detections)} 个目标")
            print(f"  耗时: {elapsed*1000:.1f}ms")
            print(f"  FPS: {fps:.1f}")

            for det in detections:
                print(f"  - {det}")

            # 绘制结果
            result_image = detector.draw_detections(test_image, detections)
            cv2.imwrite("test_result.png", result_image)
            print("\n✓ 结果已保存到 test_result.png")
    else:
        print(f"模型文件不存在: {model_path}")
        print("请先训练模型或下载预训练模型")
