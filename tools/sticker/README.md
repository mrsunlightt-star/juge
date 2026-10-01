# 贴纸素材工具链（贴纸夜景 / sticker_lalaland）

把一张人物参考原图做成 App 里「贴纸夜景」组件风格用的贴纸素材：
男女主 + 路灯抠成扁平插画、叠白描边、透明底，落到
`app/src/main/res/drawable/sticker_lalaland.webp`。

## 跑法

```bash
cd tools/sticker
python3 01_segment.py        # 需要 torch/torchvision，第一次会下 DeepLabV3 权重
python3 02_refine_mask.py
python3 03_clean_mask.py
python3 04_fix_hand.py
python3 05_build_sticker.py  # 直接写回 res/drawable/
```

依赖：`numpy` `opencv-python` `pillow`，01 额外要 `torch` `torchvision`。

只想改人物轮廓/配色/比例时，跳过 01~03 —— `assets/clean_mask.npy` 就是
03 的产物，04 会自动用它。全链路重跑会得到完全一致的结果（掩码和贴纸
都验证过逐字节相同）。

## 各步在干什么

| 脚本 | 输入 → 输出 | 说明 |
| --- | --- | --- |
| `01_segment.py` | `assets/source.png` → `build/dl_person.npy` | DeepLabV3(COCO) 的 person 概率图，只当粗轮廓用 |
| `02_refine_mask.py` | `dl_person` → `build/refine_mask.npy` | GrabCut 在轮廓带内按颜色细分，找回两人之间、手臂之间的镂空 |
| `03_clean_mask.py` | `refine_mask` → `build/clean_mask.npy` | 清掉误吞的紫色天空块（只清紧凑块，抗锯齿细线保留）；用暖色把被切掉的末端肢体捞回来 |
| `04_fix_hand.py` | `clean_mask` → `build/clean_mask2.npy`、`build/src_painted.png` | 补女主被切掉的左手 |
| `05_build_sticker.py` | 上面两个 + 路灯素材 → `res/drawable/sticker_lalaland.webp` | 矢量化 → 调色板量化 → 白描边 → 与路灯合成 → 颜色外扩 |

## 几个容易踩的点

- **左手必须手工补。** DeepLabV3 把她抬起的左手整只漏掉，GrabCut 也捞不回来，
  03 里那段"暖色补肢体"只能补出位置和形状都不靠谱的一坨。`04_fix_hand.py`
  按手臂走向重画了一只**没有手指**的平滑手掌 —— 男主的手也是这样，看不出
  手指但很自然；画上五指反而像爪子。
- **手部起点要压在掩码内部。** 直接在掩码断口（只有 5px 宽）上接手掌，手腕
  处会捏出一个"腰"。`WRIST=(212.0, 500.0)` 是往前臂里压了一段的位置。
- **手部只描亮边、不描暗边。** 暗边会围着每根手指各长一圈沟。
- **轮廓简化别调大。** `05` 里 `EPS=0.8 / ITERS=2` 是特意压低的，调大会把
  刚补出来的手又抹圆。
- **改输出尺寸要同步 Kotlin。** `05` 末尾会打印宽高比，要同步
  `WidgetCanvasRenderer.kt` 的 `STICKER_ART_ASPECT`（当前 1293f / 1200f）。
- **调色板只在"核心区"采样。** 边缘混色会带出脏色，所以先 erode 再 kmeans；
  另外做了色相约束，否则深棕发会被近黑西裤吞掉。

## 素材来源

`assets/` 里是随仓库提交的输入素材：

- `source.png` —— 参考原图，分割与取色的唯一来源。
- `clean_mask.npy` —— 03 的产物，跳过 01~03 时用。
- `lamp_mask.npy` / `lamp_rgb.png` —— 提前抠好的路灯（抠图脚本没有留存，
  重新抠需自行准备）。

`build/` 是中间产物，可重建，不入库。