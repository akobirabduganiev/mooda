package tech.nuqta.mooda.api.dto.stats

import tech.nuqta.mooda.domain.service.StatsService

/**
 * API response for share data. Frontend can build share cards from this JSON.
 */
 data class ShareTodayResponse(
     val scope: String,
     val country: String?,
     val countryName: String?,
     val countryEmoji: String?,
     val date: String,
     val totalCount: Long,
     val top: List<String>,
     val totals: List<ShareTotalItem>
 ) {
     companion object {
         fun fromLive(
             live: StatsService.LiveStatsDto,
             countryName: String?,
             countryEmoji: String?,
             labelProvider: (code: String) -> String
         ): ShareTodayResponse {
             return ShareTodayResponse(
                 scope = live.scope,
                 country = live.country,
                 countryName = countryName,
                 countryEmoji = countryEmoji,
                 date = live.date,
                 totalCount = live.totalCount,
                 top = live.top,
                 totals = live.totals.map { t ->
                     ShareTotalItem(
                         moodType = t.moodType,
                         label = labelProvider(t.moodType),
                         emoji = t.emoji,
                         count = t.count,
                         percent = t.percent
                     )
                 }
             )
         }
     }
 }

 data class ShareTotalItem(
     val moodType: String,
     val label: String,
     val emoji: String,
     val count: Long,
     val percent: Double
 )
