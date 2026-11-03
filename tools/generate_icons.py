import os
import sys
from PIL import Image, ImageDraw, ImageOps

def make_round(img):
    size = img.size
    mask = Image.new('L', size, 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0, size[0], size[1]), fill=255)
    output = ImageOps.fit(img, mask.size, centering=(0.5, 0.5))
    output.putalpha(mask)
    return output

def generate_android_icons(master_image_path, res_dir):
    sizes = {
        'mipmap-mdpi': 48,
        'mipmap-hdpi': 72,
        'mipmap-xhdpi': 96,
        'mipmap-xxhdpi': 144,
        'mipmap-xxxhdpi': 192,
    }

    if not os.path.exists(master_image_path):
        print(f"Error: master image not found: {master_image_path}")
        return False

    master = Image.open(master_image_path).convert('RGBA')

    for folder, size in sizes.items():
        target_dir = os.path.join(res_dir, folder)
        os.makedirs(target_dir, exist_ok=True)

        # Standard icon
        icon = master.resize((size, size), Image.Resampling.LANCZOS)
        icon.save(os.path.join(target_dir, 'ic_launcher.png'), 'PNG')

        # Round icon
        round_icon = make_round(icon)
        round_icon.save(os.path.join(target_dir, 'ic_launcher_round.png'), 'PNG')

        print(f"Generated {folder}: {size}x{size}")

    print("All mipmap icons generated successfully!")
    return True

if __name__ == '__main__':
    if len(sys.argv) < 3:
        print("Usage: python generate_icons.py <master_image> <res_dir>")
        sys.exit(1)
    generate_android_icons(sys.argv[1], sys.argv[2])
