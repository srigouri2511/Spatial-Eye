"""
Spatial Eye — On-Device Obstacle Object Detection Fine-Tuning Script
Fine-tunes YOLOv8n on indoor and outdoor obstacle datasets for navigation assistance.
"""

import os
import yaml
from pathlib import Path

OBSTACLE_CLASSES = [
    "stairs", "curb", "pole", "furniture", 
    "vehicle", "door", "person", "pothole", "wall"
]

def generate_data_yaml(output_path: str, data_dir: str):
    """Generates YOLO format dataset config YAML file."""
    config = {
        'path': os.path.abspath(data_dir),
        'train': 'images/train',
        'val': 'images/val',
        'names': {i: name for i, name in enumerate(OBSTACLE_CLASSES)}
    }
    with open(output_path, 'w') as f:
        yaml.dump(config, f, default_flow_style=False)
    print(f"Generated dataset configuration at: {output_path}")

def train_model(data_yaml: str, epochs: int = 50, imgsz: int = 416, batch: int = 16):
    """Trains lightweight YOLOv8n detector tuned for mobile real-time inference."""
    try:
        from ultralytics import YOLO
    except ImportError:
        print("Ultralytics library required for YOLOv8 training. Install via `pip install ultralytics`.")
        return

    print("Initializing YOLOv8n nano architecture for on-device inference...")
    model = YOLO("yolov8n.pt")  # Start with pretrained weights

    print(f"Starting training on {len(OBSTACLE_CLASSES)} custom obstacle classes for {epochs} epochs...")
    results = model.train(
        data=data_yaml,
        epochs=epochs,
        imgsz=imgsz,
        batch=batch,
        workers=4,
        project="spatial_eye_train",
        name="obstacle_yolov8n",
        exist_ok=True
    )
    
    print("Training complete! Saving best model weights...")
    best_weights = Path("spatial_eye_train/obstacle_yolov8n/weights/best.pt")
    print(f"Best model saved to: {best_weights}")
    return str(best_weights)

if __name__ == "__main__":
    import sys
    data_dir = sys.argv[1] if len(sys.argv) > 1 else "./dataset"
    yaml_path = "./obstacle_dataset.yaml"
    generate_data_yaml(yaml_path, data_dir)
    print(f"To start fine-tuning, populate {data_dir} with annotated images and run train_model('{yaml_path}').")
