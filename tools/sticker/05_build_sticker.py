"""第五步：把补好手的人物重绘成扁平插画，再和路灯合成为贴纸素材。

流程：
  1) 掩码矢量化（approxPolyDP + Chaikin）得到平滑轮廓
  2) 调色板量化成扁平插画（色相约束避免深棕发被近黑西裤吞掉）
  3) 叠白描边
  4) 与路灯按参考图比例合成 → app/src/main/res/drawable/sticker_lalaland.webp

改了输出尺寸/比例后，记得同步 WidgetCanvasRenderer.kt 里的 STICKER_ART_ASPECT。
"""
import numpy as np
import cv2
from PIL import Image

import paths

paths.ensure_work()

SCALE = 2          # 高清源图放大倍数
NCOLOR = 16        # 调色板颜色数
SIGMA = 3.0        # 色块边界平滑
R_OUT = 11         # 白描边宽度（放大后坐标）
LAMP_RATIO = 1.66  # 路灯高度 / 人物高度（取自参考图）
GAP_RATIO = 0.40   # 人物与路灯的间距 / 人物高度
OUT_H = 1200       # 输出高度
EPS = 0.8          # 轮廓简化容差（越小越贴原形，手部轮廓靠它保住）
ITERS = 2          # Chaikin 平滑次数
OUT = paths.DRAWABLE / 'sticker_lalaland.webp'

ELL = lambda r: cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (r * 2 + 1, r * 2 + 1))


def _depth(hier, i):
    d = 0
    while hier[i][3] != -1:
        i = hier[i][3]
        d += 1
    return d


def chaikin(pts, iters=3):
    for _ in range(iters):
        nxt = np.empty((len(pts) * 2, 2), np.float64)
        nxt[0::2] = pts * 0.75 + np.roll(pts, -1, axis=0) * 0.25
        nxt[1::2] = pts * 0.25 + np.roll(pts, -1, axis=0) * 0.75
        pts = nxt
    return pts


def polygon_silhouette(mask, scale, eps=1.4, iters=3):
    conts, hier = cv2.findContours(mask.astype(np.uint8), cv2.RETR_CCOMP, cv2.CHAIN_APPROX_NONE)
    h, w = mask.shape
    out = np.zeros((h * scale, w * scale), np.uint8)
    if hier is None:
        return out.astype(bool)
    hier = hier[0]
    for i in sorted(range(len(conts)), key=lambda i: _depth(hier, i)):
        pts = cv2.approxPolyDP(conts[i], eps, True).reshape(-1, 2).astype(np.float64)
        if len(pts) < 3:
            continue
        pts = chaikin(pts, iters)
        pts = (pts + 0.5) * scale
        cv2.fillPoly(out, [np.round(pts).astype(np.int32).reshape(-1, 1, 2)],
                     1 if _depth(hier, i) % 2 == 0 else 0, cv2.LINE_AA)
    return out > 0


def soft_split(labels, n):
    h, w = labels.shape
    resp = np.zeros((n, h, w), np.float32)
    for i in range(n):
        resp[i] = cv2.GaussianBlur((labels == i).astype(np.float32), (0, 0), SIGMA)
    return np.argmax(resp, axis=0)


def redraw(rgb, mask):
    a = mask.astype(np.float32)
    k = 9
    num = cv2.blur(rgb.astype(np.float32) * a[:, :, None], (k, k))
    den = cv2.blur(a, (k, k))[:, :, None]
    rgb = np.clip(num / np.maximum(den, 1e-6), 0, 255).astype(np.uint8)
    rgb[~mask] = 0

    core = cv2.erode(mask.astype(np.uint8), ELL(int(1.6 * SCALE)))
    data = rgb[core > 0].astype(np.float32)
    crit = (cv2.TERM_CRITERIA_EPS + cv2.TERM_CRITERIA_MAX_ITER, 30, 0.5)
    _, _, centers = cv2.kmeans(data, NCOLOR, None, crit, 8, cv2.KMEANS_PP_CENTERS)
    centers = centers.astype(np.uint8)

    ys, xs = np.nonzero(mask)
    px = rgb[ys, xs].astype(np.int32)
    d = ((px[:, None, :] - centers[None, :, :].astype(np.int32)) ** 2).sum(2)
    cwarm = (centers[:, 0].astype(np.int32) - centers[:, 2].astype(np.int32)) > 4
    pwarm = (px[:, 0] - px[:, 2]) > 12
    d[np.ix_(pwarm, ~cwarm)] = 1 << 30
    lab = np.zeros(mask.shape, np.int32)
    lab[ys, xs] = np.argmin(d, axis=1)
    lab = soft_split(lab, NCOLOR)

    canvas = np.zeros((*mask.shape, 3), np.uint8)
    for i in range(NCOLOR):
        canvas[(lab == i) & mask] = centers[i]
    return canvas


