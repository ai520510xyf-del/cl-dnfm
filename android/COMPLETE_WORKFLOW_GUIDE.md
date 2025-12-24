# 🎮 DNF游戏AI助手 - 完整使用流程指南

**目标**: 训练一个能帮你自动玩DNF游戏的AI助手

---

## 📋 完整流程概览

```
1. 数据收集 → 2. 数据标注 → 3. 上传数据 → 4. 云端训练 → 5. 下载模型 → 6. 部署使用 → 7. 自动游戏
```

---

## 第一步：数据收集 (APP操作)

### 1.1 启动机器人
1. 打开DNF游戏
2. 打开我们的APP
3. 点击"首页" → "启动机器人"
4. 授予无障碍权限和悬浮窗权限
5. 机器人会自动开始截图收集数据

### 1.2 自动收集截图
- 机器人在后台自动截图
- 每隔1-2秒截取一次游戏画面
- 截图保存在：`/Android/data/com.gamebot.ai/files/dataset/images/`
- 建议收集：**至少300-500张截图**

### 1.3 查看收集进度
- 打开APP → "统计"页面
- 查看"总图片数"
- 确保数据足够后，停止机器人

---

## 第二步：数据标注 (APP操作)

### 2.1 开始标注
1. 打开APP → "数据收集"页面
2. 点击"标注数据集"
3. 进入标注界面

### 2.2 标注目标
**重要**：你需要告诉AI这些目标是什么，它才能学会识别：

| 目标类型 | 说明 | 标注方法 |
|---------|------|---------|
| **enemy** | 敌人 | 框选所有敌人 |
| **skill_button** | 技能按钮 | 框选攻击技能图标 |
| **start_button** | 开始按钮 | 框选"开始战斗"等按钮 |
| **item** | 掉落物品 | 框选地上的装备/道具 |
| **door** | 门/传送门 | 框选可进入的门 |
| **boss** | BOSS | 框选BOSS |

### 2.3 标注操作
1. **选择类别**：点击底部类别按钮
2. **框选目标**：在图片上拖动框选
3. **保存**：点击"保存标注"
4. **下一张**：继续标注下一张图片

### 2.4 标注建议
- ✅ **质量优于数量**：宁可慢点标准确
- ✅ **框要紧贴**：边界框应该紧贴目标
- ✅ **全部标注**：一张图中的所有目标都要标
- ✅ **标注足够**：建议至少标注200张

---

## 第三步：上传数据集 (APP操作)

### 3.1 上传到云端
1. 打开APP → "云端训练"页面
2. 查看"已标注图片"数量
3. 输入数据集名称，例如：`dnf_dataset_v1`
4. 点击"上传数据集"
5. 等待上传完成（约3-5分钟，取决于网络）

### 3.2 上传成功标志
```
✅ 上传成功！200/200 张图片
数据集ID: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
```

**保存这个数据集ID**，后面需要用！

---

## 第四步：云端训练 (Google Colab操作)

### 4.1 为什么需要Colab？
- 手机性能不够，无法训练神经网络
- Colab提供**免费的GPU** (Tesla T4)
- 训练时间：约30-60分钟

### 4.2 打开训练脚本
1. 在电脑上打开项目文件夹
2. 找到文件：`DNF_Training_Colab.ipynb`
3. 上传到Google Colab：
   - 访问 https://colab.research.google.com
   - 点击"文件" → "上传笔记本"
   - 选择 `DNF_Training_Colab.ipynb`

### 4.3 配置Supabase连接

**第一个代码单元格**：
```python
# Supabase配置
SUPABASE_URL = "https://lcvunitsbdpaltisybhn.supabase.co"
SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."  # 你的Key
```

### 4.4 运行训练

**按顺序执行每个单元格**：

#### 单元格1：安装依赖
```python
!pip install ultralytics supabase
```
⏱️ 耗时：约2分钟

#### 单元格2：连接Supabase
```python
from supabase import create_client
supabase = create_client(SUPABASE_URL, SUPABASE_KEY)
print("✅ Supabase连接成功")
```

#### 单元格3：查询待训练任务
```python
# 查询你刚刚创建的训练任务
jobs = supabase.table("training_jobs").select("*").eq("status", "pending").execute()
```

应该能看到你的训练任务：
```
找到待训练任务: xxxxx
数据集ID: xxxxx
训练轮数: 50
```

