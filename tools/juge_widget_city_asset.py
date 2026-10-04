#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把一张微缩城市插画压成 drawable 里的那一张素材（一个风格一张图）。

只做四件事：抠背景 → 裁到内容 bbox → 限宽 → 落 webp。**不做任何横向留白或分档**：
城市在组件里怎么摆、和文字区怎么接，全由渲染器按该风格的衔接决定，
素材本身保持插画的原始宽高比。

`--matte` 选抠图方式：
  - `sky`（默认）：背景是均匀浅灰天空（江西/上海/浙江那种）
  - `checker`：背景是画进像素里的假透明**棋盘格**（北京那张）

用法：
    python3 tools/juge_widget_city_asset.py --src <图> --name shanghai_city_cutout
    python3 tools/juge_widget_city_asset.py --src <图> --name beijing_city_cutout --matte checker
"""
import argparse
import os

import cv2
import numpy as np
from PIL import Image

from juge_widget_checker_matte import matte_checker
from juge_widget_sky_matte import matte

DRAWABLE_DIR = "/Users/sunqiqi/ans/app/src/main/res/drawable"
BG_DIR = "/Users/sunqiqi/ans/widget-bg"
WEBP_QUALITY = 92
MAX_WIDTH = 2048  # 与既有素材同量级，避免 webp 体积失控


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", required=True)
    ap.add_argument("--name", required=True, help="drawable 资源名，如 shanghai_city_cutout")
    ap.add_argument("--matte", choices=("sky", "checker"), default="sky")
    args = ap.parse_args()

    bgr = cv2.imread(args.src, cv2.IMREAD_COLOR)
    if bgr is None:
        raise SystemExit(f"读不到 {args.src}")
    if args.matte == "checker":
        rgba, sure_bg = matte_checker(bgr)
        bg_label = "棋盘格"
    else:
        rgba, sure_bg, _ = matte(bgr)
        bg_label = "天空"
    a = rgba[:, :, 3] > 8
    ys, xs = np.where(a)
    # 只裁掉四周真正的背景；贴边那一侧不动（城市底边就是画幅底边）
    rgba = rgba[ys.min():ys.max() + 1, xs.min():xs.max() + 1]

    h, w = rgba.shape[:2]
    if w > MAX_WIDTH:
        scale = MAX_WIDTH / w
        rgba = cv2.resize(rgba, (MAX_WIDTH, max(1, round(h * scale))),
                          interpolation=cv2.INTER_AREA)
    h, w = rgba.shape[:2]
    png = os.path.join(BG_DIR, args.name + ".png")
    Image.fromarray(cv2.cvtColor(rgba, cv2.COLOR_BGRA2RGBA)).save(png, "PNG", optimize=True)
    webp = os.path.join(DRAWABLE_DIR, args.name + ".webp")
    cv2.imwrite(webp, rgba, [cv2.IMWRITE_WEBP_QUALITY, WEBP_QUALITY])
    print(f"{args.name}: {w}×{h} 比例 {w/h:.3f}  {bg_label} {100*sure_bg.mean():.1f}%  "
          f"webp {os.path.getsize(webp)//1024} KB")


if __name__ == "__main__":
    main()
