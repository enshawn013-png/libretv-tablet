"""生成 LibreTV 的启动图标 PNG（深底 + 播放三角），不依赖 Pillow。"""
import struct, zlib, os, math

SIZE = 192
BG = (18, 49, 41)      # 深绿，和 App 主题一致
FG = (240, 179, 94)    # 暖橙，和 LibreTV 强调色一致

def px(x, y):
    # 圆角方块背景
    r = SIZE * 0.22
    cx1, cy1 = r, r
    cx2, cy2 = SIZE - r, SIZE - r
    inside = True
    if x < cx1 and y < cy1:
        inside = (x - cx1) ** 2 + (y - cy1) ** 2 <= r * r
    elif x > cx2 and y < cy1:
        inside = (x - cx2) ** 2 + (y - cy1) ** 2 <= r * r
    elif x < cx1 and y > cy2:
        inside = (x - cx1) ** 2 + (y - cy2) ** 2 <= r * r
    elif x > cx2 and y > cy2:
        inside = (x - cx2) ** 2 + (y - cy2) ** 2 <= r * r
    if not inside:
        return (0, 0, 0, 0)
    # 播放三角：顶点在右侧
    left = SIZE * 0.36
    right = SIZE * 0.70
    top = SIZE * 0.28
    bottom = SIZE * 0.72
    mid = (top + bottom) / 2
    if left <= x <= right:
        t = (x - left) / (right - left)
        half = (bottom - top) / 2 * (1 - t)
        if mid - half <= y <= mid + half:
            return FG + (255,)
    return BG + (255,)

raw = b''
for y in range(SIZE):
    raw += b'\x00'  # filter type 0
    for x in range(SIZE):
        raw += bytes(px(x, y))

def chunk(tag, data):
    c = struct.pack('>I', len(data)) + tag + data
    return c + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)

png = b'\x89PNG\r\n\x1a\n'
png += chunk(b'IHDR', struct.pack('>IIBBBBB', SIZE, SIZE, 8, 6, 0, 0, 0))
png += chunk(b'IDAT', zlib.compress(raw, 9))
png += chunk(b'IEND', b'')

out = os.path.join(os.path.dirname(__file__), 'ic_launcher.png')
os.makedirs(os.path.dirname(out), exist_ok=True)
open(out, 'wb').write(png)
print('wrote', out, os.path.getsize(png), 'bytes')