#### 单元格4：下载数据集
```python
# 从Supabase Storage下载你上传的图片和标注
```
⏱️ 耗时：约5-10分钟

#### 单元格5：转换为YOLO格式
```python
# 将JSON标注转换为YOLO txt格式
```
⏱️ 耗时：约1分钟

#### 单元格6：**开始训练** 🚀
```python
from ultralytics import YOLO

# 加载YOLOv8预训练模型
model = YOLO('yolov8n.pt')

# 开始训练
results = model.train(
    data='dataset.yaml',
    epochs=50,
    imgsz=320,
    batch=16,
    device=0  # 使用GPU
)
```

⏱️ **训练时间：约30-60分钟**

**训练过程中你会看到**：
```
Epoch   GPU_mem   box_loss   cls_loss   dfl_loss
1/50    1.5G      1.234      0.876      1.123
2/50    1.5G      1.156      0.823      1.089
3/50    1.5G      1.098      0.784      1.045
...
50/50   1.5G      0.456      0.234      0.567     ✅
```

#### 单元格7：转换为TFLite
```python
# 将PyTorch模型转换为TensorFlow Lite (Android使用)
model.export(format='tflite')
```
⏱️ 耗时：约2分钟

#### 单元格8：上传模型到Supabase
```python
# 上传训练好的模型到Supabase Storage
model_path = f"models/{job_id}/model.tflite"
with open("best.tflite", "rb") as f:
    supabase.storage.from_("models").upload(model_path, f)

print(f"✅ 模型已上传: {model_path}")
```

#### 单元格9：更新训练状态
```python
# 将训练任务状态改为"completed"
supabase.table("training_jobs").update({
    "status": "completed",
    "progress": 100,
    "accuracy": final_accuracy
}).eq("id", job_id).execute()

print("🎉 训练完成！")
```

### 4.5 训练完成标志

你应该看到：
```
🎉 训练完成！
模型路径: models/xxxxx/model.tflite
准确率: 92.5%
```

---

## 第五步：监控训练进度 (APP操作)

### 5.1 在APP中查看进度

**虽然训练在Colab进行，但APP可以实时监控**：

1. 在"云端训练"页面
2. 点击"开始训练"后，会自动显示进度卡片
3. 实时显示：
   ```
   状态: 训练中 35/50
   Epoch: 35/50 | Loss: 0.567 | Accuracy: 87.3%

   进度条: ████████████░░░░░░░░ 70%
   ```

### 5.2 训练状态说明

| 状态 | 说明 | 下一步 |
|-----|------|--------|
| **PENDING** | 等待训练 | 在Colab中运行训练脚本 |
| **TRAINING** | 训练中 | 耐心等待30-60分钟 |
| **COMPLETED** | ✅ 训练完成 | 下载模型 |
| **FAILED** | ❌ 训练失败 | 查看错误日志 |

### 5.3 训练完成提示

当训练完成时，APP会显示：
```
✅ 训练完成！
状态: 训练完成 50/50
准确率: 92.5%

可以下载模型了！
```

---

## 第六步：下载模型 (APP操作)

### 6.1 下载到手机
1. 训练完成后，"模型管理"卡片会显示
2. 点击"下载模型"
3. 等待下载（模型大小约10-20MB）
4. 下载完成提示：
   ```
   ✅ 模型已下载: downloaded_model.tflite
   ```

### 6.2 部署模型
1. 点击"部署模型"按钮
2. 模型会被部署到应用可以使用的位置
3. 部署成功提示：
   ```
   ✅ 模型已部署！可以在首页启动使用了
   ```

---

## 第七步：使用AI玩游戏 (APP操作)

### 7.1 启动AI助手

**现在，你训练的模型已经可以用了！**

1. 打开DNF游戏
2. 打开APP → "首页"
3. 确保显示：`模型: dnf_detection_model.tflite ✅`
4. 点击"启动机器人"

### 7.2 AI会做什么？

启动后，AI会：

#### 1️⃣ **实时检测**
```
检测到目标:
- enemy (置信度: 95%) 位置: (120, 450)
- enemy (置信度: 92%) 位置: (340, 520)
- skill_button (置信度: 98%) 位置: (800, 2100)
```

