#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""微缩城市插画：抠掉天空，输出带软边缘的 RGBA。

## 为什么按"从画布外能不能开阔地走到"判天空，而不是按颜色

天空是均匀浅灰（取样 BGR 226,226,227），而东方明珠球体顶帽的受光面**比天空还亮**
（229~250），两者颜色差不到一个容差档。任何"离背景色多远 → alpha 多大"的判据
都会顺着这片同色区一路吃进球体内部，在球面上留下一个洞；洞又和天空连成一片，
连"补内部孔洞"都救不回来。实测那版在深色壁纸下球帽缺了 23%。

所以轮廓交给**拓扑**：从顶边取种子做 FIXED_RANGE 洪水填充（比较对象是种子像素，
不是相邻像素，所以不会顺着渐变蔓延），填到的就是天空，其余一律算城市。
颜色只留给**边缘那 2px** 做羽化——贴边那一圈反正是天空与城市的混色，
按离天空色的远近给半透明，既不留灰边也不啃掉楼体。

用法：
    python3 tools/juge_widget_sky_matte.py --src <图> --out <png> [--debug <png>]
"""
import argparse

import cv2
import numpy as np

FLOOD_TOL = 10        # 洪水填充容差：天空渐变很平缓，10 足够覆盖，再大就会啃进同色的楼体
FEATHER_LO, FEATHER_HI = 4.0, 26.0   # 边缘带内按"离天空色多远"给 alpha
FG_DIST = 70          # 与天空色差这么大的一定是城市（兜底，防洪水填充从某条缝漏进内部）


def sky_seed(bgr):
    """天空的代表色：取顶边中位数（顶边整条都是天空）"""
    return np.median(bgr[0:3, :, :].reshape(-1, 3), axis=0)


def flood_sky(bgr, sky, tol=FLOOD_TOL):
    """从顶边洪水填充出天空。FIXED_RANGE 让每一步都和种子比，不会顺着渐变爬进城市"""
    h, w = bgr.shape[:2]
    img = bgr.copy()
    mask = np.zeros((h + 2, w + 2), np.uint8)
    flags = 8 | cv2.FLOODFILL_MASK_ONLY | (255 << 8) | cv2.FLOODFILL_FIXED_RANGE
    for x in range(0, w, 8):
        if mask[1, x + 1] == 0:
            cv2.floodFill(img, mask, (x, 0), (0, 0, 0),
                          loDiff=(tol,) * 3, upDiff=(tol,) * 3, flags=flags)
    return mask[1:-1, 1:-1] > 0


def punch_enclosed_sky(bgr, alpha, sure_bg, d_max=12.0, det_max=3.0):
    """把**被楼体封闭**的天空色平滑区抠成透明（环球金融中心顶部那个方洞）。

    洪水填充只能到"从画布外走得进去"的天空，所以镂空会被填成实体，
    深色壁纸上就是一块灰斑。判据必须同时满足三条才敢抠：
    与天空同色、局部平滑（球面亮帽虽然同色，但它有麻点纹理）、
    且**不与外界天空连通**（球帽是同色连通的，一判连通就保住了）。
    """
    g = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY).astype(np.float32)
    det = cv2.GaussianBlur(np.abs(cv2.Laplacian(g, cv2.CV_32F)), (0, 0), 1.5)
    d = np.abs(bgr.astype(np.float32) - sky_seed(bgr)).max(axis=2)
    cand = (d < d_max) & (det < det_max) & (~sure_bg)
    if not cand.any():
        return alpha
    num, lab = cv2.connectedComponents((cand | sure_bg).astype(np.uint8), connectivity=8)
    has_sky = np.zeros(num + 1, bool)
    has_sky[lab[sure_bg]] = True
    holes = cand & ~(has_sky[lab])
    alpha[holes] = 0.0
    return alpha


def matte(bgr):
    """返回 (BGRA, 天空掩码, 城市掩码)"""
    sky = sky_seed(bgr)
    sure_bg = flood_sky(bgr, sky)
    d = np.abs(bgr.astype(np.float32) - sky).max(axis=2)

    hard = (~sure_bg).astype(np.float32)
    # 兜底前景：与天空色差很大的连通块（且不贴画布四边）。先腐蚀再膨胀，
    # 免得这条"颜色够深就算前景"的规则把天空与城市之间那 1~2px 混色圈也标成前景
    solid = cv2.morphologyEx((d > FG_DIST).astype(np.uint8), cv2.MORPH_OPEN,
                             np.ones((3, 3), np.uint8))
    num, lab = cv2.connectedComponents(solid, connectivity=8)
    border = set(lab[0, :]) | set(lab[-1, :]) | set(lab[:, 0]) | set(lab[:, -1])
    border.discard(0)
    sure_fg = np.isin(lab, [i for i in range(1, num) if i not in border]) > 0

    alpha = cv2.GaussianBlur(hard, (5, 5), 1.2)
    # 只在轮廓带里按色差给半透明：城市内部（alpha 已经是 1）不受影响，
    # 所以球面那种"和天空同色的亮面"不会再被吃掉
    band = (alpha > 0.001) & (alpha < 0.999)
    t = np.clip((d - FEATHER_LO) / (FEATHER_HI - FEATHER_LO), 0.0, 1.0)
    alpha = np.where(band, np.minimum(alpha, t * t * (3 - 2 * t)), alpha)
    alpha = np.where(sure_bg, 0.0, alpha)
    alpha = np.where(sure_fg, 1.0, alpha)
    alpha = punch_enclosed_sky(bgr, alpha, sure_bg)

    # 反解前景色：贴边像素是"城市 × 天空"的混色，除掉天空分量边缘才不发灰
    aa = np.clip(alpha, 1e-3, 1.0)[:, :, None]
    fg = np.clip((bgr.astype(np.float32) - (1 - aa) * sky) / aa, 0, 255)
    fg = np.where(alpha[:, :, None] > 0.02, fg, bgr.astype(np.float32))
    return np.dstack([fg, alpha * 255.0]).astype(np.uint8), sure_bg, sure_fg


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--debug", default="", help="写一张透明区铺斜纹的检查图")
    args = ap.parse_args()

    bgr = cv2.imread(args.src, cv2.IMREAD_COLOR)
    if bgr is None:
        raise SystemExit(f"读不到 {args.src}")
    rgba, sure_bg, sure_fg = matte(bgr)
    a = rgba[:, :, 3].astype(np.float32) / 255.0
    ys, xs = np.where(a > 0.02)
    print(f"{rgba.shape[1]}×{rgba.shape[0]}  天空 {100*sure_bg.mean():.1f}%  "
          f"半透明 {100*((a>0.02)&(a<0.98)).mean():.1f}%  "
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
