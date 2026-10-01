"""第一步：用 torchvision 预训练 DeepLabV3(COCO) 把人从原图里粗分出来。

输出的是概率图，不是最终掩码 —— 它会把抬起的左手整只漏掉、也会把两人之间
的镂空糊上。下一步的 GrabCut 才负责按颜色把边界修细。

产物：build/dl_person.npy（与原图等大的 person 概率图）
"""
import numpy as np
import torch
import cv2
from PIL import Image
from torchvision.models.segmentation import deeplabv3_resnet50, DeepLabV3_ResNet50_Weights

import paths

paths.ensure_work()

w = DeepLabV3_ResNet50_Weights.COCO_WITH_VOC_LABELS_V1
model = deeplabv3_resnet50(weights=w).eval()

img = Image.open(paths.SOURCE).convert('RGB')
arr = np.asarray(img)
t = w.transforms()(img).unsqueeze(0)
with torch.no_grad():
    out = model(t)['out'][0]
prob = out.softmax(0)
# COCO/VOC 标签里 person = 15
person = prob[15].numpy()

p = cv2.resize(person, (arr.shape[1], arr.shape[0]), interpolation=cv2.INTER_LINEAR)
np.save(paths.DL_PERSON, p)
print('person prob %.3f~%.3f  mean %.3f' % (person.min(), person.max(), person.mean()))

vis = arr.copy()
vis[p > 0.5] = (vis[p > 0.5] * 0.35 + np.array([255, 0, 0]) * 0.65).astype(np.uint8)
Image.fromarray(vis).save(paths.WORK / 'dl_overlay.png')

m = (p > 0.5).astype(np.uint8)
n, lab, st, _ = cv2.connectedComponentsWithStats(m, 8)
for i in range(1, n):
    if st[i, 4] > 200:
        print('  comp %d area %d box %s' % (i, st[i, 4], st[i, :4]))
Image.fromarray(m * 255).save(paths.WORK / 'dl_mask.png')
print('saved', paths.DL_PERSON)