#### 2️⃣ **自动决策**
```kotlin
// AI的决策逻辑 (GameBotAccessibilityService.kt)

if (检测到敌人) {
    → 点击技能按钮攻击
}

if (检测到物品) {
    → 移动过去拾取
}

if (检测到门) {
    → 移动并进入下一关
}

if (检测到BOSS) {
    → 使用大招攻击
}
```

#### 3️⃣ **自动操作**
- ✅ 自动打怪
- ✅ 自动拾取装备
- ✅ 自动进入下一关
- ✅ 自动使用技能

### 7.3 查看实时效果

**悬浮窗会显示**：
```
🤖 AI助手运行中

检测: 3个目标
- enemy x2
- skill_button x1

状态: 攻击中
FPS: 10
```

### 7.4 调试信息

查看日志：
```bash
adb logcat -s GameBotService:I YoloDetector:I
```

你会看到：
```
✅ 模型加载成功
输入形状: [1, 320, 320, 3]
输出形状: [1, 2100, 85]

检测完成: 用时67ms, 发现3个目标
- enemy (0.95) at (120, 450)
- enemy (0.92) at (340, 520)
- skill_button (0.98) at (800, 2100)

执行操作: 点击技能按钮
```

---

## 🎯 完整流程时间估算

| 步骤 | 时间 | 说明 |
|-----|------|------|
| 1. 数据收集 | 30-60分钟 | 收集300-500张截图 |
| 2. 数据标注 | 2-3小时 | 标注200-300张 |
| 3. 上传数据 | 5-10分钟 | 取决于网络 |
| 4. 云端训练 | 30-60分钟 | Colab自动训练 |
| 5. 下载模型 | 1-2分钟 | 10-20MB |
| 6. 部署模型 | 10秒 | 自动部署 |
| 7. 开始使用 | 立即 | AI开始玩游戏 |
| **总计** | **4-6小时** | 首次训练 |

**后续优化**：
- 收集更多数据 → 标注 → 重新训练
- 模型准确率会越来越高
- 第二次训练只需2-3小时

---

## ❓ 常见问题

### Q1: 为什么上传后没有自动开始训练？

**A**: 训练不是自动的，需要你在Colab中手动运行训练脚本。

**原因**：
- Supabase的Edge Functions有限制
- Colab提供免费GPU更适合训练
- 你可以控制训练过程

**解决**：按照"第四步"在Colab中运行训练

---

### Q2: 如何知道模型训练完成了？

**三种方式**：

1. **APP内查看** (推荐)
   - "云端训练"页面实时显示进度
   - 完成后显示"✅ 训练完成"

2. **Colab中查看**
   - 最后一个单元格会输出"🎉 训练完成！"

3. **直接查Supabase**
   - 登录 Supabase Dashboard
   - 打开 `training_jobs` 表
   - 查看 `status` 字段是否为 `completed`

---

### Q3: 模型准确率低怎么办？

**准确率 < 80%** → 需要优化

**优化方法**：

1. **增加数据量**
   - 至少500张标注图片
   - 每个类别至少50个实例

2. **提高标注质量**
   - 边界框要准确
   - 不要遗漏目标
   - 避免误标

3. **增加训练轮数**
   - 默认50轮，可以改为100轮
   - 修改Colab中 `epochs=50` → `epochs=100`

4. **平衡数据集**
   - 确保每个类别的数量相近
   - 不要某类过多，某类过少

5. **使用更大的模型**
   - 默认 `yolov8n.pt` (最小)
   - 改用 `yolov8s.pt` (小) 或 `yolov8m.pt` (中)

---

### Q4: 下载的模型在哪里？

**模型位置**：

1. **下载后**：`/data/data/com.gamebot.ai/files/downloaded_model.tflite`
2. **部署后**：`/data/data/com.gamebot.ai/files/models/dnf_detection_model.tflite`

**检测器会从这里加载**：
```kotlin
// GameBotAccessibilityService.kt
val modelPath = "models/dnf_detection_model.tflite"
detector = YoloDetector(this, modelPath)
```

---

### Q5: AI玩游戏效果不好？

**可能原因**：

1. **模型准确率低** → 重新训练更好的模型
2. **检测阈值太高** → 调低置信度阈值
3. **决策逻辑不合理** → 优化GameBotAccessibilityService的逻辑
4. **游戏界面变化** → 重新收集数据训练

**优化建议**：

调整检测阈值 (YoloDetector.kt):
```kotlin
private const val CONFIDENCE_THRESHOLD = 0.3f  // 降低到0.2
```

