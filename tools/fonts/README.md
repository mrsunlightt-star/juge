# 字体子集化

把 `app/src/main/assets/fonts/` 下的字体裁剪到常用字集，减小 APK 体积。

## 为什么需要

8 款内置字体原始合计约 41MB，体积几乎全部来自字形轮廓：`--no-hinting`
对毛笔类字体几乎无效（MaShanZheng 仅 -1.2%），因此**按字符集裁剪是唯一有效的杠杆**。

## 用法

```bash
python3 tools/fonts/subset_fonts.py          # 就地替换字体
python3 tools/fonts/subset_fonts.py --check  # 只测量并报告，不写文件
python3 tools/fonts/subset_fonts.py --dir <目录>  # 处理指定目录（默认 assets/fonts）
```

依赖 `fonttools`（`pyftsubset`）。原字体已入库，可用 `git checkout -- app/src/main/assets/fonts` 还原。
脚本同时处理 `.ttf` 与 `.otf`（思源黑体为 CFF/OTF，输出扩展名与源保持一致）。
对已裁剪过的字体重复执行是幂等的（覆盖数与体积不再变化）。

## 字符覆盖

目标字符集共 8968 个字符，由三部分组成：

1. `charset_common.txt` ——《通用规范汉字表》8105 字（一级 3500 + 二级 3000 + 三级 1605）；
2. ASCII 可打印字符；
3. 中文标点、全角字符、常用标点/字母符号/箭头/带圈序号。

裁剪只删除**目标集之外**的冷僻字形：目标集内字符，字体原本能渲染的仍能渲染。
目标集之外的字符会显示为方块。

## 数据来源

`charset_common.txt` 取自《通用规范汉字表》（国务院 2013 年公布），
经 [frankslin/cn-characters-standard](https://github.com/frankslin/cn-characters-standard) 的
`data.js` 提取规范字并校验码位一致（8105 条，0 处不一致）。

字体下载来源：

| 字体 | 文件 | 来源 |
|---|---|---|
| 思源黑体 Source Han Sans | `SourceHanSansCN-Regular.otf` | [adobe-fonts/source-han-sans](https://github.com/adobe-fonts/source-han-sans) `SubsetOTF/CN` |
| 思源宋体 Source Han Serif | `SourceHanSerifCN-Regular.ttf` | [adobe-fonts/source-han-serif](https://github.com/adobe-fonts/source-han-serif) `SubsetOTF/CN` |
| 霞鹜新致宋 | `LXGWNeoZhiSong.ttf` | [lxgw/LxgwNeoZhiSong](https://github.com/lxgw/LxgwNeoZhiSong) v1.067 |
| 霞鹜新晰黑 Screen | `LXGWNeoXiHeiScreen.ttf` | [lxgw/LxgwNeoXiZhi-Screen](https://github.com/lxgw/LxgwNeoXiZhi-Screen) 26.08.21 |
| 霞鹜文楷 Lite | `LXGWWenKai-Regular.ttf` | [lxgw/LxgwWenKai-Lite](https://github.com/lxgw/LxgwWenKai-Lite)（PostScript 名 `LXGWWenKaiLite-Regular`） |
| 青柳行书 | `MasaFont-Regular.ttf` | 项目内既有素材 |
| 衡山草书 | `KouzanBrushFontSousyo.ttf` | 项目内既有素材 |
| 毛笔楷书 | `MaShanZheng-Regular.ttf` | [googlefonts/mashanzheng](https://github.com/googlefonts/mashanzheng) |

## 效果

| 字体 | 原始 | 子集后 | 覆盖 |
|---|---|---|---|
| SourceHanSansCN-Regular.otf | 8.0M | 1.9M | 8723/8968 |
| SourceHanSerifCN-Regular.ttf | 1.0M | 1.0M | 3613/8968 |
| LXGWNeoZhiSong.ttf | 10.0M | 2.9M | 8696/8968 |
| LXGWNeoXiHeiScreen.ttf | 7.3M | 2.1M | 8696/8968 |
| LXGWWenKai-Regular.ttf | 1.6M | 1.6M | 3613/8968 |
| MasaFont-Regular.ttf | 4.7M | 4.7M | 6571/8968 |
| KouzanBrushFontSousyo.ttf | 2.7M | 2.7M | 5667/8968 |
| MaShanZheng-Regular.ttf | 5.4M | 5.4M | 6863/8968 |
| **合计** | **40.8M** | **22.3M** | |

## 授权与注意事项

- **子集化属于「修改字体」**，脚本用 `--name-IDs=* --name-languages=* --name-legacy`
  完整保留 name 表（版权、授权、商标记录，含非英文与 Macintosh 平台），避免丢失授权声明。
- 授权分三档：
  - **OFL 1.1**：思源黑体、思源宋体、霞鹜文楷 Lite、青柳行书、毛笔楷书。
    OFL 要求：再分发时保留版权与授权声明（本脚本已保留）；不得使用保留字体名
    （Source Han Sans 保留名 `Source`、青柳行书保留名 `KouzanBrushFontGyousyo`）另行命名衍生字体。
  - **IPA Font License 1.0**：霞鹜新致宋、霞鹜新晰黑 Screen（字体内 nameID 13/14 指向
    `https://opensource.org/licenses/IPA/`）。IPA 条款允许嵌入 App 与制作衍生字体，
    但衍生字体须继续以 IPA 条款分发，且不得使用原字体名命名衍生字体。当前仅做子集化、未改名。
  - **未声明授权**：衡山草书字体内无授权条款，使用前请确认原作者条款。
- **合规落地**：App 内「开源许可」页面（`LegalDocs.OPEN_SOURCE_LICENSES`，入口在页面底部页脚与「我的」页）
  逐款列出上表字体的版权与授权，并附 **OFL 1.1 与 IPA 1.0 的许可证全文**，
  满足 OFL「随分发附版权声明与许可证」与 IPA「附协议副本」的要求。
- **覆盖数不足目标集的字体**：霞鹜文楷 Lite、思源宋体本身是 3613 字形的精简子集，
  只能覆盖目标集里的 3613 字。这两款字体下，目标集之外的字符会显示为方块——
  这是裁剪前就存在的限制，不是本次引入的。若要改善，需换成完整版字库（体积会显著增加）。
- 其余字体的覆盖数即为其自身字库上限（如毛笔楷书 6863、青柳行书 6571），裁剪不会让覆盖数下降。
- `WidgetFontRenderTest` 会逐个渲染内置字体并与系统默认字体比对，
  防止子集化损坏或资源名失配导致 Android 静默回退到系统字体。