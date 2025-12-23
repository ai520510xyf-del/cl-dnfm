# 开发指南 - Development Guide

本文档提供详细的开发指南和最佳实践。

## 目录

1. [开发流程](#开发流程)
2. [核心模块详解](#核心模块详解)
3. [自定义策略开发](#自定义策略开发)
4. [数据标注技巧](#数据标注技巧)
5. [模型训练优化](#模型训练优化)
6. [性能调优](#性能调优)
7. [调试技巧](#调试技巧)

## 开发流程

### 完整开发流程

```
1. 需求分析
   ↓
2. 游戏元素识别
   - 确定需要检测的对象
   - 定义类别和标签
   ↓
3. 数据收集
   - 收集不同场景的截图
   - 确保数据多样性
   ↓
4. 数据标注
   - 精确标注边界框
   - 保证标注质量
   ↓
5. 模型训练
   - 选择合适的模型
   - 调整超参数
   ↓
6. 策略开发
   - 实现决策逻辑
   - 测试策略效果
   ↓
7. 集成测试
   - 完整流程测试
   - 性能优化
   ↓
8. 部署运行
```

### 快速原型开发

对于新游戏，推荐以下快速原型流程:

```bash
# 1. 快速收集少量数据 (50-100张)
python tools/collect_data.py --count 50 --interval 3

# 2. 快速标注核心类别 (2-3个最重要的)
# 使用labelImg或Roboflow

# 3. 快速训练 (30轮)
python tools/train_yolo.py --epochs 30 --batch 8

# 4. 测试效果
python main.py

# 5. 根据效果迭代改进
```

## 核心模块详解

### 1. 屏幕捕获模块 (capture)

**关键类:**
- `BaseCapture`: 抽象基类
- `AndroidCapture`: Android实现
- `IOSCapture`: iOS实现
- `CaptureManager`: 统一管理器

**优化建议:**
```python
# 降低截图质量以提升速度
frame = self.device.screenshot(format='opencv')
frame = cv2.resize(frame, (640, 360))  # 降低分辨率

# 使用minicap (Android)
# 需要额外配置，但速度更快
```

### 2. 检测器模块 (detector)

**关键类:**
- `YOLODetector`: YOLO检测器
- `Detection`: 检测结果类
- `DetectionTracker`: 目标跟踪器

**使用示例:**
```python
from src.detector import YOLODetector

# 初始化
detector = YOLODetector("models/best.pt", conf_threshold=0.3)

# 检测
detections = detector.detect(frame)

# 过滤特定类别
enemies = detector.get_detections_by_class(detections, "enemy")

# 查找最近的目标
nearest = detector.find_nearest_detection(detections, (500, 500))
```

### 3. 控制器模块 (controller)

**关键类:**
- `BaseController`: 抽象基类
- `AndroidController`: Android实现
- `IOSController`: iOS实现
- `ControllerManager`: 统一管理器

**高级操作:**
```python
from src.controller import ControllerManager

controller = ControllerManager("android", device)

# 基础操作
controller.tap(x, y)
controller.swipe(x1, y1, x2, y2)
controller.long_press(x, y, duration=2.0)

# 高级操作
controller.tap_random(x, y, radius=10)  # 随机偏移
controller.swipe_smooth(x1, y1, x2, y2)  # 贝塞尔曲线
controller.multi_tap([(x1,y1), (x2,y2)])  # 连续点击

# 相对坐标
controller.tap_relative(0.5, 0.5)  # 屏幕中心

# 方向滑动
controller.swipe_direction("up", distance=300)
```

### 4. 策略模块 (strategy)

**关键类:**
- `BaseStrategy`: 策略基类
- `SimpleStrategy`: 简单策略实现
- `StateMachineStrategy`: 状态机策略
- `GameState`: 游戏状态枚举

## 自定义策略开发

### 策略开发模板

```python
from src.strategy.base_strategy import BaseStrategy, GameState
from src.detector.yolo_detector import Detection
from typing import List, Dict, Any

class MyGameStrategy(BaseStrategy):
    """我的游戏策略"""

    def __init__(self):
        super().__init__(name="MyGameStrategy")

        # 定义状态判断规则
        self.state_rules = {
            GameState.MENU: self._is_menu_state,
            GameState.BATTLE: self._is_battle_state,
            GameState.REWARD: self._is_reward_state,
        }

        # 定义状态处理器
        self.state_handlers = {
            GameState.MENU: self._handle_menu,
            GameState.BATTLE: self._handle_battle,
            GameState.REWARD: self._handle_reward,
        }

    def analyze_state(self, frame, detections: List[Detection]) -> GameState:
        """分析游戏状态"""
        for state, rule_func in self.state_rules.items():
            if rule_func(detections):
                return state
        return GameState.UNKNOWN

    def make_decision(
        self,
        frame,
        detections: List[Detection],
        state: GameState
    ) -> Dict[str, Any]:
        """做出决策"""
        handler = self.state_handlers.get(state)
        if handler:
            return handler(frame, detections)
        return {"action": "wait", "params": {}}

    # 状态判断规则
    def _is_menu_state(self, detections: List[Detection]) -> bool:
        """判断是否在菜单界面"""
        return self.has_detection(detections, "menu_bg")

    def _is_battle_state(self, detections: List[Detection]) -> bool:
        """判断是否在战斗"""
        return self.has_detection(detections, "enemy") or \
               self.has_detection(detections, "hp_bar")

    def _is_reward_state(self, detections: List[Detection]) -> bool:
        """判断是否在奖励界面"""
        return self.has_detection(detections, "reward_icon")

    # 状态处理器
    def _handle_menu(self, frame, detections: List[Detection]) -> Dict[str, Any]:
        """处理菜单状态"""
        # 查找开始按钮并点击
        buttons = self.get_detections_by_class(detections, "start_button")
        if buttons:
            cx, cy = buttons[0].center
            return {"action": "tap", "params": {"x": cx, "y": cy}}
        return {"action": "wait", "params": {}}

    def _handle_battle(self, frame, detections: List[Detection]) -> Dict[str, Any]:
        """处理战斗状态"""
        # 优先级: 技能 > 攻击敌人 > 移动

        # 1. 检查技能
        skills = self.get_detections_by_class(detections, "skill_button")
        if skills:
            skill = skills[0]
            cx, cy = skill.center
            return {"action": "tap", "params": {"x": cx, "y": cy}}

        # 2. 攻击敌人
        enemies = self.get_detections_by_class(detections, "enemy")
        if enemies:
            enemy = enemies[0]
            cx, cy = enemy.center
            return {"action": "tap", "params": {"x": cx, "y": cy}}

        # 3. 无目标，等待
        return {"action": "wait", "params": {}}

    def _handle_reward(self, frame, detections: List[Detection]) -> Dict[str, Any]:
        """处理奖励状态"""
        # 点击任意位置关闭奖励界面
        claim_buttons = self.get_detections_by_class(detections, "claim_button")
        if claim_buttons:
            cx, cy = claim_buttons[0].center
            return {"action": "tap", "params": {"x": cx, "y": cy}}
        return {"action": "wait", "params": {}}
```

### 高级策略技巧

#### 1. 优先级队列

```python
def make_decision(self, frame, detections, state):
    # 定义动作优先级
    actions = []

    # 检查各种可能的动作
    if self.has_detection(detections, "danger"):
        actions.append((100, self._escape))  # 最高优先级

    if self.has_detection(detections, "skill_ready"):
        actions.append((80, self._use_skill))

    if self.has_detection(detections, "enemy"):
        actions.append((60, self._attack_enemy))

    # 执行优先级最高的动作
    if actions:
        actions.sort(reverse=True, key=lambda x: x[0])
        _, action_func = actions[0]
        return action_func(detections)

    return {"action": "wait", "params": {}}
```

#### 2. 状态记忆

```python
class StatefulStrategy(BaseStrategy):
    def __init__(self):
        super().__init__()
        self.state_memory = []
        self.last_action = None
        self.action_history = []

    def make_decision(self, frame, detections, state):
        # 避免重复动作
        if self.last_action == "attack" and \
           len(self.action_history) > 5 and \
           all(a == "attack" for a in self.action_history[-5:]):
            # 连续5次攻击，尝试其他动作
            return {"action": "wait", "params": {}}

        # 记录动作
        decision = self._make_decision_internal(frame, detections, state)
        self.last_action = decision["action"]
        self.action_history.append(decision["action"])

        return decision
```

## 数据标注技巧

### 标注质量检查清单

- [ ] 边界框紧密贴合目标
- [ ] 没有遗漏小目标
- [ ] 标签类别正确
- [ ] 重叠目标都已标注
- [ ] 检查边缘情况

### 标注工具推荐

**1. labelImg (本地)**
```bash
pip install labelImg
labelImg
```
优点: 简单易用，离线工作
缺点: 功能较少

**2. Roboflow (在线)**
网址: https://roboflow.com

优点:
- 自动数据增强
- 团队协作
- 格式转换
- 健康检查

**3. CVAT (专业)**
优点: 功能强大，适合大型项目

### 数据增强建议

在训练配置中启用:
```yaml
# 在训练时自动应用
augmentation:
  hsv_h: 0.015  # 色调
  hsv_s: 0.7    # 饱和度
  hsv_v: 0.4    # 明度
  degrees: 0.0  # 旋转 (游戏通常不需要)
  translate: 0.1  # 平移
  scale: 0.5    # 缩放
  shear: 0.0    # 剪切
  perspective: 0.0  # 透视
  flipud: 0.0   # 垂直翻转 (游戏通常不需要)
  fliplr: 0.5   # 水平翻转
  mosaic: 1.0   # Mosaic增强
  mixup: 0.0    # Mixup增强
```

## 模型训练优化

### 超参数调优

```python
# 训练配置建议
configs = {
    "快速原型": {
        "model": "yolov8n.pt",
        "epochs": 30,
        "batch": 16,
        "img_size": 320,
    },
    "平衡性能": {
        "model": "yolov8s.pt",
        "epochs": 100,
        "batch": 16,
        "img_size": 640,
    },
    "高精度": {
        "model": "yolov8m.pt",
        "epochs": 200,
        "batch": 8,
        "img_size": 640,
    },
}
```

### 训练监控

使用WandB或TensorBoard监控训练:

```bash
# 安装wandb
pip install wandb

# 训练时自动上传到wandb
wandb login
python tools/train_yolo.py --data data.yaml
```

### 模型评估

```python
from ultralytics import YOLO

model = YOLO("runs/train/exp/weights/best.pt")

# 验证集评估
metrics = model.val()

print(f"mAP50: {metrics.box.map50}")
print(f"mAP50-95: {metrics.box.map}")
```

## 性能调优

### FPS优化

```python
# 1. 使用更小的模型
detector = YOLODetector("yolov8n.pt")  # n < s < m < l < x

# 2. 降低输入分辨率
frame = cv2.resize(frame, (320, 180))

# 3. 跳帧检测
if frame_count % 2 == 0:  # 每2帧检测一次
    detections = detector.detect(frame)

# 4. 限制检测区域
roi = frame[100:500, 100:600]  # 只检测ROI
detections = detector.detect(roi)
```

### 内存优化

```python
# 1. 及时释放资源
import gc
gc.collect()

# 2. 限制保存的截图数量
if len(screenshot_buffer) > 100:
    screenshot_buffer.pop(0)

# 3. 使用numpy内存视图
frame_view = frame[::2, ::2]  # 下采样视图，不复制
```

## 调试技巧

### 日志调试

```python
import logging

# 设置详细日志
logger = setup_logger(level=logging.DEBUG)

# 在策略中添加日志
logger.debug(f"检测到 {len(detections)} 个目标")
logger.info(f"当前状态: {state.value}")
```

### 可视化调试

```python
# 保存调试图像
debug_frame = detector.draw_detections(frame, detections)
cv2.imwrite(f"debug/frame_{frame_count}.jpg", debug_frame)

# 绘制自定义信息
cv2.putText(debug_frame, f"State: {state}", (10, 30), ...)
cv2.circle(debug_frame, (cx, cy), 10, (0, 255, 0), -1)
```

### 性能分析

```python
import time

# 测量各模块耗时
t1 = time.time()
frame = capture_manager.get_frame()
capture_time = time.time() - t1

t2 = time.time()
detections = detector.detect(frame)
detect_time = time.time() - t2

print(f"捕获: {capture_time*1000:.1f}ms")
print(f"检测: {detect_time*1000:.1f}ms")
```

## 最佳实践

### 1. 代码组织

```python
# 将游戏特定逻辑独立出来
# src/games/my_game/
#   ├── __init__.py
#   ├── strategy.py
#   ├── config.yaml
#   └── README.md
```

### 2. 配置管理

```yaml
# 为不同游戏创建不同配置
config/
  ├── default_config.yaml
  ├── game1_config.yaml
  └── game2_config.yaml
```

### 3. 版本控制

```bash
# 使用git管理代码
git add .
git commit -m "feat: 添加新游戏策略"

# 忽略数据和模型
echo "data/" >> .gitignore
echo "models/*.pt" >> .gitignore
echo "logs/" >> .gitignore
```

## 故障排除

### 常见错误及解决方法

1. **ModuleNotFoundError**: 检查依赖安装
2. **CUDA out of memory**: 减小batch_size
3. **设备连接超时**: 重启adb服务
4. **检测不准确**: 增加训练数据
5. **操作延迟高**: 优化检测速度

## 进阶主题

### 1. 多线程架构

```python
import threading
import queue

class MultiThreadBot:
    def __init__(self):
        self.frame_queue = queue.Queue(maxsize=5)
        self.detection_queue = queue.Queue(maxsize=5)

    def capture_thread(self):
        while True:
            frame = self.capture_manager.get_frame()
            self.frame_queue.put(frame)

    def detect_thread(self):
        while True:
            frame = self.frame_queue.get()
            detections = self.detector.detect(frame)
            self.detection_queue.put(detections)

    def control_thread(self):
        while True:
            detections = self.detection_queue.get()
            decision = self.strategy.update(None, detections)
            self._execute_action(decision)
```

### 2. 远程监控

```python
from flask import Flask, jsonify

app = Flask(__name__)

@app.route('/status')
def get_status():
    return jsonify({
        'fps': bot.fps,
        'state': bot.strategy.current_state.value,
        'frame_count': bot.frame_count
    })

# 启动Web服务
app.run(host='0.0.0.0', port=5000)
```

## 总结

本指南覆盖了从开发到部署的完整流程。建议:

1. 从简单游戏开始练习
2. 逐步增加复杂度
3. 重视数据质量
4. 持续优化性能
5. 遵守游戏规则

祝开发顺利！
