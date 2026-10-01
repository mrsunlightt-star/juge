"""贴纸工具链共用的路径。

所有脚本都从这里取路径，不再写死 /tmp，换个位置放也能跑。
"""
from pathlib import Path

HERE = Path(__file__).resolve().parent

ASSETS = HERE / "assets"          # 输入素材（随仓库一起提交）
WORK = HERE / "build"             # 中间产物（可重建，不入库）

SOURCE = ASSETS / "source.png"    # 参考原图：分割与取色的唯一来源
LAMP_MASK = ASSETS / "lamp_mask.npy"
LAMP_RGB = ASSETS / "lamp_rgb.png"
CLEAN_MASK = ASSETS / "clean_mask.npy"   # 已修好的人物掩码，可跳过 01~03

DL_PERSON = WORK / "dl_person.npy"
REFINE_MASK = WORK / "refine_mask.npy"
WORK_CLEAN_MASK = WORK / "clean_mask.npy"
PAINTED_SOURCE = WORK / "src_painted.png"
PATCHED_MASK = WORK / "clean_mask2.npy"

DRAWABLE = HERE.parent.parent / "app/src/main/res/drawable"


def ensure_work():
    WORK.mkdir(parents=True, exist_ok=True)


def source_mask():
    """补手这一步要用的干净掩码：优先用本地产物，没有就退回 assets 里那份。"""
    if WORK_CLEAN_MASK.exists():
        print("掩码: %s（本地重新生成的）" % WORK_CLEAN_MASK)
        return WORK_CLEAN_MASK
    print("掩码: %s（仓库自带）" % CLEAN_MASK)
    return CLEAN_MASK