package com.nohimazin.monotune.models

import com.nohimazin.monotune.db.entities.LocalItem
import com.zionhuang.innertube.models.YTItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)

