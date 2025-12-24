# TensorFlow Lite Model Files

## Required Model File

Place your trained YOLO TensorFlow Lite model file in this directory:

- **File name**: `game_model_320.tflite`
- **Input size**: 320x320x3 (RGB image)
- **Output format**: YOLO detection output [1, num_boxes, num_classes + 5]

## Model Classes

The model should be trained to detect the following game elements:

0. **enemy** - Enemy characters or units
1. **skill_button** - Skill/ability buttons
2. **start_button** - Start/play buttons
3. **claim_button** - Reward claim buttons
4. **close_button** - Close/exit buttons
5. **item** - In-game items or objects
6. **character** - Player character or friendly units
7. **obstacle** - Obstacles or hazards

## Training the Model

To train your own model:

1. Collect screenshots from your target game
2. Annotate objects using tools like LabelImg or CVAT
3. Train a YOLOv5/YOLOv8 model
4. Convert to TensorFlow Lite format:
   ```bash
   # For YOLOv5
   python export.py --weights best.pt --include tflite --img 320

   # For YOLOv8
   yolo export model=best.pt format=tflite imgsz=320
   ```
5. Place the `.tflite` file here as `game_model_320.tflite`

## Note

Without a model file, the app will start but detection functionality will fail.
For development/testing, you can create a dummy model or use transfer learning
from a pre-trained YOLO model.
