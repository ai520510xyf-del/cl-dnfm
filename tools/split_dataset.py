"""
数据集划分工具 - 将标注好的数据划分为train/val/test
Dataset Split Tool - Split annotated data into train/val/test
"""

import shutil
import random
import argparse
from pathlib import Path
from tqdm import tqdm


def split_dataset(
    source_dir: str,
    output_dir: str,
    train_ratio: float = 0.8,
    val_ratio: float = 0.1,
    test_ratio: float = 0.1,
    seed: int = 42
):
    """
    划分数据集

    Args:
        source_dir: 源数据目录 (包含images和labels子目录)
        output_dir: 输出目录
        train_ratio: 训练集比例
        val_ratio: 验证集比例
        test_ratio: 测试集比例
        seed: 随机种子
    """
    source_path = Path(source_dir)
    output_path = Path(output_dir)

    # 检查源目录
    images_dir = source_path / "images"
    labels_dir = source_path / "labels"

    if not images_dir.exists():
        print(f"✗ 图像目录不存在: {images_dir}")
        return

    if not labels_dir.exists():
        print(f"✗ 标注目录不存在: {labels_dir}")
        return

    print("=== 数据集划分工具 ===\n")
    print(f"源目录: {source_path}")
    print(f"输出目录: {output_path}")
    print(f"划分比例: train={train_ratio}, val={val_ratio}, test={test_ratio}\n")

    # 获取所有图像文件
    image_files = list(images_dir.glob("*.jpg")) + list(images_dir.glob("*.png"))

    if not image_files:
        print("✗ 未找到图像文件")
        return

    print(f"找到 {len(image_files)} 张图像")

    # 随机打乱
    random.seed(seed)
    random.shuffle(image_files)

    # 计算划分数量
    total = len(image_files)
    train_count = int(total * train_ratio)
    val_count = int(total * val_ratio)
    test_count = total - train_count - val_count

    print(f"训练集: {train_count} 张")
    print(f"验证集: {val_count} 张")
    print(f"测试集: {test_count} 张\n")

    # 划分数据
    train_files = image_files[:train_count]
    val_files = image_files[train_count:train_count + val_count]
    test_files = image_files[train_count + val_count:]

    # 创建输出目录
    splits = {
        'train': train_files,
        'val': val_files,
        'test': test_files
    }

    for split_name, files in splits.items():
        split_images_dir = output_path / split_name / "images"
        split_labels_dir = output_path / split_name / "labels"

        split_images_dir.mkdir(parents=True, exist_ok=True)
        split_labels_dir.mkdir(parents=True, exist_ok=True)

        print(f"处理 {split_name} 集...")

        for image_file in tqdm(files, desc=f"{split_name}"):
            # 复制图像
            shutil.copy2(image_file, split_images_dir / image_file.name)

            # 复制对应的标注文件
            label_file = labels_dir / f"{image_file.stem}.txt"
            if label_file.exists():
                shutil.copy2(label_file, split_labels_dir / label_file.name)
            else:
                print(f"  警告: 缺少标注文件 {label_file.name}")

    print("\n✓ 数据集划分完成!")
    print(f"  输出目录: {output_path}")
    print("\n下一步:")
    print("1. 检查划分后的数据集")
    print("2. 创建data.yaml配置文件")
    print("3. 开始训练模型")


def main():
    parser = argparse.ArgumentParser(description='数据集划分工具')

    parser.add_argument('--source', type=str, required=True,
                        help='源数据目录 (包含images和labels)')
    parser.add_argument('--output', type=str, default='data',
                        help='输出目录 (默认: data)')
    parser.add_argument('--train-ratio', type=float, default=0.8,
                        help='训练集比例 (默认: 0.8)')
    parser.add_argument('--val-ratio', type=float, default=0.1,
                        help='验证集比例 (默认: 0.1)')
    parser.add_argument('--test-ratio', type=float, default=0.1,
                        help='测试集比例 (默认: 0.1)')
    parser.add_argument('--seed', type=int, default=42,
                        help='随机种子 (默认: 42)')

    args = parser.parse_args()

    # 检查比例和是否为1
    total_ratio = args.train_ratio + args.val_ratio + args.test_ratio
    if abs(total_ratio - 1.0) > 0.01:
        print(f"✗ 比例总和必须为1.0，当前为: {total_ratio}")
        return

    split_dataset(
        source_dir=args.source,
        output_dir=args.output,
        train_ratio=args.train_ratio,
        val_ratio=args.val_ratio,
        test_ratio=args.test_ratio,
        seed=args.seed
    )


if __name__ == "__main__":
    main()
