# AI Game Bot - 基于YOLO的自动游戏机器人

一个功能完整的AI自动玩手机游戏的框架，基于YOLO目标检测技术实现游戏元素识别和智能决策。

## 功能特性

- **多平台支持**: 支持Android和iOS设备
- **智能检测**: 基于YOLOv8的高精度游戏元素识别
- **自动控制**: 模拟人类操作的触摸控制系统
- **策略系统**: 灵活的游戏策略框架，支持自定义决策逻辑
- **完整工具链**: 数据收集、标注、训练、部署一体化
- **可视化界面**: 实时显示检测结果和运行状态

## 项目结构

```
cl-dnfm/
├── src/                      # 源代码
│   ├── capture/             # 屏幕捕获模块
│   ├── detector/            # YOLO检测器模块
│   ├── controller/          # 游戏控制器模块
│   ├── strategy/            # 游戏策略模块
│   └── utils/               # 工具模块
├── config/                   # 配置文件
├── data/                     # 数据集目录
│   ├── images/              # 原始图像
│   ├── labels/              # 标注文件
│   ├── train/               # 训练集
│   ├── val/                 # 验证集
│   └── test/                # 测试集
├── models/                   # 训练好的模型
├── tools/                    # 工具脚本
│   ├── collect_data.py      # 数据收集
│   ├── split_dataset.py     # 数据集划分
│   └── train_yolo.py        # 模型训练
├── scripts/                  # 示例脚本
│   └── quick_start.py       # 快速启动测试
├── logs/                     # 日志文件
├── main.py                   # 主程序入口
├── requirements.txt          # 依赖列表
└── README.md                 # 项目文档
```

## 快速开始

### 1. 环境准备

#### 系统要求
- Python 3.8+
- Android设备 (或iOS设备 + WebDriverAgent)
- CUDA (可选，用于GPU加速)

#### 安装依赖

```bash
# 基础依赖
pip install -r requirements.txt

# Android设备初始化 (仅首次)
python -m uiautomator2 init
```

#### 设备连接

**Android设备:**
1. 开启USB调试
2. 连接电脑
3. 运行 `adb devices` 确认连接

**iOS设备 (可选):**
1. 安装WebDriverAgent
2. 启动WDA服务
3. 配置端口映射

### 2. 测试连接

```bash
python scripts/quick_start.py
```

如果显示设备连接成功并能正常截图，说明环境配置正确。

### 3. 数据收集与标注

#### 步骤1: 收集游戏截图

```bash
python tools/collect_data.py --platform android --count 500 --interval 2
```

参数说明:
- `--platform`: 平台类型 (android/ios)
- `--count`: 截图数量
- `--interval`: 截图间隔(秒)
- `--output`: 输出目录 (默认: data/images)

#### 步骤2: 标注数据

推荐使用以下工具之一:
- **labelImg**: 本地标注工具
- **Roboflow**: 在线标注平台 (推荐)
- **CVAT**: 专业标注工具

标注格式: YOLO format (txt文件)

标注类别示例:
```yaml
0: enemy           # 敌人
1: skill_button    # 技能按钮
2: start_button    # 开始按钮
3: claim_button    # 领取按钮
4: hp_bar          # 血条
5: menu_bg         # 菜单背景
```

#### 步骤3: 划分数据集

```bash
python tools/split_dataset.py \
  --source data/annotated \
  --output data \
  --train-ratio 0.8 \
  --val-ratio 0.1 \
  --test-ratio 0.1
```

### 4. 训练YOLO模型

#### 创建数据配置文件

在训练前，运行工具自动生成配置:

```bash
python tools/train_yolo.py
```

这会在 `data/game_data.yaml` 创建配置文件。

#### 开始训练

```bash
python tools/train_yolo.py \
  --data data/game_data.yaml \
  --epochs 100 \
  --batch 16 \
  --img-size 640 \
  --model yolov8n.pt \
  --device 0
```

训练参数说明:
- `--data`: 数据配置文件
- `--epochs`: 训练轮数 (建议100-200)
- `--batch`: 批次大小 (根据显存调整)
- `--img-size`: 输入图像尺寸 (640或320)
- `--model`: 预训练模型 (n/s/m/l/x)
- `--device`: GPU设备 (0, 1... 或 cpu)

训练完成后，模型保存在 `runs/train/exp/weights/best.pt`

