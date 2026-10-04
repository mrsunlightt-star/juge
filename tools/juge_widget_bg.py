#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
句阁桌面小组件 —— 江西 3D 微缩城市背景素材处理
1) 探测并修补生成图右下角的水印
2) 按组件真实比例精确裁切（4x2 图片区 4.747:1 / 4x4 图片区 2.083:1）
3) 输出渲染器实际需要的像素尺寸，并生成桌面效果预览图
"""
import os
from PIL import Image, ImageDraw, ImageFilter, ImageFont

SRC_BAND = "/Users/sunqiqi/ans/generated-images/Ultra_wide_panoramic_3D_miniat_2026-10-02T04-17-39.png"
SRC_DIORAMA = "/Users/sunqiqi/ans/generated-images/Isometric_3D_miniature_clay_di_2026-10-02T04-17-56.png"
OUT = "/Users/sunqiqi/ans/widget-bg"
os.makedirs(OUT, exist_ok=True)

FONT_SERIF = "/System/Library/Fonts/Supplemental/Songti.ttc"
FONT_SANS = "/System/Library/Fonts/Hiragino Sans GB.ttc"

# 渲染器常量（对齐 WidgetCanvasRenderer.kt）
RENDER_DENSITY_SCALE = 1.5
SPLIT_CARD_RATIO = 0.48
CARD_INSET_DP = 4.0


def font(path, size, index=0):
    try:
        return ImageFont.truetype(path, size, index=index)
    except Exception:
        return ImageFont.load_default()


def detect_watermark(img, max_w_ratio=0.14, max_h_ratio=0.30, corner_gap=0.12):
    """
    在右下角区域检测近白像素，返回 bbox。

    必须做合理性校验：水印只可能「很小」且「紧贴右下角」。
    画面里任何亮色内容（草坡高光、浅色路面、建筑白墙、水面反光）都能凑出上千个亮点，
    不做尺寸与位置约束就会误判，进而在画面中间糊掉一整块矩形——比水印本身严重得多。
    """
    w, h = img.size
    rx0, ry0 = int(w * 0.72), int(h * 0.75)
    region = img.crop((rx0, ry0, w, h)).convert("RGB")
    px = region.load()
    rw, rh = region.size
    minx, miny, maxx, maxy = rw, rh, -1, -1
    count = 0
    for y in range(rh):
        for x in range(rw):
            r, g, b = px[x, y]
            if r > 205 and g > 205 and b > 205:
                count += 1
                if x < minx:
                    minx = x
                if y < miny:
                    miny = y
                if x > maxx:
                    maxx = x
                if y > maxy:
                    maxy = y
    if count < 40 or maxx < 0:
        return None
    box = (rx0 + minx, ry0 + miny, rx0 + maxx, ry0 + maxy)
    bw = box[2] - box[0] + 1
    bh = box[3] - box[1] + 1
    if bw > w * max_w_ratio or bh > h * max_h_ratio:
        print(f"  疑似误检：候选区域 {bw}×{bh} 过大（> {w * max_w_ratio:.0f}×{h * max_h_ratio:.0f}），判为画面亮色内容，跳过")
        return None
    if (w - box[2]) > w * corner_gap or (h - box[3]) > h * corner_gap:
        print(f"  疑似误检：候选区域未紧贴右下角 {box}，跳过")
        return None
    return box + (count,)


def mirror_fill(img, box, blur=9, feather=9):
    """对纯色/渐变背景区域：把紧邻上方的同宽区域垂直镜像后填入，再柔化"""
    x0, y0, x1, y1 = box
    bw, bh = x1 - x0, y1 - y0
    src_top = max(0, y0 - bh)
    src = img.crop((x0, src_top, x1, y0))
    if src.size != (bw, bh):
        src = src.resize((bw, bh), Image.LANCZOS)
    src = src.transpose(Image.FLIP_TOP_BOTTOM)
    if blur > 0:
        src = src.filter(ImageFilter.GaussianBlur(blur))
    mask = Image.new("L", (bw, bh), 0)
    ImageDraw.Draw(mask).rectangle([0, feather, bw, bh], fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(feather * 0.5))
    img.paste(src, (x0, y0), mask)
    return img


def _solve3(m, v):
    """克拉默法则解三元一次方程组"""
    def det(a):
        return (a[0][0] * (a[1][1] * a[2][2] - a[1][2] * a[2][1])
                - a[0][1] * (a[1][0] * a[2][2] - a[1][2] * a[2][0])
                + a[0][2] * (a[1][0] * a[2][1] - a[1][1] * a[2][0]))
    d = det(m)
    if abs(d) < 1e-9:
        return None
    out = []
    for i in range(3):
        mm = [row[:] for row in m]
        for r in range(3):
            mm[r][i] = v[r]
        out.append(det(mm) / d)
    return out


def plane_fill(img, box, ring=26, feather=8):
    """
    纯色/渐变背景上的修补：用 box 四周环形区域做最小二乘平面拟合 c=a+bx+cy，
    再用拟合出的平滑面填充 box。比克隆/镜像更贴合渐变底，不留矩形接缝。
    左/右/上三边羽化，下边保持不透明（通常贴近图像下缘，无需羽化）。
    """
    x0, y0, x1, y1 = box
    W, H = img.size
    x1, y1 = min(W, x1), min(H, y1)
    bw, bh = x1 - x0, y1 - y0

    n = Sx = Sy = Sxx = Syy = Sxy = 0.0
    sums = [0.0, 0.0, 0.0]
    sxv = [0.0, 0.0, 0.0]
    syv = [0.0, 0.0, 0.0]
    for y in range(max(0, y0 - ring), min(H, y1 + ring)):
        for x in range(max(0, x0 - ring), min(W, x1 + ring)):
            if x0 <= x < x1 and y0 <= y < y1:
                continue
            p = img.getpixel((x, y))
            n += 1
            Sx += x
            Sy += y
            Sxx += x * x
            Syy += y * y
            Sxy += x * y
            for c in range(3):
                sums[c] += p[c]
                sxv[c] += x * p[c]
                syv[c] += y * p[c]
    if n < 60:
        print("  环形样本不足，退回镜像填充")
        return mirror_fill(img, box, feather=feather)
    M = [[n, Sx, Sy], [Sx, Sxx, Sxy], [Sy, Sxy, Syy]]

    coefs = []
    for c in range(3):
        coef = _solve3(M, [sums[c], sxv[c], syv[c]])
        if coef is None:
            return mirror_fill(img, box, feather=feather)
        coefs.append(coef)

    data = []
    for yy in range(bh):
        gy = y0 + yy
        for xx in range(bw):
            gx = x0 + xx
            data.append(tuple(
                int(max(0, min(255, round(coefs[c][0] + coefs[c][1] * gx + coefs[c][2] * gy))))
                for c in range(3)
            ) + (255,))
    patch = Image.new("RGBA", (bw, bh))
    patch.putdata(data)

    # 羽化只作用于「不靠图像边缘」的那几边。
    # 靠边的方向不需要羽化（那里没有接缝），反而会把水印残留在羽化带里——水印常压在右下角，
    # 右/下两边往往就是图像边界，因此这两边必须保持完全不透明。
    mask = Image.new("L", (bw, bh), 255)
    md = ImageDraw.Draw(mask)
    if y0 > 0:
        md.rectangle([0, 0, bw, feather], fill=0)
    if x0 > 0:
        md.rectangle([0, 0, feather, bh], fill=0)
    if x1 < W:
        md.rectangle([bw - feather, 0, bw, bh], fill=0)
    if y1 < H:
        md.rectangle([0, bh - feather, bw, bh], fill=0)
    mask = mask.filter(ImageFilter.GaussianBlur(feather * 0.5))
    img.paste(patch, (x0, y0), mask)
    return img


def cleanup_watermark(img, label, box=None, mode="plane", skip=False):
    if skip:
        print(f"[{label}] 跳过水印处理（由裁切回避）")
        return img
    if box is None:
        info = detect_watermark(img)
        if not info:
            print(f"[{label}] 未检测到可信水印（未指定 box，跳过）")
            return img
        x0, y0, x1, y1, cnt = info
        pad = 8
        box = (max(0, x0 - pad), max(0, y0 - pad), min(img.size[0], x1 + pad), min(img.size[1], y1 + pad))
        print(f"[{label}] 检测到水印 bbox={box} 亮像素={cnt}")
    else:
        print(f"[{label}] 使用指定 bbox={box}")
    if mode == "mirror":
        mirror_fill(img, box)
    elif mode == "plane":
        plane_fill(img, box)
    return img


def center_crop_to_ratio(img, ratio):
    w, h = img.size
    if w / h > ratio:
        nw = int(round(h * ratio))
        left = (w - nw) // 2
        return img.crop((left, 0, left + nw, h))
    nh = int(round(w / ratio))
    top = (h - nh) // 2
    return img.crop((0, top, w, top + nh))


def save_assets(img, name, target_w, target_h, pre_crop=None, quiet=False):
    src = img.crop(pre_crop) if pre_crop else img
    exact = center_crop_to_ratio(src, target_w / target_h)
    scale = target_w / exact.size[0]
    one = exact.resize((target_w, target_h), Image.LANCZOS)
    two = exact.resize((target_w * 2, target_h * 2), Image.LANCZOS)
    p1 = f"{OUT}/{name}_{target_w}x{target_h}.png"
    p2 = f"{OUT}/{name}_{target_w * 2}x{target_h * 2}@2x.png"
    one.convert("RGB").save(p1, "PNG", optimize=True)
    two.convert("RGB").save(p2, "PNG", optimize=True)
    one.convert("RGB").save(f"{OUT}/{name}_{target_w}x{target_h}.webp", "WEBP", quality=92, method=6)
    if not quiet:
        tag = "放大" if scale > 1.02 else "缩小"
        print(f"  裁切自 {exact.size[0]}×{exact.size[1]} → {target_w}×{target_h}（{tag} {scale:.2f}x）")
        for p in (p1, p2):
            print(f"  写出 {os.path.basename(p)}  {os.path.getsize(p) // 1024} KB")
    return one


def cover(img, w, h):
    """CENTER_CROP 语义：等比放大到铺满 w×h 后居中裁切"""
    iw, ih = img.size
    s = max(w / iw, h / ih)
    r = img.resize((max(1, int(round(iw * s))), max(1, int(round(ih * s)))), Image.LANCZOS)
    rw, rh = r.size
    return r.crop(((rw - w) // 2, (rh - h) // 2, (rw - w) // 2 + w, (rh - h) // 2 + h))


def render_widget(bg_img, width_px, height_px, corner_r_px, lines, inset_px=0):
    """按 WidgetCanvasRenderer 的 SPLIT_CARD 结构绘制一个组件卡片"""
    card = Image.new("RGBA", (width_px, height_px), (0, 0, 0, 0))
    d = ImageDraw.Draw(card)
    img_h = int(round(height_px * SPLIT_CARD_RATIO))

    # 上半：背景图
    top = cover(bg_img, width_px, img_h)
    card.paste(top, (0, 0))
    # 下半：白色文字面板
    d.rectangle([0, img_h, width_px, height_px], fill=(255, 255, 255, 255))
    d.line([0, img_h, width_px, img_h], fill=(229, 231, 235, 255), width=max(1, width_px // 900))

    # 文字（padding 16dp，scale = width_px / 250dp）
    s = width_px / 250.0 * 1.0
    pad = 16 * s
    cur_y = img_h + 12 * s
    for text, sp, color, serif in lines:
        f = font(FONT_SERIF if serif else FONT_SANS, max(10, int(round(sp * s))))
        d.text((pad, cur_y), text, font=f, fill=color)
        cur_y += int(round(sp * s * 1.34))

    # 圆角遮罩
    mask = Image.new("L", (width_px, height_px), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, width_px - 1, height_px - 1], radius=corner_r_px, fill=255)
    card.putalpha(mask)
    return card


def paste_with_shadow(canvas, card, x, y, blur=16, offset=8, alpha=70):
    sh = Image.new("RGBA", (card.width + blur * 4, card.height + blur * 4), (0, 0, 0, 0))
    m = card.split()[3].point(lambda a: min(255, int(a * alpha / 255)))
    sh.paste(Image.new("RGBA", card.size, (20, 25, 35, 255)), (blur * 2, blur * 2), m)
    sh = sh.filter(ImageFilter.GaussianBlur(blur))
    canvas.alpha_composite(sh, (x - blur * 2, y - blur * 2 + offset))
    canvas.alpha_composite(card, (x, y))


def build_preview(band_img, diorama_img):
    W, H = 1220, 890
    cv = Image.new("RGBA", (W, H), (238, 240, 245, 255))
    d = ImageDraw.Draw(cv)
    top_c, bot_c = (206, 220, 236), (240, 233, 226)
    for y in range(H):
        t = y / (H - 1)
        d.line([(0, y), (W, y)], fill=tuple(int(top_c[i] + (bot_c[i] - top_c[i]) * t) for i in range(3)))
    d.ellipse([860, -140, 1300, 300], fill=(255, 255, 255, 55))
    d.ellipse([-110, 620, 300, 1030], fill=(255, 255, 255, 40))

    d.text((56, 24), "11:44", font=font(FONT_SANS, 30), fill=(255, 255, 255))

    tl = font(FONT_SANS, 19)
    d.text((56, 68), "4x2（250×110dp）放大 2.2 倍", font=tl, fill=(88, 98, 112))
    d.text((650, 68), "4x4（250×250dp）放大 2.2 倍", font=tl, fill=(88, 98, 112))

    w42 = render_widget(band_img, 550, 242, int(12 * 2.2), [
        ("明天 09:00 · 交房租", 19.0, (58, 77, 92, 255), True),
    ])
    paste_with_shadow(cv, w42, 56, 98)

    w44 = render_widget(diorama_img, 550, 550, int(12 * 2.2), [
        ("还有 3 天 · 交房租", 19.0, (58, 77, 92, 255), True),
        ("10月05日 到期", 13.0, (120, 132, 145, 255), False),
        ("—— 句阁提醒", 12.0, (150, 160, 170, 255), False),
    ])
    paste_with_shadow(cv, w44, 650, 98)

    d.text((56, 380), "1:1 实际大小（1080p 手机上实际看到的尺寸）", font=tl, fill=(88, 98, 112))
    w42s = render_widget(band_img, 250, 110, 12, [
        ("明天 09:00 · 交房租", 19.0, (58, 77, 92, 255), True),
    ])
    paste_with_shadow(cv, w42s, 56, 414, blur=8, offset=4, alpha=55)
    w44s = render_widget(diorama_img, 250, 250, 12, [
        ("还有 3 天 · 交房租", 19.0, (58, 77, 92, 255), True),
        ("10月05日 到期", 13.0, (120, 132, 145, 255), False),
    ])
    paste_with_shadow(cv, w44s, 350, 414, blur=8, offset=4, alpha=55)
    sl = font(FONT_SANS, 14)
    d.text((56, 536), "↑ 图片区 250×53dp（4.75:1）", font=sl, fill=(120, 128, 140))
    d.text((350, 676), "↑ 图片区 250×120dp（2.08:1）", font=sl, fill=(120, 128, 140))

    note = font(FONT_SANS, 15)
    notes = [
        "关键取舍：4x2 的图片区只有 250×53dp，",
        "精细的微缩楼群会糊成一片，所以 4x2 版",
        "刻意改用低视角天际线长条构图，只保留",
        "大色块与强轮廓；4x4 空间充足，可用完整底座。",
    ]
    yy = 672
    for line in notes:
        d.text((650, yy), line, font=note, fill=(104, 114, 128))
        yy += 25

    d.line([56, 800, 1164, 800], fill=(203, 208, 216, 255))
    d.text((56, 816), "句阁 · 江西 3D 微缩城市背景素材预览", font=font(FONT_SANS, 16), fill=(110, 118, 130))
    d.text((56, 842), "clay 写实微缩风格   |   图片区比例：4x2 = 4.75:1，4x4 = 2.08:1   |   渲染基准：1125×237 / 1125×540 px（3x 屏）",
           font=font(FONT_SANS, 14), fill=(132, 140, 150))

    out = f"{OUT}/jiangxi_widget_preview.png"
    cv.convert("RGB").save(out, "PNG", optimize=True)
    print(f"  写出 {os.path.basename(out)}  {os.path.getsize(out) // 1024} KB")


BAND_PRE_CROP = (30, 0, 1970, 432)

# 组件网格尺寸 → (宽dp, 高dp)，与 ReminderWidgetProvider.getWidgetSizeString() 的 (h+30)/70 口径一致
SIZE_SPECS = [("4x2", 250, 110), ("4x3", 250, 180), ("4x4", 250, 250)]


def image_area_px(width_dp, height_dp, density=3.0):
    """图片区在桌面上的实际像素（3x 屏）"""
    w = int(round(width_dp * density * RENDER_DENSITY_SCALE))
    h = int(round(height_dp * density * RENDER_DENSITY_SCALE * SPLIT_CARD_RATIO))
    return w, h


def build_size_matrix(band_src, dio_src):
    """
    尺寸适配矩阵：同一个素材在不同组件高度下会发生什么。
    关键规则来自 drawBgImage 的 CENTER_CROP 分支：
      素材比例 > 目标比例 → 保留全高、横向裁（构图完整，只是看中间一段）
      素材比例 < 目标比例 → 保留全宽、纵向裁（会把楼顶/底座切掉）
    """
    cell_w = 300
    W, H = 1180, 960
    cv = Image.new("RGBA", (W, H), (246, 247, 249, 255))
    d = ImageDraw.Draw(cv)
    cols = [56, 460, 864]
    band_top = 120
    band_h = 150

    f_title = font(FONT_SANS, 21)
    f_sub = font(FONT_SANS, 14)
    f_cap = font(FONT_SANS, 15)
    f_note = font(FONT_SANS, 15)

    sources = [
        ("A. 超宽天际线长条（原图 4.75:1，1125×237）", band_src, True),
        ("B. 完整微缩底座（原图 2.08:1，1125×540）", dio_src, False),
    ]

    for row, (title, src, is_band) in enumerate(sources):
        y0 = band_top + row * (band_h + 190)
        d.text((56, y0 - 46), title, font=f_title, fill=(44, 52, 64))
        for i, (name, wdp, hdp) in enumerate(SIZE_SPECS):
            ratio = (wdp * 1.0) / (hdp * SPLIT_CARD_RATIO)
            cw, ch = int(round(cell_w * 1.0)), int(round(cell_w / ratio))
            cell = center_crop_to_ratio(src, ratio).resize((cw, ch), Image.LANCZOS)
            x = cols[i]
            y = y0 + (band_h - ch) // 2
            # 描边让浅色画面在白底上看得清边界
            d.rectangle([x - 1, y - 1, x + cw, y + ch], outline=(190, 195, 203, 255), width=1)
            cv.paste(cell, (x, y))

            src_ratio = src.size[0] / src.size[1]
            # 8% 以内的裁切只吃天空/留白，视觉上无损，不要标红误导
            if abs(src_ratio - ratio) / max(src_ratio, ratio) <= 0.08:
                tip = f"基本无损（裁 {abs(1 - ratio / src_ratio) * 100:.0f}% 天空/留白）"
                color = (46, 106, 78)
            elif src_ratio > ratio:
                loss = 1 - ratio / src_ratio
                tip = f"横向裁掉 {loss * 100:.0f}%（主体不切）"
                color = (46, 106, 78) if loss < 0.5 else (150, 104, 20)
            else:
                loss = 1 - src_ratio / ratio
                tip = f"纵向切掉 {loss * 100:.0f}%（切主体）"
                color = (176, 58, 58)
            d.text((x, y0 + band_h + 8), f"{name}　图片区 {wdp}×{wdp * 0 - 0 + int(round(hdp * SPLIT_CARD_RATIO))}dp",
                   font=f_cap, fill=(52, 60, 72))
            d.text((x, y0 + band_h + 30), tip, font=f_sub, fill=color)

    ny = band_top + 2 * (band_h + 190) + 6
    notes = [
        "结论：组件是 resizeMode=horizontal|vertical，用户随时会把它拖高。这时两条素材的表现完全不同——",
        "· A 长条：任何高度都只横向裁，楼顶、街道、天空的垂直关系永远完整，拖到 4x4 相当于放大看中间那段街景；",
        "· B 微缩底座：拖到 4x2/4x3 会被纵向切掉 56%/28%，庐山山顶和悬浮底座直接没了——这正是 4x2 图片区只有 53dp 高时最怕的情况。",
        "建议：按入口默认尺寸配素材——「句阁 小卡」（默认 4x2）配 A 长条，「句阁 大卡」（默认 4x4）配 B 微缩底座，两个入口各自开局即最佳；",
        "若用户把入口拖成别的行数：A 只横向裁、主体不切，B 会纵向切主体。所以 B 只适合大卡，A 可以兜底任何尺寸。",
    ]
    for i, line in enumerate(notes):
        d.text((56, ny + i * 26), line, font=f_note, fill=(58, 66, 78))

    out = f"{OUT}/jiangxi_widget_size_matrix.png"
    cv.convert("RGB").save(out, "PNG", optimize=True)
    print(f"  写出 {os.path.basename(out)}  {os.path.getsize(out) // 1024} KB")


def main():
    print("== 处理 4x2 超宽长条 ==", os.path.basename(SRC_BAND))
    band = Image.open(SRC_BAND).convert("RGBA")
    print("  原图尺寸", band.size)
    # 长条图的水印压在右下角的有纹理草坡上，不适合做拟合回填：
    # 直接靠 BAND_PRE_CROP 裁掉水印所在的右侧那一列，画面本身不伪造内容
    band = cleanup_watermark(band, "band", box=None, skip=True)
    band_src = band.crop(BAND_PRE_CROP)   # 裁掉右下角水印所在列
    print(f"  去水印后可用画幅 {band_src.size[0]}×{band_src.size[1]}")
    band_1x = save_assets(band, "jiangxi_widget_bg_4x2", 1125, 237, pre_crop=BAND_PRE_CROP)
    save_assets(band, "jiangxi_widget_bg_4x3", 1125, 389, pre_crop=BAND_PRE_CROP)
    save_assets(band, "jiangxi_widget_alt_band_4x4", 1125, 540, pre_crop=BAND_PRE_CROP)

    print("== 处理 4x4 微缩底座 ==", os.path.basename(SRC_DIORAMA))
    dio = Image.open(SRC_DIORAMA).convert("RGBA")
    print("  原图尺寸", dio.size)
    dio = cleanup_watermark(dio, "diorama", box=(1425, 705, 1536, 768), mode="plane")
    dio_1x = save_assets(dio, "jiangxi_widget_bg_4x4", 1125, 540)

    print("== 生成预览图 ==")
    build_preview(band_1x, dio_1x)
    build_size_matrix(band_src, dio)
    print("完成 ->", OUT)


if __name__ == "__main__":
    main()
