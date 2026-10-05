package com.juge.app.ui

/** 保存反馈结果：区分「已落库」与「被付费墙拦下」，避免向用户误报成功。 */
enum class SaveOutcome { SAVED, REQUIRES_PRO }
