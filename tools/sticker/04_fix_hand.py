"""第四步：给女主抬起的左手补形。

源图里她的左手被运动模糊糊掉、分割掩码也把它整只切掉了，于是手臂末端
是一个秃头，看起来"没有手"。这里按手臂的走向和贴纸的扁平配色，补一只
和男主同款的手：**不画手指**，就是一块从手腕略微放宽、再向指尖收圆的
平滑手掌（男主的手也是这样，看不出手指但很自然）。写入掩码 + 用与手臂
同源的肤色平涂到源图上，再交给后面的矢量化/调色板/白描边流程。

产物：build/clean_mask2.npy、build/src_painted.png
"""
import numpy as np
from PIL import Image, ImageDraw

import paths

paths.ensure_work()

# —— 手部几何（源图像素坐标）——
# 起点压在掩码内部（那里前臂还够宽），往外延伸成一整块手掌，
# 否则直接在掩码断口(只有 5px 宽)上接，会在手腕处捏出一个"腰"。
WRIST = (212.0, 500.0)
UX, UY = -0.512, -0.859     # 手/臂轴方向（指向左上）
VX, VY = -UY, UX            # 垂直方向，+v 朝手臂受光的一侧（右上）

HAND_LEN = 41.0             # 起点 → 指尖
# (沿轴距离, 半宽)：跟前臂 17~18px 的宽度接上，掌部微鼓，向指尖收圆
PROFILE = [(0.0, 8.8), (10.0, 8.3), (17.0, 9.2), (28.0, 8.0), (36.0, 6.2), (41.0, 4.4)]
BEND = -2.0                 # 中心线轻微侧弯，别像一根直棍

# 肤色（取自前臂横截面的实际像素，调色板量化后会归到同一批色块）
SKIN = (176, 112, 60)
SKIN_LIT = (214, 173, 136)


def build_hand(hw):
    m = Image.new('L', (hw[1], hw[0]), 0)
    d = ImageDraw.Draw(m)
    wx, wy = WRIST

    def center(s):
        f = s / HAND_LEN
        return (wx + UX * s + VX * BEND * f * f,
                wy + UY * s + VY * BEND * f * f)

    def halfw(s):
        for (s0, w0), (s1, w1) in zip(PROFILE, PROFILE[1:]):
            if s <= s1:
                return w0 + (w1 - w0) * (s - s0) / (s1 - s0)
        return PROFILE[-1][1]

    steps = 40
    top, bot = [], []
    for i in range(steps + 1):
        s = HAND_LEN * i / steps
        cx, cy = center(s)
        w = halfw(s)
        top.append((cx + VX * w, cy + VY * w))
        bot.append((cx - VX * w, cy - VY * w))
    d.polygon(top + bot[::-1], fill=255)

    cx, cy = center(HAND_LEN)
    r = PROFILE[-1][1]
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=255)
    return np.array(m) > 128


def translate(mask, dx, dy):
    dx, dy = int(round(dx)), int(round(dy))
    out = np.zeros_like(mask)
    h, w = mask.shape
    xs0, xs1 = max(0, dx), min(w, w + dx)
    ys0, ys1 = max(0, dy), min(h, h + dy)
    out[ys0:ys1, xs0:xs1] = mask[ys0 - dy:ys1 - dy, xs0 - dx:xs1 - dx]
    return out


img = np.asarray(Image.open(paths.SOURCE).convert('RGB')).copy()
fg = np.load(paths.source_mask())

hand = build_hand(fg.shape) & ~fg

# 受光：只沿 +v 那一侧的轮廓描一条窄亮带，和前臂的高光连成一条。
# 注意只做亮边、不做暗边 —— 暗边会围着每根手指各长一圈，指缝看着像爪子的沟。
painted = img.copy()
painted[hand] = SKIN
lit = hand & ~translate(hand, -VX * 4.0, -VY * 4.0)
painted[lit] = SKIN_LIT

np.save(paths.PATCHED_MASK, fg | hand)
Image.fromarray(painted).save(paths.PAINTED_SOURCE)

vis = painted.copy()
edge = hand & ~translate(hand, -1, 0)
vis[edge] = [0, 255, 0]
Image.fromarray(vis[440:520, 165:250]).resize((85 * 7, 80 * 7), Image.NEAREST).save(
    paths.WORK / 'hand_fix_preview.png')
print('hand area %d' % int(hand.sum()))
print('saved', paths.PATCHED_MASK, paths.PAINTED_SOURCE)