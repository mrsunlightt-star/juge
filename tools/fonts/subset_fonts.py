#!/usr/bin/env python3
"""字体子集化：把 assets/fonts 下的字体裁剪到常用字集，显著减小 APK 体积。

背景：7 款字体原始合计约 45MB，占 release APK 的绝大部分。体积几乎全在字形轮廓上，
去 hinting 对毛笔类字体几乎无效，唯一有效的手段是按字符集裁剪。

字符覆盖 = 《通用规范汉字表》8105 字（charset_common.txt）
         + ASCII 可打印
         + 中文标点 / 全角字符 / 常用符号

裁剪只删除「目标字符集之外」的冷僻字形：目标集内的字符，字体原本能渲染的仍然能渲染，
因此常用字不会因裁剪而缺字。目标集之外的字符会显示为方块。

用法：
    python3 tools/fonts/subset_fonts.py          # 就地替换字体
    python3 tools/fonts/subset_fonts.py --check  # 只测量并报告，不写文件

依赖：fonttools（pyftsubset）。原字体已在 git 中，可随时用 git checkout 还原。
"""

import argparse
import os
import subprocess
import sys
import tempfile

from fontTools.ttLib import TTFont

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
FONT_DIR = os.path.join(REPO_ROOT, "app", "src", "main", "assets", "fonts")
CHARSET_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "charset_common.txt")

# 这些表是旧式 Windows 设备度量/签名表，Skia 渲染用不到，去掉可再省一点体积
DROP_TABLES = "gasp,DSIG,FFTM,mort,hdmx,LTSH,VDMX,PCLT"

# ASCII 可打印 + 中文标点 + 全角 + 常用标点/字母符号
SYMBOL_RANGES = [
    (0x0020, 0x007E),
    (0x2000, 0x206F),
    (0x2100, 0x214F),
    (0x2190, 0x21FF),
    (0x2460, 0x24FF),
    (0x3000, 0x303F),
    (0xFF00, 0xFFEF),
]


def build_charset() -> str:
    chars = set()
    for start, end in SYMBOL_RANGES:
        chars.update(chr(cp) for cp in range(start, end + 1))
    with open(CHARSET_FILE, encoding="utf-8") as f:
        chars.update(f.read().strip())
    return "".join(sorted(chars))


def font_coverage(path: str, charset: str) -> tuple[int, int]:
    font = TTFont(path, lazy=True)
    cmap = font.getBestCmap()
    covered = sum(1 for ch in charset if ord(ch) in cmap)
    total = len(cmap)
    font.close()
    return covered, total


def subset(src: str, dst: str, text_file: str) -> None:
    cmd = [
        sys.executable, "-m", "fontTools.subset", src,
        f"--text-file={text_file}",
        f"--output-file={dst}",
        f"--drop-tables+={DROP_TABLES}",
        "--no-hinting",
        # 保留全部 name 记录（含各语言）：OFL 要求保留版权与授权声明，
        # 若只保留英文记录会丢掉日文/中文的版权、商标与授权说明
        "--name-IDs=*",
        "--name-languages=*",
        "--name-legacy",
        "--recalc-bounds",
    ]
    subprocess.run(cmd, check=True, capture_output=True)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="只测量，不写文件")
    parser.add_argument("--dir", default=FONT_DIR, help="字体目录（默认 app/src/main/assets/fonts）")
    args = parser.parse_args()

    font_dir = os.path.abspath(args.dir)
    charset = build_charset()
    print(f"目标字符集：{len(charset)} 个字符（通用规范汉字表 8105 + 符号）\n")

    with tempfile.NamedTemporaryFile("w", encoding="utf-8", suffix=".txt", delete=False) as tf:
        tf.write(charset)
        text_file = tf.name

    fonts = sorted(f for f in os.listdir(font_dir) if f.endswith((".ttf", ".otf")))
    if not fonts:
        print(f"未找到字体：{font_dir}", file=sys.stderr)
        return 1
    print(f"字体目录：{font_dir}\n")

    total_before = total_after = 0
    try:
        for name in fonts:
            src = os.path.join(font_dir, name)
            before = os.path.getsize(src)
            covered_before, glyphs_before = font_coverage(src, charset)

            # 输出扩展名与源一致：OTF(CFF) 不能被写成 .ttf，否则 Android/Skia 解析异常
            ext = os.path.splitext(name)[1].lower()
            with tempfile.NamedTemporaryFile(suffix=ext, delete=False) as of:
                out = of.name
            try:
                subset(src, out, text_file)
                after = os.path.getsize(out)
                covered_after, glyphs_after = font_coverage(out, charset)
                if covered_after < covered_before:
                    raise RuntimeError(
                        f"{name}: 子集化后覆盖字符从 {covered_before} 降到 {covered_after}，"
                        "疑似误删了目标集内的字形"
                    )
                if not args.check:
                    os.replace(out, src)
                else:
                    os.unlink(out)
            except Exception:
                if os.path.exists(out):
                    os.unlink(out)
                raise

            total_before += before
            total_after += after
            print(
                f"{name:<34} {before/1048576:6.1f}M -> {after/1048576:6.1f}M "
                f"({after/before*100:3.0f}%) | 字形 {glyphs_before}->{glyphs_after}, "
                f"覆盖 {covered_after}/{len(charset)}"
            )
    finally:
        os.unlink(text_file)

    saved = (total_before - total_after) / 1048576
    print(
        f"\n{'合计':<34} {total_before/1048576:6.1f}M -> {total_after/1048576:6.1f}M "
        f"（省 {saved:.1f}M，{(1-total_after/total_before)*100:.0f}%）"
    )
    if args.check:
        print("（--check：未写入文件）")
    return 0


if __name__ == "__main__":
    sys.exit(main())