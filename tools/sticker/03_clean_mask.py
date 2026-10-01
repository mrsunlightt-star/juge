"""第三步：清掉掩码里残留的紫色天空块，并把被切掉的末端肢体捞回来。

- 紫色天空：GrabCut 会把人物边缘的紫调天空连进来，但只清"紧凑的块"，
  细的描边是轮廓抗锯齿，清了会让轮廓缺牙。
- 末端肢体：手掌这类在 DeepLabV3 里被整只切掉、GrabCut 也没捞回来。
  用暖色（皮肤）在掩码邻域内把成块的补回去 —— 但只手会漏，位置和形状都
  不靠谱，所以补完还要走 04_fix_hand.py 手工重画。

产物：build/clean_mask.npy（同时随仓库提交为 assets/clean_mask.npy）
"""
import numpy as np
import cv2
from PIL import Image

import paths

paths.ensure_work()
ELL = lambda r: cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (r * 2 + 1, r * 2 + 1))

img = np.asarray(Image.open(paths.SOURCE).convert('RGB')).astype(np.int16)
fg = np.load(paths.REFINE_MASK)

R, G, B = img[:, :, 0], img[:, :, 1], img[:, :, 2]
purple = (B - G > 40) & (B > 90) & (R > 55) & (R < 180) & fg

# 紧凑的紫块（腐蚀后仍活着）＝真的背景镂空；细线是轮廓抗锯齿，保留
core = cv2.erode(purple.astype(np.uint8), ELL(3))
n, lab, st, _ = cv2.connectedComponentsWithStats(core, 8)
kill = np.zeros_like(fg)
for i in range(1, n):
    if st[i, 4] >= 30:
        print('  kill purple blob area=%d box=%s' % (st[i, 4], st[i, :4]))
        kill |= (lab == i)
kill = cv2.dilate(kill.astype(np.uint8), ELL(3)).astype(bool)
fg = fg & ~kill

# 手掌这类末端肢体在 DeepLabV3 里被切掉、GrabCut 也没捞回来 —— 用暖色(皮肤)在掩码邻域内补回
warm = (R - B > 30) & (R > 70) & (fg == 0)
near = cv2.dilate(fg.astype(np.uint8), ELL(40)).astype(bool)
cand = (warm & near).astype(np.uint8)
n, lab, st, _ = cv2.connectedComponentsWithStats(cand, 8)
for i in range(1, n):
    if st[i, 4] < 40:
        continue
    blob = (lab == i).astype(np.uint8)
    if (cv2.dilate(blob, ELL(3)) & fg).any():
        print('  recover limb area=%d box=%s' % (st[i, 4], st[i, :4]))
        fg = fg | blob.astype(bool)

# 清完再收一次边：填小孔、去毛刺
fg = cv2.morphologyEx(fg.astype(np.uint8), cv2.MORPH_CLOSE, ELL(3))
fg = cv2.morphologyEx(fg, cv2.MORPH_OPEN, ELL(2))
n, lab, st, _ = cv2.connectedComponentsWithStats(fg, 8)
big = 1 + int(np.argmax(st[1:, 4]))
near = cv2.dilate((lab == big).astype(np.uint8), ELL(16)).astype(bool)
keep = (lab == big)
for i in range(1, n):
    if i != big and st[i, 4] > 120 and (near & (lab == i)).any():
        keep |= (lab == i)
fg = keep.astype(np.uint8)

free = (fg == 0).astype(np.uint8)
pad = np.pad(free, 1, constant_values=1)
cv2.floodFill(pad, np.zeros((pad.shape[0] + 2, pad.shape[1] + 2), np.uint8), (0, 0), 2)
outside = (pad == 2)[1:-1, 1:-1]
holes = free.astype(bool) & ~outside
nh, lh, sh, _ = cv2.connectedComponentsWithStats(holes.astype(np.uint8), 4)
for i in range(1, nh):
    if sh[i, 4] <= 350:
        fg[lh == i] = 1

fg = cv2.medianBlur((fg * 255).astype(np.uint8), 7) > 127
# 轻微平滑轮廓，去掉锯齿
sm = cv2.GaussianBlur((fg * 255).astype(np.float32), (0, 0), 2.0)
fg = sm > 127
np.save(paths.WORK_CLEAN_MASK, fg)
print('clean area %d' % int(fg.sum()))
ys, xs = np.nonzero(fg)
print('bbox %d %d %d %d' % (xs.min(), ys.min(), xs.max(), ys.max()))

st2 = np.zeros((*fg.shape, 4), np.uint8)
st2[..., :3] = img.astype(np.uint8)
st2[..., 3] = fg * 255
st2 = st2[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
ck = np.zeros_like(st2)[:, :, :3]
yy, xx = np.mgrid[0:st2.shape[0], 0:st2.shape[1]]
ck[((yy // 16) + (xx // 16)) % 2 == 0] = 200
ck[((yy // 16) + (xx // 16)) % 2 == 1] = 150
a = st2[:, :, 3:4].astype(np.float32) / 255
Image.fromarray((st2[:, :, :3] * a + ck * (1 - a)).astype(np.uint8)).save(paths.WORK / 'clean_checker.png')
print('saved', paths.WORK_CLEAN_MASK)