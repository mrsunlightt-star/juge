"""第二步：DeepLabV3 给粗轮廓，GrabCut 在轮廓带内按颜色细分。

DeepLab 的轮廓会溢到两人之间、手臂之间，把镂空糊死。这里把它的输出当成
"前景候选带"，用 GrabCut 在带内按颜色重新划分，找回这些镂空。

产物：build/refine_mask.npy
"""
import numpy as np
import cv2
from PIL import Image

import paths

paths.ensure_work()
ELL = lambda r: cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (r * 2 + 1, r * 2 + 1))

img = np.asarray(Image.open(paths.SOURCE).convert('RGB'))
p = np.load(paths.DL_PERSON)
dl = (p > 0.5).astype(np.uint8)

# 只保留主连通域，去掉零散误检
n, lab, st, _ = cv2.connectedComponentsWithStats(dl, 8)
big = 1 + int(np.argmax(st[1:, 4]))
dl = (lab == big).astype(np.uint8)
dl = cv2.morphologyEx(dl, cv2.MORPH_CLOSE, ELL(4))

sure_fg = cv2.erode(dl, ELL(4))
ring = cv2.dilate(dl, ELL(20))
gc = np.full(img.shape[:2], cv2.GC_PR_BGD, np.uint8)
gc[ring > 0] = cv2.GC_PR_FGD
gc[dl > 0] = cv2.GC_PR_FGD
gc[sure_fg > 0] = cv2.GC_FGD
gc[ring == 0] = cv2.GC_BGD

bgd = np.zeros((1, 65), np.float64)
fgd = np.zeros((1, 65), np.float64)
cv2.grabCut(img, gc, None, bgd, fgd, 8, cv2.GC_INIT_WITH_MASK)
fg = np.isin(gc, (cv2.GC_FGD, cv2.GC_PR_FGD)).astype(np.uint8)

fg = cv2.morphologyEx(fg, cv2.MORPH_CLOSE, ELL(3))
fg = cv2.morphologyEx(fg, cv2.MORPH_OPEN, ELL(2))
# 主连通域 + 与其邻近的大块（手指/鞋这类可能断开）
n, lab, st, _ = cv2.connectedComponentsWithStats(fg, 8)
big = 1 + int(np.argmax(st[1:, 4]))
near = cv2.dilate((lab == big).astype(np.uint8), ELL(14)).astype(bool)
keep = (lab == big)
for i in range(1, n):
    if i != big and st[i, 4] > 150 and (near & (lab == i)).any():
        keep |= (lab == i)
fg = keep.astype(np.uint8)

# 只填小孔，保留人物之间的镂空
free = (fg == 0).astype(np.uint8)
pad = np.pad(free, 1, constant_values=1)
cv2.floodFill(pad, np.zeros((pad.shape[0] + 2, pad.shape[1] + 2), np.uint8), (0, 0), 2)
outside = (pad == 2)[1:-1, 1:-1]
holes = free.astype(bool) & ~outside
nh, lh, sh, _ = cv2.connectedComponentsWithStats(holes.astype(np.uint8), 4)
for i in range(1, nh):
    if sh[i, 4] <= 400:
        fg[lh == i] = 1

fg = cv2.medianBlur((fg * 255).astype(np.uint8), 5) > 127
np.save(paths.REFINE_MASK, fg)
print('fg area %d' % int(fg.sum()))
ys, xs = np.nonzero(fg)
print('bbox %d %d %d %d' % (xs.min(), ys.min(), xs.max(), ys.max()))

vis = img.copy()
vis[fg] = (vis[fg] * 0.35 + np.array([255, 40, 40]) * 0.65).astype(np.uint8)
Image.fromarray(vis).save(paths.WORK / 'refine_overlay.png')