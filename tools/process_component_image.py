#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
process_component_image.py — 把一张"组件效果图"处理成 App 可用的背景图。

用途：给桌面小组件(App 句阁)新增"图片背景 + 文字安全区"风格前，用本脚本把
效果图处理成干净的组件主体图（去背景 / 去水印 / 裁边距 / 检查透明）。

用法（子命令）：
  python3 tools/process_component_image.py info   <input>            # 检查尺寸/alpha/四角
  python3 tools/process_component_image.py crop   <input> <output>   # 裁掉四周纯白边距,让主体填满
  python3 tools/process_component_image.py flood  <input> <output>   # 从四边flood-fill,把连通近纯白置透明
  python3 tools/process_component_image.py inpaint <input> <output> <x0> <y0> <x1> <y1> [--bg R G B]
                                                                     # 把指定矩形里的深色文字/水印填充成周围主体色
  python3 tools/process_component_image.py clone  <input> <output> <x0> <y0> <x1> <y1> [--from bottom|top|left|right] [--offset 5]
                                                                     # 用干净背景带覆盖水印矩形(有纹理背景用这个,比inpaint好)
每个子命令都尽量做到"只改该做的事"，不破坏主体。

典型流程（把效果图变成组件主体图）：
  # 1) 先检查是不是真·透明抠图,四角alpha是否透明
  python3 tools/process_component_image.py info 效果图.png
  # 2) 去掉四周纯白边距,让主体填满
  python3 tools/process_component_image.py crop 效果图.png 主体.png --pad 4
  # 3) 若不规则形状,把主体外背景置透明(保留撕纸/异形边缘)
  python3 tools/process_component_image.py flood 主体.png 组件主体.png --tol 8
  # 4) 去水印文字: 分两种
  #    a) 纯色/浅色背景: inpaint(均色填充)
  #    b) 带纹理/花纹背景(像素十字、纸纹等): clone(从正下方取干净背景覆盖), 效果更好
  python3 tools/process_component_image.py clone 组件主体.png 干净.png 535 320 1183 477 --from bottom --offset 5
  # 5) 用合法资源名保存到 res/drawable/ 并删掉原中文名文件

