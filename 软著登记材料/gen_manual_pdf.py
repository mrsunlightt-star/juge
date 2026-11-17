# -*- coding: utf-8 -*-
"""生成《文档鉴别材料（用户手册）》PDF。

规则（依据中国版权保护中心《计算机软件著作权登记指南》）：
  - 一般交存：任一种文档前、后各连续 30 页；整份文档不足 60 页的，提交整份文档；
  - 文档每页不少于 30 行；
  - 页眉标注软件名称与版本号，右上角标注页码，从目录开始由第 1 页起编。

用法：python3 gen_manual_pdf.py
"""

import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import reg_info  # noqa: E402
from reportlab.pdfgen import canvas as rl_canvas  # noqa: E402

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
MANUAL_MD = os.path.join(SCRIPT_DIR, "用户手册.md")
OUT_PDF = os.path.join(SCRIPT_DIR, "文档鉴别材料（用户手册）.pdf")

BODY_SIZE = 10.5
LINES_PER_PAGE = reg_info.DOC_LINES_PER_PAGE   # 40
LEADING = reg_info.DOC_LEADING                 # 16pt

CJK = "Songti"
CJK_B = "Songti-Bold"
LATIN = "Times-Roman"
LATIN_B = "Times-Bold"
MAX_W = reg_info.TEXT_W - 2.0


class Row:
    """一「行」版面上的内容：正文行、标题行或空行。"""

    __slots__ = ("text", "font", "latin", "size", "indent", "blank", "key", "toc_page")

    def __init__(self, text="", font=CJK, latin=LATIN, size=BODY_SIZE, indent=0.0,
                 blank=False, key=None, toc_page=None):
        self.text = text
        self.font = font
        self.latin = latin
        self.size = size
        self.indent = indent
        self.blank = blank
        self.key = key
        self.toc_page = toc_page


def parse_markdown(path):
    """把手册 Markdown 解析成块列表 [(kind, text, level)]。"""
    blocks = []
    with open(path, "r", encoding="utf-8") as f:
        lines = f.read().split("\n")
    for raw in lines:
        line = raw.rstrip()
        if not line.strip():
            continue
        if line.startswith("# "):
            blocks.append(("h1", line[2:].strip()))
        elif line.startswith("## "):
            blocks.append(("h2", line[3:].strip()))
        elif line.startswith("### "):
            blocks.append(("h3", line[4:].strip()))
        elif line.startswith("- "):
            blocks.append(("bullet", line[2:].strip()))
        else:
            blocks.append(("p", line.strip()))
    return blocks


def blocks_to_rows(blocks, wc_body):
    """块 → 版面行。标题前后留空行，列表项带悬挂缩进。"""
    rows = []
    for kind, text in blocks:
        if kind == "h1":
            continue  # 文档标题单独渲染
        if kind == "h2":
            rows.append(Row(blank=True))
            rows.append(Row(text, font=CJK_B, latin=LATIN_B, size=12.0, key=text))
            rows.append(Row(blank=True))
        elif kind == "h3":
            rows.append(Row(blank=True))
            rows.append(Row(text, font=CJK_B, latin=LATIN_B, size=10.5, indent=0.0, key=text))
        elif kind == "bullet":
            wrapped = reg_info.wrap_line(text, CJK, LATIN, wc_body, MAX_W - 21.0)
            for i, seg in enumerate(wrapped):
                rows.append(Row(("· " + seg) if i == 0 else seg,
                                indent=21.0 if i == 0 else 21.0))
        else:
            wrapped = reg_info.wrap_line(text, CJK, LATIN, wc_body, MAX_W)
            for seg in wrapped:
                rows.append(Row(seg))
    # 去掉连续空行与开头的空行
    cleaned = []
    for r in rows:
        if r.blank and (not cleaned or cleaned[-1].blank):
            continue
        cleaned.append(r)
    while cleaned and cleaned[-1].blank:
        cleaned.pop()
    return cleaned


def toc_rows(entries, wc_toc):
    """目录行：[级别, 标题, 页码]。"""
    rows = [Row(blank=True), Row("目录", font=CJK_B, latin=LATIN_B, size=12.0), Row(blank=True)]
    for level, title, page in entries:
        indent = 0.0 if level == 2 else 21.0
        left = title
        page_s = str(page) if page else ""
        rows.append(Row(left, indent=indent, toc_page=page_s))
    return rows


