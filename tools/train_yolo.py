"""
YOLO模型训练脚本
YOLO Model Training Script
"""

import argparse
from pathlib import Path
import yaml


def train_yolo(
    data_yaml: str,
    epochs: int = 100,
    batch_size: int = 16,
    img_size: int = 640,
    model: str = "yolov8n.pt",
    device: str = "0",
    project: str = "runs/train",
    name: str = "exp"
):
    """
    训练YOLO模型

    Args:
        data_yaml: 数据配置文件路径
        epochs: 训练轮数
        batch_size: 批次大小
        img_size: 输入图像尺寸
        model: 预训练模型
        device: 设备 (0, 1, 2... 或 cpu)
        project: 项目目录
        name: 实验名称
    """
    try:
        from ultralytics import YOLO

        print("=== YOLO模型训练 ===\n")
        print(f"数据配置: {data_yaml}")
        print(f"训练轮数: {epochs}")
        print(f"批次大小: {batch_size}")
        print(f"图像尺寸: {img_size}")
        print(f"预训练模型: {model}")
        print(f"设备: {device}\n")

        # 加载模型
        print("加载模型...")
        yolo_model = YOLO(model)

        # 开始训练
        print("开始训练...\n")
        results = yolo_model.train(
            data=data_yaml,
            epochs=epochs,
            batch=batch_size,
            imgsz=img_size,
            device=device,
            project=project,
            name=name,
            patience=20,         # 早停耐心值
            save=True,           # 保存检查点
            save_period=10,      # 每10轮保存一次
            plots=True,          # 生成训练图表
            verbose=True,        # 详细输出
        )

        print("\n" + "="*50)
        print("训练完成!")
        print(f"最佳模型: {project}/{name}/weights/best.pt")
        print(f"最终模型: {project}/{name}/weights/last.pt")
        print("="*50)

        return results

    except ImportError:
        print("错误: 请安装ultralytics")
        print("运行: pip install ultralytics")
        return None

    except Exception as e:
        print(f"训练失败: {e}")
        return None


def create_data_yaml(
    data_dir: str,
    class_names: list,
    output_path: str = "data.yaml"
):
    """
    创建YOLO数据配置文件

    Args:
        data_dir: 数据目录
        class_names: 类别名称列表
        output_path: 输出路径
    """
    data_dir = Path(data_dir).absolute()

    data_config = {
        'path': str(data_dir),
        'train': 'train/images',
        'val': 'val/images',
        'test': 'test/images',
        'nc': len(class_names),
        'names': class_names
    }

    with open(output_path, 'w', encoding='utf-8') as f:
        yaml.dump(data_config, f, allow_unicode=True)

    print(f"✓ 数据配置文件已创建: {output_path}")
    print(f"  类别数量: {len(class_names)}")
    print(f"  类别列表: {class_names}")


def main():
    parser = argparse.ArgumentParser(description='YOLO模型训练脚本')

    parser.add_argument('--data', type=str, required=True,
                        help='数据配置文件路径 (data.yaml)')
    parser.add_argument('--epochs', type=int, default=100,
                        help='训练轮数 (默认: 100)')
    parser.add_argument('--batch', type=int, default=16,
                        help='批次大小 (默认: 16)')
    parser.add_argument('--img-size', type=int, default=640,
                        help='输入图像尺寸 (默认: 640)')
    parser.add_argument('--model', type=str, default='yolov8n.pt',
                        help='预训练模型 (默认: yolov8n.pt)')
    parser.add_argument('--device', type=str, default='0',
                        help='设备 (默认: 0, 使用cpu则填cpu)')
    parser.add_argument('--project', type=str, default='runs/train',
                        help='项目目录 (默认: runs/train)')
    parser.add_argument('--name', type=str, default='exp',
                        help='实验名称 (默认: exp)')

    args = parser.parse_args()

    train_yolo(
        data_yaml=args.data,
        epochs=args.epochs,
        batch_size=args.batch,
        img_size=args.img_size,
        model=args.model,
        device=args.device,
        project=args.project,
        name=args.name
    )


if __name__ == "__main__":
    # 示例用法
    print("=== YOLO训练工具 ===\n")
    print("使用方法:")
    print("python tools/train_yolo.py --data data.yaml --epochs 100 --batch 16\n")

    # 如果直接运行，创建示例数据配置
    example_classes = [
        "enemy",
        "skill_button",
        "start_button",
        "claim_button",
        "hp_bar",
        "menu_bg",
        "loading_icon",
        "reward_icon"
    ]

    print("创建示例数据配置...")
    create_data_yaml(
        data_dir="data",
        class_names=example_classes,
        output_path="data/game_data.yaml"
    )

    print("\n下一步:")
    print("1. 准备数据集并标注")
    print("2. 将数据放入 data/train, data/val, data/test 目录")
    print("3. 运行训练: python tools/train_yolo.py --data data/game_data.yaml")
