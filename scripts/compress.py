import os
from pathlib import Path
from PIL import Image

def optimize_images_for_android(target_dir, quality=85, max_dimension=1920):
    """
    Scans a directory for images, scales them down if they exceed max_dimension,
    and converts them to WebP format for optimal Android APK sizing.
    """
    supported_formats = {'.png', '.jpg', '.jpeg'}
    target_path = Path(target_dir)

    # Create an output directory so we don't destructively overwrite your originals
    output_dir = target_path / "android_optimized"
    output_dir.mkdir(exist_ok=True)

    total_saved_bytes = 0

    print(f"Scanning directory: {target_dir}...")

    for file_path in target_path.iterdir():
        if file_path.suffix.lower() in supported_formats:
            try:
                with Image.open(file_path) as img:
                    original_size = os.path.getsize(file_path)

                    # 1. Resize if it's absurdly large (maintains aspect ratio)
                    # For Android, anything over your target screen size (like 1920) is wasted RAM
                    img.thumbnail((max_dimension, max_dimension), Image.Resampling.LANCZOS)

                    # 2. Convert to RGB if it's a PNG with transparency we are stripping,
                    # or keep RGBA if you want to keep transparency in WebP.
                    # WebP supports alpha channels natively!
                    if img.mode not in ('RGB', 'RGBA'):
                        img = img.convert('RGBA')

                    # 3. Save as WebP
                    new_filename = f"{file_path.stem}.webp"
                    new_file_path = output_dir / new_filename

                    # Quality 85 is the sweet spot for WebP.
                    # It's virtually indistinguishable from the original but massively smaller.
                    img.save(new_file_path, "webp", quality=quality, method=6)

                    new_size = os.path.getsize(new_file_path)
                    saved = original_size - new_size
                    total_saved_bytes += saved

                    print(f"Optimized: {file_path.name}")
                    print(f"  -> Original: {original_size / 1024:.1f} KB | New: {new_size / 1024:.1f} KB | Saved: {saved / 1024:.1f} KB")

            except Exception as e:
                print(f"Failed to process {file_path.name}: {e}")

    print("\n" + "="*40)
    print(f"Optimization Complete!")
    print(f"Total space saved: {total_saved_bytes / (1024 * 1024):.2f} MB")
    print("="*40)

if __name__ == "__main__":
    # Point this to the folder containing your Pexels images
    IMAGE_DIRECTORY = "./"

    # Run the optimizer
    # Note: For RecyclerView items (like album art lists), max_dimension=500 is usually plenty!
    # 1920 is kept here assuming these are full-screen backgrounds.
    optimize_images_for_android(IMAGE_DIRECTORY, quality=85, max_dimension=1920)