增加检测频率 (GameBotAccessibilityService.kt):
```kotlin
delay(1000) // 改为 delay(500) 每0.5秒检测一次
```

---

### Q6: 如何重新训练模型？

**迭代优化流程**：

1. **继续收集数据** → 启动机器人收集更多截图
2. **标注新数据** → 标注新收集的截图
3. **上传新数据集** → 起个新名字 `dnf_dataset_v2`
4. **重新训练** → 在Colab中用新数据集训练
5. **对比效果** → 测试新模型是否更好
6. **部署使用** → 如果更好就部署新模型

**建议**：保留多个版本的模型，方便回退

---

### Q7: 训练失败了怎么办？

**检查清单**：

1. **Colab连接**
   ```python
   # 检查GPU是否可用
   import tensorflow as tf
   print(tf.config.list_physical_devices('GPU'))
   # 应该输出: [PhysicalDevice(name='/physical_device:GPU:0', device_type='GPU')]
   ```

2. **数据完整性**
   - 检查Supabase Storage中是否有图片和标注
   - 图片和标注数量是否一致

3. **内存不足**
   - 减小batch size: `batch=16` → `batch=8`
   - 减小图像尺寸: `imgsz=320` → `imgsz=256`

4. **查看错误日志**
   - Colab中查看完整的错误信息
   - Supabase的 `training_jobs` 表查看 `error_message`

---

## 🚀 高级技巧

### 1. 批量处理

**一次训练多个任务**：
```python
# Colab中循环处理所有pending任务
jobs = supabase.table("training_jobs").select("*").eq("status", "pending").execute()
for job in jobs.data:
    train_model(job)  # 依次训练
```

### 2. 使用预训练模型

**加速训练**：
- 第一次用 `yolov8n.pt` (从头训练)
- 后续用自己训练的模型继续训练
```python
model = YOLO('previous_best.pt')  # 用上次的最佳模型
model.train(...)  # 继续训练
```

### 3. 自动化部署

**训练完自动下载**：
```kotlin
// 可以添加到CloudTrainingFragment
lifecycleScope.launch {
    // 轮询直到完成
    while (status != "completed") {
        delay(30000)  // 每30秒查一次
        checkStatus()
    }

    // 自动下载
    downloadModel()

    // 自动部署
    deployModel()

    Toast.makeText(context, "✅ 新模型已自动部署", Toast.LENGTH_LONG).show()
}
```

### 4. A/B测试

**对比不同模型**：
```kotlin
// 测试模型A
detector = YoloDetector(this, "models/model_v1.tflite")
val resultsA = testModel()

// 测试模型B
detector = YoloDetector(this, "models/model_v2.tflite")
val resultsB = testModel()

// 对比准确率
if (resultsB.accuracy > resultsA.accuracy) {
    // 使用模型B
}
```

---

## 📊 效果评估

### 训练指标

**好的模型应该有**：
- ✅ 训练Loss < 0.5
- ✅ 验证Loss < 0.6
- ✅ mAP@0.5 > 0.85
- ✅ 准确率 > 85%

### 实际效果

**测试场景**：
1. **打怪测试** → 10分钟能打多少怪
2. **通关测试** → 10分钟能过几关
3. **拾取测试** → 是否能拾取所有掉落
4. **存活测试** → 是否会避开危险

**记录数据**：
```
模型版本: v1
测试时间: 10分钟
击杀敌人: 45个
通过关卡: 5关
拾取物品: 23个
死亡次数: 2次
评分: ⭐⭐⭐☆☆ (3/5)
```

---

## 🎉 总结

**完整流程**：

1. ✅ **收集数据** → APP自动截图
2. ✅ **标注数据** → 手动标注目标
3. ✅ **上传数据** → 上传到Supabase
4. ✅ **云端训练** → Colab自动训练
5. ✅ **监控进度** → APP实时显示
6. ✅ **下载模型** → 下载到手机
7. ✅ **部署使用** → AI开始玩游戏

**关键点**：
- 🎯 **数据质量是关键** → 好的标注 = 好的模型
- 🎯 **耐心等待训练** → 30-60分钟是正常的
- 🎯 **持续优化** → 收集更多数据，不断改进

**现在你知道如何训练和使用AI玩游戏了！** 🎮🤖

如有问题，查看日志或重新阅读本指南。
