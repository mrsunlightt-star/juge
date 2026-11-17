package com.juge.app.ui

import androidx.compose.ui.Alignment
import com.juge.app.data.AppBackgroundPreset

/**
 * 每个背景的视觉重点不同，裁切锚点不能共用：
 * 阳光海滩的遮阳伞在右侧，大海的人物与长椅在中轴线上。
 */
internal val AppBackgroundPreset.heroAlignment: Alignment
    get() = when (this) {
        AppBackgroundPreset.SUNNY_BEACH -> Alignment.TopEnd
        AppBackgroundPreset.SEA -> Alignment.TopCenter
    }

/** 缩略图是横向盒子，除了水平锚点还要把纵向构图移到主体所在的中下部。 */
internal val AppBackgroundPreset.thumbnailAlignment: Alignment
    get() = when (this) {
        AppBackgroundPreset.SUNNY_BEACH -> Alignment.CenterEnd
        AppBackgroundPreset.SEA -> Alignment.Center
    }
