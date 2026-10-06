# AGENTS.md

## 协作规则

### 方案取舍先汇报（素材不达标时尤其）

**先停下来汇报，由用户拍板；不要自己改道。** 典型触发场景是输入素材质量不达标
（示例图分辨率过低、没有透明底、带水印等），导致文档里的既定接入路线走不通——
例如「把效果图处理成 drawable 素材铺满组件」在 250px 的图上必然糊成一片，
这时**不要**自作主张换成代码重绘之类另一条实现路线，也不要因为"反正能出图"就先做完。

汇报至少包含三件事：

1. **现状**：素材到底能不能用（尺寸 / 透明底 / 水印，用 `tools/process_component_image.py info` 说话）；
2. **原路线为什么走不通**；
3. **可选方案与各自代价**（还原度、代码量、4×2 / 4×4 多尺寸表现），并给出你的建议。

范围仅限「需要替用户做取舍」的决定。纯实现细节（命名、文件落点、跟着编译器补 `when` 分支）
照旧自己处理，不必逐条请示。

## Agent skills

### Issue tracker

Issues are tracked as local markdown files under `.scratch/`. See `docs/agents/issue-tracker.md`.

### Triage labels

Five canonical roles, label == name: needs-triage, needs-info, ready-for-agent, ready-for-human, wontfix. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `CONTEXT.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.
