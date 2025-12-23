"""
模型转换工具 - 将YOLO模型转换为手机可用格式
Model Conversion Tool - Convert YOLO to Mobile Format
"""

import argparse
from pathlib import Path


def convert_to_tflite(
    model_path: str,
    output_dir: str = "android/models",
    img_size: int = 320,
    quantize: bool = True
):
    """
    转换YOLO模型为TensorFlow Lite

    Args:
        model_path: YOLO模型路径 (.pt)
        output_dir: 输出目录
        img_size: 输入图像尺寸 (推荐320或640)
        quantize: 是否使用INT8量化
    """
    try:
        from ultralytics import YOLO

        print("="*60)
        print("YOLO模型转换为TFLite")
        print("="*60)

        # 加载模型
        print(f"\n1. 加载模型: {model_path}")
        model = YOLO(model_path)

        # 导出TFLite
        print(f"2. 导出TFLite (img_size={img_size}, quantize={quantize})")
        export_path = model.export(
            format="tflite",
            imgsz=img_size,
            int8=quantize,
        )

        print(f"\n✓ 转换完成!")
        print(f"  输出路径: {export_path}")
        print(f"  模型尺寸: {Path(export_path).stat().st_size / 1024 / 1024:.1f} MB")

        # 提取模型文件
        import shutil
        tflite_dir = Path(export_path).parent
        tflite_file = tflite_dir / ("best_int8.tflite" if quantize else "best_float32.tflite")

        if tflite_file.exists():
            output_path = Path(output_dir)
            output_path.mkdir(parents=True, exist_ok=True)

            target_file = output_path / f"game_model_{img_size}.tflite"
            shutil.copy2(tflite_file, target_file)

            print(f"\n✓ 模型已复制到: {target_file}")

        print("\n下一步:")
        print("1. 将 .tflite 文件放入 Android 项目的 assets 目录")
        print("2. 在 Android 代码中加载模型")

        return export_path

    except ImportError:
        print("错误: 请安装 ultralytics")
        print("运行: pip install ultralytics")
        return None

    except Exception as e:
        print(f"转换失败: {e}")
        return None


def convert_to_onnx(
    model_path: str,
    output_dir: str = "android/models",
    img_size: int = 320,
    simplify: bool = True
):
    """
    转换YOLO模型为ONNX

    Args:
        model_path: YOLO模型路径
        output_dir: 输出目录
        img_size: 输入图像尺寸
        simplify: 是否简化模型
    """
    try:
        from ultralytics import YOLO

        print("="*60)
        print("YOLO模型转换为ONNX")
        print("="*60)

        # 加载模型
        print(f"\n1. 加载模型: {model_path}")
        model = YOLO(model_path)

        # 导出ONNX
        print(f"2. 导出ONNX (img_size={img_size})")
        export_path = model.export(
            format="onnx",
            imgsz=img_size,
            simplify=simplify,
        )

        print(f"\n✓ 转换完成!")
        print(f"  输出路径: {export_path}")
        print(f"  模型尺寸: {Path(export_path).stat().st_size / 1024 / 1024:.1f} MB")

        # 复制文件
        import shutil
        output_path = Path(output_dir)
        output_path.mkdir(parents=True, exist_ok=True)

        target_file = output_path / f"game_model_{img_size}.onnx"
        shutil.copy2(export_path, target_file)

        print(f"\n✓ 模型已复制到: {target_file}")
        print("\n使用ONNX Runtime Mobile加载此模型")

        return export_path

    except ImportError:
        print("错误: 请安装 ultralytics")
        return None

    except Exception as e:
        print(f"转换失败: {e}")
        return None


def test_tflite_inference(model_path: str, test_image: str = None):
    """测试TFLite模型推理"""
    try:
        import tensorflow as tf
        import numpy as np
        import cv2

        print("\n测试TFLite推理...")

        # 加载模型
        interpreter = tf.lite.Interpreter(model_path=model_path)
        interpreter.allocate_tensors()

        # 获取输入输出详情
        input_details = interpreter.get_input_details()
        output_details = interpreter.get_output_details()

        print(f"输入形状: {input_details[0]['shape']}")
        print(f"输出形状: {output_details[0]['shape']}")

        # 如果有测试图像
        if test_image and Path(test_image).exists():
            img = cv2.imread(test_image)
            img_size = input_details[0]['shape'][1:3]

            # 预处理
            img_resized = cv2.resize(img, img_size)
            img_normalized = img_resized.astype(np.float32) / 255.0
            img_input = np.expand_dims(img_normalized, axis=0)

            # 推理
            import time
            start = time.time()

            interpreter.set_tensor(input_details[0]['index'], img_input)
            interpreter.invoke()
            output = interpreter.get_tensor(output_details[0]['index'])

            elapsed = (time.time() - start) * 1000

            print(f"✓ 推理成功! 耗时: {elapsed:.1f}ms")
        else:
            print("未提供测试图像，跳过推理测试")

    except Exception as e:
        print(f"测试失败: {e}")


def main():
    parser = argparse.ArgumentParser(description='YOLO模型转换工具')

    parser.add_argument('--model', type=str, required=True,
                        help='YOLO模型路径 (.pt)')
    parser.add_argument('--format', type=str, default='tflite',
                        choices=['tflite', 'onnx', 'both'],
                        help='目标格式')
    parser.add_argument('--output', type=str, default='android/models',
                        help='输出目录')
    parser.add_argument('--img-size', type=int, default=320,
                        help='输入图像尺寸 (推荐: 320, 640)')
    parser.add_argument('--no-quantize', action='store_true',
                        help='不使用INT8量化 (TFLite)')
    parser.add_argument('--test-image', type=str, default=None,
                        help='测试图像路径')

    args = parser.parse_args()

    # 检查模型文件
    if not Path(args.model).exists():
        print(f"错误: 模型文件不存在: {args.model}")
        return

    # 转换
    if args.format in ['tflite', 'both']:
        tflite_path = convert_to_tflite(
            args.model,
            args.output,
            args.img_size,
            not args.no_quantize
        )

        # 测试
        if tflite_path and args.test_image:
            test_tflite_inference(tflite_path, args.test_image)

    if args.format in ['onnx', 'both']:
        convert_to_onnx(
            args.model,
            args.output,
            args.img_size
        )


if __name__ == "__main__":
    # 示例用法
    print("\n=== YOLO模型转换工具 ===\n")
    print("使用方法:")
    print("python android/convert_model.py --model models/best.pt --format tflite")
    print("python android/convert_model.py --model models/best.pt --format onnx")
    print("python android/convert_model.py --model models/best.pt --format both\n")

    # 如果直接运行
    import sys
    if len(sys.argv) == 1:
        print("提示: 请提供 --model 参数")
        print("\n示例:")
        print("python android/convert_model.py --model models/best.pt --format tflite --img-size 320")
    else:
        main()
