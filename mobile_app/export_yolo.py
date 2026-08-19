from ultralytics import YOLO

def export_yolov8n_tflite():
    print("Loading YOLOv8n model...")
    # Load the official YOLOv8 nano model
    model = YOLO("yolov8n.pt")
    
    print("Exporting to TFLite FP16...")
    # Export to TFLite with float16 quantization
    # This reduces model size by half with minimal accuracy loss, suitable for mobile.
    model.export(
        format="litert", # Replaced tflite with litert based on Ultralytics 8.4.83+
        quantize=None,   # None for FP32 to ensure maximum accuracy and precision
        imgsz=640        # 640 for better precision and accuracy
    )
    print("Export complete. Check the 'runs/detect' folder or current directory for the .tflite file.")
    print("Rename the file to 'yolov8n_float16.tflite' and move it to 'assets/models/'.")

if __name__ == "__main__":
    export_yolov8n_tflite()
