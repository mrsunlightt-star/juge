#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""微缩城市插画（棋盘格底）：抠掉"画进像素里的假透明棋盘格"，输出 RGBA。

## 为什么不能直接套 sky_matte

`juge_widget_sky_matte.matte()` 假设背景是**一块均匀的浅灰天空**，靠"从顶边洪水填充 +
固定容差"判背景。这张图的背景是**棋盘格**：两档中性灰（约 163~198 与 245~252），
两档差了 50 多，任何单一容差的洪水填充都跨不过方块边界，会把一半棋盘当成前景。

## 判据：中性色 + 与画布边界连通

棋盘格两档都是**中性灰**（R≈G≈B，饱和度 < 3），而城市主体是彩色插画
（青绿楼体、暖橙、朱红宫墙、米杏底座，饱和度普遍 > 10）。
所以背景 = 饱和度极低 **且** 从画布边界能连通走到的像素：

- 米杏底座（饱和度 15~45）不会被误伤；
- 城市内部的深色阴影虽然也偏中性，但**不与画布边界连通**，同样保得住。

用法：
    python3 tools/juge_widget_checker_matte.py --src <图> --out <png> [--debug <png>]
"""
import argparse

import cv2
import numpy as np

SAT_TOL = 9.0     # 饱和度低于此值算"中性"：棋盘格 ~1，米杏底座 ~15，留足余量
CLOSE_PX = 5      # 方块边缘的压缩噪点会让个别背景像素饱和度虚高，先闭运算补回来
ERODE_PX = 1      # 贴边那圈是"城市 × 棋盘格"的混色，收 1px 掉它，免得深色壁纸下起灰边


def matte_checker(bgr, sat_tol=SAT_TOL):
    """返回 (BGRA, 背景掩码)。背景掩码为 True 的像素来自棋盘格，应被抠掉。"""
    f = bgr.astype(np.float32)
    sat = f.max(axis=2) - f.min(axis=2)
    cand = (sat <= sat_tol).astype(np.uint8)
    cand = cv2.morphologyEx(cand, cv2.MORPH_CLOSE,
                            np.ones((CLOSE_PX, CLOSE_PX), np.uint8))

    h, w = cand.shape
    mask = np.zeros((h + 2, w + 2), np.uint8)
    flags = 8 | cv2.FLOODFILL_MASK_ONLY | (255 << 8)
    # 棋盘格整片连通，取几个边界种子即可；已填过就跳过
    for x in range(0, w, 16):
        for seed in ((x, 0), (x, h - 1)):
            if mask[seed[1] + 1, seed[0] + 1] == 0:
                cv2.floodFill(cand.copy(), mask, seed, 2, flags=flags)
    sure_bg = mask[1:-1, 1:-1] > 0

    fg = (~sure_bg).astype(np.uint8)
    if ERODE_PX > 0:
        k = np.ones((2 * ERODE_PX + 1, 2 * ERODE_PX + 1), np.uint8)
        fg = cv2.erode(fg, k)
    alpha = cv2.GaussianBlur(fg.astype(np.float32), (0, 0), 0.7)
    return np.dstack([f, alpha[..., None] * 255.0]).astype(np.uint8), sure_bg


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--debug", default="", help="写一张透明区铺斜纹的检查图")
    args = ap.parse_args()

    bgr = cv2.imread(args.src, cv2.IMREAD_COLOR)
    if bgr is None:
        raise SystemExit(f"读不到 {args.src}")
    rgba, sure_bg = matte_checker(bgr)
    a = rgba[:, :, 3].astype(np.float32) / 255.0
    ys, xs = np.where(a > 0.02)
    print(f"{rgba.shape[1]}×{rgba.shape[0]}  棋盘格 {100*sure_bg.mean():.1f}%  "
          f"内容 bbox x[{xs.min()},{xs.max()}] y[{ys.min()},{ys.max()}] "
          f"比例 {(xs.max()-xs.min()+1)/(ys.max()-ys.min()+1):.3f}")
    cv2.imwrite(args.out, rgba)
    if args.debug:
        h, w = a.shape
        sq = (np.indices((h, w)).sum(axis=0) // 40) % 2
        chk = (sq[..., None] * 60.0 + 90.0) * (1 - a[..., None]) + \
            rgba[:, :, :3].astype(np.float32) * a[..., None]
        cv2.imwrite(args.debug, chk.astype(np.uint8))
        print("检查图", args.debug)


if __name__ == "__main__":
    main()