注意：Android 资源名只允许 小写a-z/数字/下划线。处理完请用合法名保存(如 letter_feather.png)，
并且删除原中文名文件(否则编译报错)。
"""

import argparse
import sys
from collections import deque, Counter
from PIL import Image


def load_rgba(path):
    img = Image.open(path).convert("RGBA")
    return img


# ---------- info ----------
def cmd_info(args):
    img = load_rgba(args.input)
    w, h = img.size
    px = img.load()
    corners = [px[0, 0], px[w - 1, 0], px[0, h - 1], px[w - 1, h - 1]]
    print(f"尺寸: {w}x{h}  模式: {img.mode}")
    print("四角 RGBA: 左上%s 右上%s 左下%s 右下%s" % tuple(corners))
    corner_alpha = [c[3] for c in corners]
    if all(a > 200 for a in corner_alpha):
        print("== 警告: 四角不透明(alpha>200)。这不是透明抠图, 四周可能带了背景色。"
              " 透出壁纸的效果做不了, 需先 flood 去背景。")
    elif all(a < 50 for a in corner_alpha):
        print("== 四角透明, 这是一张真·透明抠图。可直接用作背景(透明区透出桌面壁纸)。")
    # alpha 分布
    c = Counter()
    for y in range(0, h, max(1, h // 200)):
        for x in range(0, w, max(1, w // 200)):
            c[px[x, y][3] > 50] += 1
    total = sum(c.values())
    print(f"非透明区域约占 {100 * c[True] / total:.1f}%")
    return 0


# ---------- crop ----------
def cmd_crop(args):
    img = load_rgba(args.input)
    w, h = img.size
    px = img.load()
    # 检测"非纯白"包围盒(纯白=255,255,255,主体因纹理/阴影/装饰≠纯白)
    minx, miny, maxx, maxy = w, h, 0, 0
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a > 200 and (r < 250 or g < 250 or b < 250):
                minx = min(minx, x); maxx = max(maxx, x)
                miny = min(miny, y); maxy = max(maxy, y)
    if maxx <= minx or maxy <= miny:
        print("未检测到主体(全是纯白)。请确认图片内容是主体+白边。")
        return 1
    pad = args.pad
    x0 = max(0, minx - pad); y0 = max(0, miny - pad)
    x1 = min(w, maxx + pad); y1 = min(h, maxy + pad)
    crop = img.crop((x0, y0, x1, y1))
    crop.save(args.output)
    print(f"裁剪 {x0},{y0} ~ {x1},{y1} -> {crop.size} 已保存 {args.output}")
    print("提示: 如果你要“组件即主体”, 裁完主体就填满整图。但主体若是不规则形状,边角仍是背景,可再 flood。")
    return 0


# ---------- flood (去背景) ----------
def cmd_flood(args):
    img = load_rgba(args.input)
    w, h = img.size
    px = img.load()
    tol = args.tol
    br, bgr, bb = args.bg  # 背景色 R,G,B (flood 用于判定)
    visited = [[False] * w for _ in range(h)]
    q = deque()

    def is_bg(x, y):
        r, g, b, a = px[x, y]
        return a > 100 and abs(r - br) <= tol and abs(g - bgr) <= tol and abs(b - bb) <= tol

    for y in range(h):
        for x in (0, w - 1):
            if is_bg(x, y) and not visited[y][x]:
                visited[y][x] = True; q.append((x, y))
    for x in range(w):
        for y in (0, h - 1):
            if is_bg(x, y) and not visited[y][x]:
                visited[y][x] = True; q.append((x, y))
    cnt = 0
    while q:
        x, y = q.popleft()
        px[x, y] = (255, 255, 255, 0)  # 置透明
        cnt += 1
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < w and 0 <= ny < h and not visited[ny][nx] and is_bg(nx, ny):
                visited[ny][nx] = True; q.append((nx, ny))
    img.save(args.output)
    print(f"flood-fill 置透明像素数: {cnt}。已保存 {args.output}")
    print("提示: 把主体外的背景置透明后, 组件的不规则边缘就能透出桌面壁纸。")
    return 0


# ---------- inpaint 去水印 ----------
def cmd_inpaint(args):
    img = load_rgba(args.input)
    px = img.load()
    w, h = img.size
    x0, y0, x1, y1 = args.x0, args.y0, args.x1, args.y1
    bg = args.bg
    if bg is None:
        # 采样矩形内浅色像素均值, 作为主体色
        import statistics
        samples = []
        for y in range(y0, y1):
            for x in range(x0, x1):
                r, g, b, a = px[x, y]
                if a > 150 and r > 200 and g > 195 and b > 175:
                    samples.append((r, g, b))
        if samples:
            bg = (int(statistics.mean(s[0] for s in samples)),
                  int(statistics.mean(s[1] for s in samples)),
                  int(statistics.mean(s[2] for s in samples)))
        else:
            bg = (240, 234, 215)
    print(f"主体填充色: #{bg[0]:02X}{bg[1]:02X}{bg[2]:02X}")
    filled = 0
    for y in range(y0, y1):
        for x in range(x0, x1):
            r, g, b, a = px[x, y]
            # 判定偏离主体色 => 水印/杂质文字
            diff = abs(r - bg[0]) + abs(g - bg[1]) + abs(b - bg[2])
            if a > 30 and diff > args.thresh:
                px[x, y] = (bg[0], bg[1], bg[2], 255)
                filled += 1
    img.save(args.output)
    print(f"填充水印/杂质像素: {filled}。已保存 {args.output}")
    print("提示: 若残留抗锯齿淡影(阈值太紧), 可降低 --thresh 再跑一次。")
    return 0


# ---------- clone 克隆背景去水印 ----------
def cmd_clone(args):
    """
    把指定矩形(水印/杂质)用"干净背景带"填满。
    适合有纹理/花纹背景(如像素十字花纹): 从水印下方/旁边取一条干净背景,
    平移覆盖水印矩形, 花纹连贯、颜色准确。比 inpaint 均色填充效果好。
    """
    img = load_rgba(args.input)
    px = img.load()
    w, h = img.size
    x0, y0, x1, y1 = args.x0, args.y0, args.x1, args.y1
    # 源背景带的起始位置: 默认从水印矩形正下方取, 方向可选
    src_x0, src_y0, src_x1, src_y1 = x0, y0, x1, y1
    if args.from_dir == "bottom":
        src_y0 = y1 + args.offset
        src_y1 = src_y0 + (y1 - y0)  # 和矩形同高
    elif args.from_dir == "top":
        src_y0 = y0 - args.offset - (y1 - y0)
        src_y1 = y0 - args.offset
    elif args.from_dir == "right":
        src_x0 = x1 + args.offset
        src_x1 = src_x0 + (x1 - x0)
    elif args.from_dir == "left":
        src_x0 = x0 - args.offset - (x1 - x0)
        src_x1 = x0 - args.offset
    # 边界保护
    src_y0 = max(0, src_y0); src_y1 = min(h, src_y1)
    src_x0 = max(0, src_x0); src_x1 = min(w, src_x1)
    if src_y1 <= src_y0 or src_x1 <= src_x0:
        print("源背景带超出图片范围。请调小 --offset 或换方向。")
        return 1
    # 逐行/逐列平移覆盖目标矩形
    tw = x1 - x0
    th = y1 - y0
    sw = src_x1 - src_x0
    sh = src_y1 - src_y0
    for yy in range(th):
        sy = src_y0 + (yy % sh)  # 花纹对齐: 按行循环取源
        for xx in range(tw):
            sx = src_x0 + (xx % sw)
            px[x0 + xx, y0 + yy] = px[sx, sy]
    img.save(args.output)
    print(f"克隆背景覆盖矩形 x[{x0}-{x1}] y[{y0}-{y1}] (源从 {args.from_dir} 偏移 {args.offset})。已保存 {args.output}")
    return 0


def main():
    p = argparse.ArgumentParser(prog="process_component_image", description=__doc__,
                                formatter_class=argparse.RawTextHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)

    sp = sub.add_parser("info", help="检查尺寸/alpha/四角")
    sp.add_argument("input"); sp.set_defaults(func=cmd_info)

    sp = sub.add_parser("crop", help="裁掉四周纯白边距, 让主体填满")
    sp.add_argument("input"); sp.add_argument("output")
    sp.add_argument("--pad", type=int, default=4, help="裁剪时保留的边距像素(默认4)")
    sp.set_defaults(func=cmd_crop)

    sp = sub.add_parser("flood", help="从四边flood-fill,把连通的近纯白置透明")
    sp.add_argument("input"); sp.add_argument("output")
    sp.add_argument("--tol", type=int, default=8, help="纯白容差(默认8)")
    sp.add_argument("--bg", nargs=3, type=int, default=[255, 255, 255], metavar=("R", "G", "B"),
                    help="要消除的背景色(默认255,255,255)")
    sp.set_defaults(func=cmd_flood)

    sp = sub.add_parser("inpaint", help="把矩形里偏离主体色的文字/水印填成主体色")
    sp.add_argument("input"); sp.add_argument("output")
    sp.add_argument("x0", type=int); sp.add_argument("y0", type=int)
    sp.add_argument("x1", type=int); sp.add_argument("y1", type=int)
    sp.add_argument("--bg", nargs=3, type=int, metavar=("R", "G", "B"), help="主体填充色(缺省=自动采样)")
    sp.add_argument("--thresh", type=int, default=40, help="判定为杂质的色差阈值(默认40)")
    sp.set_defaults(func=cmd_inpaint)

    sp = sub.add_parser("clone", help="用干净背景带覆盖水印矩形(适合有纹理背景, 比inpaint好)")
    sp.add_argument("input"); sp.add_argument("output")
    sp.add_argument("x0", type=int); sp.add_argument("y0", type=int)
    sp.add_argument("x1", type=int); sp.add_argument("y1", type=int)
    sp.add_argument("--from", dest="from_dir", default="bottom", choices=["bottom", "top", "left", "right"],
                    help="从哪个方向取干净背景带(默认bottom=水印正下方)")
    sp.add_argument("--offset", type=int, default=5, help="源背景带与矩形之间的间隔像素(默认5)")
    sp.set_defaults(func=cmd_clone)

    args = p.parse_args()
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