def draw_toc_row(c, y, row, fonts, wc):
    """目录行：左侧标题 + 点线 + 右侧页码。"""
    x = reg_info.MARGIN_L + row.indent
    end = reg_info.draw_mixed(c, x, y, row.text, CJK, LATIN, wc)
    if row.toc_page:
        from reportlab.pdfbase import pdfmetrics
        pw = pdfmetrics.stringWidth(row.toc_page, CJK, BODY_SIZE)
        right = reg_info.PAGE_W - reg_info.MARGIN_R
        dot_w = pdfmetrics.stringWidth("·", CJK, BODY_SIZE)
        gap = right - pw - end - dot_w
        if gap > 0:
            n = int(gap // dot_w)
            c.setFont(CJK, BODY_SIZE)
            c.drawString(end + dot_w, y, "·" * n)
        c.setFont(CJK, BODY_SIZE)
        c.drawString(right - pw, y, row.toc_page)


def paginate_linear(rows, per_page):
    """按行分页；标题行不落在页末（与后续内容保持同页）。"""
    pages = []
    cur = []
    for i, row in enumerate(rows):
        cur.append(row)
        if len(cur) == per_page:
            # 若本页最后一行是标题，则把它挪到下一页
            if row.key is not None and i + 1 < len(rows):
                cur.pop()
                pages.append(cur)
                cur = [row]
            else:
                pages.append(cur)
                cur = []
    if cur:
        pages.append(cur)
    return pages


def paginate(rows, per_page, min_text_lines=30):
    """在每页不少于 ``min_text_lines`` 行正文的前提下尽量少分页。

    删除或缩短短落时，简单按固定行数分页会把余量全部挤到末页，导致末页不足 30 行。
    这里用后缀动态规划重新分配页边界；无可行解时退回原来的线性分页。
    """
    total = len(rows)
    text_prefix = [0] * (total + 1)
    for i, row in enumerate(rows):
        text_prefix[i + 1] = text_prefix[i] + (0 if row.blank or not row.text else 1)

    best_pages = [None] * (total + 1)
    best_end = [-1] * (total + 1)
    best_pages[total] = 0
    for start in range(total - 1, -1, -1):
        for end in range(min(total, start + per_page), start, -1):
            text_lines = text_prefix[end] - text_prefix[start]
            if text_lines < min_text_lines:
                continue
            if end < total and rows[end - 1].key is not None:
                continue
            if best_pages[end] is None:
                continue
            candidate = 1 + best_pages[end]
            if (best_pages[start] is None
                    or candidate < best_pages[start]
                    or (candidate == best_pages[start] and end > best_end[start])):
                best_pages[start] = candidate
                best_end[start] = end

    if best_pages[0] is None:
        return paginate_linear(rows, per_page)

    pages = []
    start = 0
    while start < total:
        end = best_end[start]
        pages.append(rows[start:end])
        start = end
    return pages


def render(pages, title_text, fonts, wc):
    c = rl_canvas.Canvas(OUT_PDF, pagesize=(reg_info.PAGE_W, reg_info.PAGE_H))
    c.setTitle(f"{reg_info.软件全称} {reg_info.版本号} 用户手册")
    c.setAuthor(reg_info.著作权人)
    total = len(pages)
    for idx, rows in enumerate(pages, start=1):
        reg_info.draw_header(c, idx, total, fonts)
        y = reg_info.DOC_TOP_BASELINE
        for row in rows:
            if not row.blank and row.text:
                if row.toc_page is not None:
                    draw_toc_row(c, y, row, fonts, wc)
                else:
                    reg_info.draw_mixed(c, reg_info.MARGIN_L + row.indent, y,
                                        row.text, row.font, row.latin,
                                        reg_info.WidthCache(row.size))
            y -= LEADING
        c.showPage()
    c.save()
    return total


def main():
    fonts = reg_info.register_fonts()
    wc_body = reg_info.WidthCache(BODY_SIZE)

    blocks = parse_markdown(MANUAL_MD)
    title_text = next((t for k, t in blocks if k == "h1"), "用户手册")
    body_rows = blocks_to_rows(blocks, wc_body)

    headings = [(2 if b[0] == "h2" else 3, b[1]) for b in blocks if b[0] in ("h2", "h3")]

    # 两遍排版：反复用上一轮算出的页码重建目录，直到页码稳定
    entries = [(lv, t, None) for lv, t in headings]
    pages = None
    for _ in range(8):
        toc = toc_rows(entries, None)
        # 第 1 页顶部：文档标题 + 空行，再接目录
        first_page_rows = [Row(title_text, font=CJK_B, latin=LATIN_B, size=15.0), Row(blank=True)]
        all_rows = first_page_rows + toc + body_rows
        pages = paginate(all_rows, LINES_PER_PAGE)

        page_of_key = {}
        for pi, rows in enumerate(pages, start=1):
            for row in rows:
                if row.key and row.key not in page_of_key:
                    page_of_key[row.key] = pi

        new_entries = [(lv, t, page_of_key.get(t)) for lv, t in headings]
        if new_entries == entries:
            break
        entries = new_entries
    else:
        print("警告：目录页码未收敛，请检查内容长度")

    total = render(pages, title_text, fonts, wc_body)

    # 统计：校验每页不少于 30 行
    stats = []
    for i, rows in enumerate(pages, start=1):
        text_lines = sum(1 for r in rows if not r.blank and r.text)
        stats.append((i, len(rows), text_lines))
    min_lines = min(s[2] for s in stats)
    short = [s for s in stats if s[2] < 30]

    print(f"文档标题：{title_text}")
    print(f"目录条目：{len(entries)} 条（含二级、三级标题）")
    print(f"总页数：{total} 页（最多 {LINES_PER_PAGE} 行/页）")
    print(f"每页正文行数：最少 {min_lines} 行"
          + (f"，不足 30 行的页：{[s[0] for s in short]}" if short else "，全部满足「每页不少于 30 行」"))
    print(f"提交方式：{'整份提交' if total <= 60 else '前 30 页 + 后 30 页'}")
    print(f"输出：{OUT_PDF}")


if __name__ == "__main__":
    main()
