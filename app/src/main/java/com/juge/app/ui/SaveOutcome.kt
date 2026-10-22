package com.juge.app.ui

/** 保存反馈结果：区分「已落库」「仅预览未落库」与「被付费墙拦下」，避免向用户误报成功。 */
enum class SaveOutcome { SAVED, PREVIEW_ONLY, REQUIRES_PRO }
