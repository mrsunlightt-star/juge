# -*- coding: utf-8 -*-
"""计算机软件著作权登记材料 —— 共用信息与 PDF 排版工具。

改这里的常量即可同时影响「源程序鉴别材料」与「文档鉴别材料（用户手册）」
两份 PDF 的页眉、标题，保证与《申请表》里填写的软件全称、版本号一致。
"""

import os

# ===== 登记信息（须与申请表填报内容完全一致）=====
软件全称 = "句阁桌面金句提醒软件"
软件简称 = "句阁"
版本号 = "V1.0"
著作权人 = "于都县虞都之南科技工作室"

页眉文字 = f"{软件全称} {版本号}"

# ===== 字体 =====
SONGTI_REGULAR = ("/System/Library/Fonts/Supplemental/Songti.ttc", 6)  # Songti SC Regular
SONGTI_BOLD = ("/System/Library/Fonts/Supplemental/Songti.ttc", 1)     # Songti SC Bold
MENLO_REGULAR = ("/System/Library/Fonts/Menlo.ttc", 0)                 # Menlo Regular（等宽，供源码用）

# ===== A4 版面 =====
PAGE_W, PAGE_H = 595.28, 841.89        # A4，单位 pt
MARGIN_L = 56.0                        # ≈ 2.0cm
MARGIN_R = 56.0
TEXT_W = PAGE_W - MARGIN_L - MARGIN_R  # 483.28pt
HEADER_BASELINE = PAGE_H - 40.0

# 源程序版面：50 行/页
CODE_TOP_BASELINE = PAGE_H - 70.0
CODE_LEADING = 14.2
CODE_LINES_PER_PAGE = 50

# 手册版面：40 行/页（规范要求「文档每页不少于 30 行」）
DOC_TOP_BASELINE = PAGE_H - 70.0
DOC_LEADING = 16.0
DOC_LINES_PER_PAGE = 40


def register_fonts():
    """注册用到的字体，返回字体名映射。"""
    from reportlab.pdfbase import pdfmetrics
    from reportlab.pdfbase.ttfonts import TTFont

    pdfmetrics.registerFont(TTFont("Songti", SONGTI_REGULAR[0], subfontIndex=SONGTI_REGULAR[1]))
    pdfmetrics.registerFont(TTFont("Songti-Bold", SONGTI_BOLD[0], subfontIndex=SONGTI_BOLD[1]))
    pdfmetrics.registerFont(TTFont("Menlo", MENLO_REGULAR[0], subfontIndex=MENLO_REGULAR[1]))
    return {"cn": "Songti", "cn_bold": "Songti-Bold", "mono": "Menlo", "latin": "Times-Roman",
            "latin_bold": "Times-Bold"}


def is_cjk(ch):
    """判断字符是否按中日韩全角字体（宋体）排版。

    覆盖：CJK 统一表意文字、扩展 A、中文标点、全角符号、假名等。
    """
    o = ord(ch)
    return (
        0x2E80 <= o <= 0x303F      # CJK 部首、中文标点
        or 0x3040 <= o <= 0x30FF   # 假名
        or 0x3400 <= o <= 0x4DBF   # 扩展 A
        or 0x4E00 <= o <= 0x9FFF   # 基本区
        or 0xF900 <= o <= 0xFAFF   # 兼容表意文字
        or 0xFF00 <= o <= 0xFF60   # 全角符号
        or 0xFFE0 <= o <= 0xFFE6   # 全角符号补充
        or o in (0x2018, 0x2019, 0x201C, 0x201D, 0x2026, 0x2014)
    )


def split_runs(text, cjk_font, latin_font):
    """把一行文本切成 [(字体名, 片段)] —— 中文走中文字体，拉丁走西文字体。"""
    runs = []
    for ch in text:
        font = cjk_font if is_cjk(ch) else latin_font
        if runs and runs[-1][0] == font:
            runs[-1][1] += ch
        else:
            runs.append([font, ch])
    return runs


class WidthCache:
    """按 (字符, 字体, 字号) 缓存字宽，避免逐字符调用 stringWidth 的开销。"""

    def __init__(self, size):
        from reportlab.pdfbase import pdfmetrics
        self._sw = pdfmetrics.stringWidth
        self._size = size
        self._cache = {}

    def char(self, ch, font):
        key = (ch, font)
        w = self._cache.get(key)
        if w is None:
            w = self._sw(ch, font, self._size)
            self._cache[key] = w
        return w


def wrap_line(text, cjk_font, latin_font, wc, max_width):
    """按实际字宽贪心折行。超宽时优先回退到最近的空格/逗号处断开，保持代码可读。"""
    if not text.strip():
        return [text]

    out = []
    cur = []
    cur_w = 0.0
    for ch in text:
        font = cjk_font if is_cjk(ch) else latin_font
        w = wc.char(ch, font)
        if cur_w + w > max_width and cur:
            # 回退断点：最近的空格（最多回看 12 个字符）
            cut = -1
            for i in range(len(cur) - 1, max(0, len(cur) - 12) - 1, -1):
                if cur[i] == " ":
                    cut = i
                    break
            if cut > 0:
                out.append("".join(cur[:cut]))
                cur = cur[cut:]
            else:
                out.append("".join(cur))
                cur = []
            cur_w = 0.0
            for c in cur:
                f = cjk_font if is_cjk(c) else latin_font
                cur_w += wc.char(c, f)
        cur.append(ch)
        cur_w += w
    out.append("".join(cur))
    return out


def draw_mixed(c, x, y, text, cjk_font, latin_font, wc):
    """在 (x, y) 起按字体分段绘制一行文本，返回结束 x 坐标。"""
    from reportlab.pdfbase import pdfmetrics
    for font, seg in split_runs(text, cjk_font, latin_font):
        c.setFont(font, wc._size)
        c.drawString(x, y, seg)
        x += pdfmetrics.stringWidth(seg, font, wc._size)
    return x


def draw_header(c, page_no, total_pages, fonts):
    """页眉：左侧软件名称 + 版本号，右上角页码（含分隔线）。"""
    c.setFont(fonts["cn_bold"], 9)
    c.drawString(MARGIN_L, HEADER_BASELINE, 页眉文字)
    c.setFont(fonts["cn"], 9)
    c.drawRightString(PAGE_W - MARGIN_R, HEADER_BASELINE, f"第 {page_no} 页 / 共 {total_pages} 页")
    c.setLineWidth(0.5)
    c.setStrokeColorRGB(0.6, 0.6, 0.6)
    c.line(MARGIN_L, HEADER_BASELINE - 8, PAGE_W - MARGIN_R, HEADER_BASELINE - 8)


def output_dir():
    return os.path.dirname(os.path.abspath(__file__))
