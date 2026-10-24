# -*- coding: utf-8 -*-
"""生成《源程序鉴别材料》PDF。

规则（依据中国版权保护中心《计算机软件著作权登记指南》）：
  - 一般交存：源程序前、后各连续 30 页，共 60 页；不足 60 页则提交全部；
  - 程序每页不少于 50 行；
  - 页眉标注软件名称与版本号，右上角标注页码，正文从第 1 页编到第 60 页。

用法：python3 gen_source_pdf.py
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import reg_info  # noqa: E402
from reportlab.pdfgen import canvas as rl_canvas  # noqa: E402

# 源码根目录（相对本脚本：仓库根/app/src/main/java）
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(SCRIPT_DIR)
SRC_ROOT = os.path.join(REPO_ROOT, "app", "src", "main", "java")
OUT_PDF = os.path.join(SCRIPT_DIR, "源程序鉴别材料.pdf")

SUBMIT_PAGES = 60          # 提交总页数（前后各 30）
FIRST_PAGES = 30           # 前 30 页
LAST_PAGES = 30            # 后 30 页
CODE_FONT_SIZE = 7.5
CJK_FONT = "Songti"
LATIN_FONT = "Menlo"
# 行首缩进的空格按等宽字体宽度计算，折行宽度留 2pt 余量
MAX_W = reg_info.TEXT_W - 2.0


def collect_source_files():
    """按相对路径排序收集 Kotlin 源文件，返回 [(相对路径, 绝对路径)]。"""
    files = []
    for dirpath, dirnames, filenames in os.walk(SRC_ROOT):
        dirnames.sort()
        for fn in sorted(filenames):
            if fn.endswith(".kt"):
                full = os.path.join(dirpath, fn)
                files.append((os.path.relpath(full, REPO_ROOT), full))
    files.sort(key=lambda x: x[0])
    return files


def build_rendered_lines(files, wc):
    """读取全部源码并折行，返回渲染行列表 [(文本, 是否续行)]。"""
    rendered = []
    for _, path in files:
        with open(path, "r", encoding="utf-8") as f:
            content = f.read()
        if content.endswith("\n"):
            content = content[:-1]
        for raw in content.split("\n"):
            line = raw.rstrip()
            wrapped = reg_info.wrap_line(line, CJK_FONT, LATIN_FONT, wc, MAX_W)
            for i, seg in enumerate(wrapped):
                rendered.append((seg, i > 0))
    return rendered


def paginate(lines, per_page):
    return [lines[i:i + per_page] for i in range(0, len(lines), per_page)]


def select_submit_pages(lines):
    """按「行」切分前 30 页与后 30 页，保证提交的每一页都是整 50 行。

    若按「页」取整份排版稿的最后 30 页，排版稿末页的余数行会让提交材料最后一页不足 50 行；
    改为从正文两端按行各取 30×50 行，则 60 页每页都是整 50 行（前后各连续 30 页）。
    """
    block = FIRST_PAGES * reg_info.CODE_LINES_PER_PAGE
    if len(lines) <= SUBMIT_PAGES * reg_info.CODE_LINES_PER_PAGE:
        return paginate(lines, reg_info.CODE_LINES_PER_PAGE), False
    head = lines[:block]
    tail = lines[-block:]
    return paginate(head + tail, reg_info.CODE_LINES_PER_PAGE), True


def render(pages, fonts, wc):
    """把已选定的页渲染成 1..N 连续页码的 PDF。"""
    c = rl_canvas.Canvas(OUT_PDF, pagesize=(reg_info.PAGE_W, reg_info.PAGE_H))
    c.setTitle(f"{reg_info.软件全称} {reg_info.版本号} 源程序鉴别材料")
    c.setAuthor(reg_info.著作权人)
    total = len(pages)
    for out_no, page in enumerate(pages, start=1):
        reg_info.draw_header(c, out_no, total, fonts)
        y = reg_info.CODE_TOP_BASELINE
        for text, _ in page:
            if text.strip():
                reg_info.draw_mixed(c, reg_info.MARGIN_L, y, text, CJK_FONT, LATIN_FONT, wc)
            y -= reg_info.CODE_LEADING
        c.showPage()
    c.save()


def main():
    fonts = reg_info.register_fonts()
    wc = reg_info.WidthCache(CODE_FONT_SIZE)

    files = collect_source_files()
    raw_total = 0
    for _, path in files:
        with open(path, "r", encoding="utf-8") as f:
            body = f.read()
            if body.endswith("\n"):
                body = body[:-1]
            raw_total += len(body.split("\n")) if body else 0

    rendered = build_rendered_lines(files, wc)
    total_pages = (len(rendered) + reg_info.CODE_LINES_PER_PAGE - 1) // reg_info.CODE_LINES_PER_PAGE
    pages, split = select_submit_pages(rendered)
    render(pages, fonts, wc)

    line_counts = {len(p) for p in pages}
    print(f"源文件数（.kt）：{len(files)}")
    print(f"源程序总行数（按源文件计）：{raw_total}")
    print(f"排版后总行数（含折行）：{len(rendered)}")
    print(f"排版总页数：{total_pages} 页（{reg_info.CODE_LINES_PER_PAGE} 行/页）")
    print(f"本次提交：{len(pages)} 页"
          f"{'（前 30 页 + 后 30 页）' if split else '（全部）'}"
          f"，每页行数：{sorted(line_counts)}")
    print(f"输出：{OUT_PDF}")
    print(f"字数统计：源程序量 {raw_total} 行")


if __name__ == "__main__":
    main()
