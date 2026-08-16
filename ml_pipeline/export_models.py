"""
Spatial Eye — Model Exporter (ONNX & TFLite)
Exports trained PyTorch obstacle detector models to optimized on-device formats.
"""

import os
from pathlib import Path

def export_to_onnx_and_tflite(weights_path: str, output_dir: str = "./exported_models"):
    """Exports model weights to ONNX and Float16 quantized TFLite format."""
    os.makedirs(output_dir, exist_ok=True)
    
    try:
        from ultralytics import YOLO
    except ImportError:
        print("Ultralytics library required for export.")
        return

    print(f"Loading model weights from {weights_path}...")
    model = YOLO(weights_path)

    # 1. Export ONNX (Native cross-platform execution)
    print("Exporting ONNX format for mobile inference engine...")
    onnx_file = model.export(format="onnx", imgsz=416, dynamic=False, opset=12)
    print(f"ONNX model exported: {onnx_file}")

    # 2. Export TFLite (TensorFlow Lite for Android/iOS NNAPI & Metal acceleration)
    print("Exporting TFLite format with INT8/FP16 quantization...")
    tflite_file = model.export(format="tflite", imgsz=416, int8=False)
    print(f"TFLite model exported: {tflite_file}")

    print(f"Models successfully exported to: {output_dir}")

if __name__ == "__main__":
    import sys
    weights = sys.argv[1] if len(sys.argv) > 1 else "yolov8n.pt"
    export_to_onnx_and_tflite(weights)
