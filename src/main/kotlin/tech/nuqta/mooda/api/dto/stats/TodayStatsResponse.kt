package tech.nuqta.mooda.api.dto.stats

/**
 * Legacy daily stats response (for backward compatibility)
 */
data class TotalItem(val moodType: String, val count: Long, val percent: Double)

data class TodayStatsResponse(val totals: List<TotalItem>, val top: List<String>)