### 5. 运行游戏机器人

#### 配置文件

编辑 `config/default_config.yaml`:

```yaml
device:
  platform: "android"      # 平台类型

model:
  path: "models/best.pt"   # 模型路径
  conf_threshold: 0.25     # 置信度阈值

strategy:
  name: "SimpleStrategy"   # 策略名称
  action_cooldown: 0.5     # 操作冷却时间
```

#### 启动机器人

```bash
# 使用默认配置
python main.py

# 使用自定义配置
python main.py --config config/my_config.yaml
```

#### 运行效果

机器人会:
1. 实时捕获游戏画面
2. 使用YOLO检测游戏元素
3. 根据策略做出决策
4. 自动执行触摸操作
5. 显示可视化界面 (可选)

## 自定义开发

### 1. 创建自定义策略

在 `src/strategy/` 创建新策略:

```python
from src.strategy.base_strategy import BaseStrategy, GameState

class MyStrategy(BaseStrategy):
    def analyze_state(self, frame, detections):
        # 分析游戏状态
        if self.has_detection(detections, "menu_button"):
            return GameState.MENU
        return GameState.UNKNOWN

    def make_decision(self, frame, detections, state):
        # 做出决策
        if state == GameState.MENU:
            buttons = self.get_detections_by_class(detections, "start_button")
            if buttons:
                cx, cy = buttons[0].center
                return {"action": "tap", "params": {"x": cx, "y": cy}}

        return {"action": "wait", "params": {}}
```

### 2. 配置自定义类别

编辑 `config/default_config.yaml`:

```yaml
classes:
  - my_custom_class_1
  - my_custom_class_2
  # 添加你的类别
```

### 3. 扩展控制功能

在 `src/controller/game_controller.py` 添加新操作:

```python
def my_custom_gesture(self, params):
    # 实现自定义手势
    pass
```

## 性能优化

### 提升检测速度

1. **使用更小的模型**: yolov8n > yolov8s > yolov8m
2. **降低输入分辨率**: 640 -> 320
3. **使用ONNX**: 导出为ONNX格式
4. **GPU加速**: 安装CUDA和cuDNN

```bash
# 导出为ONNX
from ultralytics import YOLO
model = YOLO("best.pt")
model.export(format="onnx")
```

### 降低CPU占用

在配置文件中调整FPS限制:

```yaml
runtime:
  fps_limit: 15  # 降低到15帧
```

## 常见问题

### Q: 设备连接失败

**A:** 检查以下几点:
1. USB调试是否开启
2. 驱动是否安装 (`adb devices`)
3. uiautomator2是否初始化 (`python -m uiautomator2 init`)
4. 是否授权USB调试

### Q: 检测准确率低

**A:** 改进方法:
1. 增加训练数据 (建议1000+)
2. 提高标注质量
3. 使用更大的模型 (yolov8m/l)
4. 增加训练轮数
5. 使用数据增强

### Q: 操作不够流畅

**A:** 优化建议:
1. 降低FPS限制
2. 减少操作冷却时间
3. 使用更快的检测模型
4. 简化策略逻辑

### Q: 如何防止被检测

**A:** 模拟人类操作:
1. 添加随机延迟
2. 使用贝塞尔曲线滑动
3. 随机点击偏移
4. 避免过于规律的操作

## 进阶功能

### 1. 强化学习集成

可以集成DQN/PPO等强化学习算法:

```python
# TODO: 实现强化学习策略
from stable_baselines3 import PPO
```

### 2. 多线程处理

分离检测和控制线程提升性能:

```python
# TODO: 实现多线程架构
```

### 3. 远程控制

添加Web界面实现远程监控:

```python
# TODO: 实现Flask/FastAPI后端
```

## 贡献指南

欢迎贡献代码！请遵循以下步骤:

1. Fork本项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启Pull Request

## 许可证

本项目仅供学习和研究使用。请勿用于违反游戏服务条款的用途。

## 致谢

- [Ultralytics YOLOv8](https://github.com/ultralytics/ultralytics)
- [uiautomator2](https://github.com/openatx/uiautomator2)
- [OpenCV](https://opencv.org/)

## 联系方式

如有问题或建议，请提交Issue或Pull Request。

---

**警告**: 使用自动化工具可能违反某些游戏的服务条款。请确保在合法和道德的范围内使用本项目。