def outline(rgb, mask, r):
    hard = mask.astype(np.uint8)
    dil = cv2.dilate(hard * 255, ELL(r)).astype(np.float32) / 255.0
    dil = cv2.GaussianBlur(dil, (0, 0), 1.0)
    al = np.maximum(dil, hard.astype(np.float32))
    rgba = np.dstack([rgb, (al * 255).astype(np.uint8)])
    rgba[(al > 0.55) & (~mask)] = [255, 255, 255, 255]
    ys, xs = np.nonzero(rgba[:, :, 3] > 8)
    return rgba[ys.min():ys.max() + 1, xs.min():xs.max() + 1]


def load_couple():
    img = np.asarray(Image.open(paths.PAINTED_SOURCE).convert('RGB'))
    m = np.load(paths.PATCHED_MASK)
    ys, xs = np.nonzero(m)
    y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
    sub = img[y0:y1, x0:x1]
    ksub = m[y0:y1, x0:x1]

    h, w = ksub.shape
    rgb = cv2.resize(sub, (w * SCALE, h * SCALE), interpolation=cv2.INTER_LANCZOS4)
    mask = polygon_silhouette(ksub, SCALE, EPS, ITERS)
    rgb[~mask] = 0
    return outline(redraw(rgb, mask), mask, R_OUT)


def load_lamp(target_h):
    m = np.load(paths.LAMP_MASK).astype(np.uint8)
    rgb = np.asarray(Image.open(paths.LAMP_RGB).convert('RGB'))
    h, w = m.shape
    s = target_h / h
    nw, nh = max(1, int(round(w * s))), target_h
    rgb2 = cv2.resize(rgb, (nw, nh), interpolation=cv2.INTER_LANCZOS4)
    m2 = cv2.resize(m.astype(np.float32), (nw, nh), interpolation=cv2.INTER_LANCZOS4) > 0.5
    return outline(rgb2, m2, int(round(R_OUT * s)))


def place(dst, src, x):
    y = dst.shape[0] - src.shape[0]
    a = src[:, :, 3:4].astype(np.float32) / 255.0
    reg = dst[y:y + src.shape[0], x:x + src.shape[1], 0:3].astype(np.float32)
    dst[y:y + src.shape[0], x:x + src.shape[1], 0:3] = (src[:, :, :3] * a + reg * (1 - a)).astype(np.uint8)
    dst[y:y + src.shape[0], x:x + src.shape[1], 3] = np.maximum(
        dst[y:y + src.shape[0], x:x + src.shape[1], 3], src[:, :, 3])


couple = load_couple()
Hc = couple.shape[0]
lamp = load_lamp(int(round(Hc * LAMP_RATIO)))
gap = int(round(Hc * GAP_RATIO))
print('couple %s lamp %s gap %d' % (couple.shape, lamp.shape, gap))

H = lamp.shape[0]
W = couple.shape[1] + gap + lamp.shape[1]
canvas = np.zeros((H, W, 4), np.uint8)
place(canvas, couple, 0)
place(canvas, lamp, couple.shape[1] + gap)

s = OUT_H / H
canvas = cv2.resize(canvas, (int(round(W * s)), OUT_H), interpolation=cv2.INTER_AREA)

# 颜色外扩，避免缩放时出现彩边/黑边
alpha = canvas[:, :, 3]
rgb = canvas[:, :, :3].copy()
known = alpha > 0
ker = np.ones((3, 3), np.uint8)
for _ in range(14):
    dil = cv2.dilate(rgb, ker)
    dila = cv2.dilate((known * 255).astype(np.uint8), ker) > 0
    fill = dila & (~known)
    rgb[fill] = dil[fill]
    known = known | fill
canvas[:, :, :3] = rgb

OUT.parent.mkdir(parents=True, exist_ok=True)
Image.fromarray(canvas).save(OUT, quality=92, method=6)
print('saved %s %s' % (OUT, canvas.shape))
print('aspect %.4f  -> WidgetCanvasRenderer.STICKER_ART_ASPECT = %df / %df'
      % (canvas.shape[1] / canvas.shape[0], canvas.shape[1], canvas.shape[0]))

for name, bgv in (('dark', 30), ('light', 238)):
    bg = np.zeros_like(canvas)
    bg[:, :, 0:3] = bgv
    bg[:, :, 3] = 255
    a = canvas[:, :, 3:4].astype(np.float32) / 255.0
    Image.fromarray((canvas[:, :, :3] * a + bg[:, :, :3] * (1 - a)).astype(np.uint8)).save(
        paths.WORK / ('sticker_%s.png' % name))