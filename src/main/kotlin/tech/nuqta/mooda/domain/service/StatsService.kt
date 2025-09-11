package tech.nuqta.mooda.domain.service

import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import tech.nuqta.mooda.domain.model.MoodType
import tech.nuqta.mooda.infrastructure.persistence.repository.MoodRepository
import tech.nuqta.mooda.infrastructure.redis.RedisService
import java.time.LocalDate
import java.time.ZoneOffset
import tech.nuqta.mooda.infrastructure.persistence.entity.MoodEntity

@Service
class StatsService(
    private val redis: RedisService,
    private val moodRepoProvider: ObjectProvider<MoodRepository>,
    @param:Value("\${app.stats.min-sample-country:100}")
    private val minSampleCountry: Int
) {
    // Legacy DTOs used by /stats/today
    data class TotalItem(val moodType: String, val count: Long, val percent: Double)
    data class TodayStats(val totals: List<TotalItem>, val top: List<String>)

    // New DTOs for live stats
    data class LiveTotalItem(val moodType: String, val count: Long, val percent: Double, val emoji: String)
    data class LiveStatsDto(
        val scope: String,
        val country: String?,
        val date: String,
        val totals: List<LiveTotalItem>,
        val top: List<String>,
        val totalCount: Long
    )

    // Range DTOs
    data class DayTotals(
        val date: String,
        val totals: List<LiveTotalItem>,
        val top: List<String>,
        val totalCount: Long
    )
    data class RangeStatsDto(
        val scope: String,
        val country: String?,
        val dateFrom: String,
        val dateTo: String,
        val totals: List<LiveTotalItem>,
        val top: List<String>,
        val totalCount: Long,
        val series: List<DayTotals>
    )

    fun today(country: String?, locale: String?): Mono<TodayStats> {
        val today = LocalDate.now(ZoneOffset.UTC)
        return fetchFromRedis(country).flatMap { redisTotals ->
            val sum = redisTotals.values.sum()
            if (sum > 0L) {
                Mono.just(buildTodayResponse(redisTotals))
            } else {
                val repo = moodRepoProvider.ifAvailable
                    ?: return@flatMap Mono.just(buildTodayResponse(redisTotals))
                repo.findByDay(today)
                    .filter { entity -> matchesCountryLocale(entity, country, locale) }
                    .collectList()
                    .map { list ->
                        val map = aggregateCounts(list)
                        buildTodayResponse(map)
                    }
            }
        }
    }

    fun live(country: String?, locale: String?): Mono<LiveStatsDto> {
        val today = LocalDate.now(ZoneOffset.UTC)
        return fetchFromRedis(country).flatMap { redisTotals ->
            val sum = redisTotals.values.sum()
            if (sum > 0L) {
                Mono.just(buildLiveResponse(redisTotals, country, today))
            } else {
                val repo = moodRepoProvider.ifAvailable
                    ?: return@flatMap Mono.just(buildLiveResponse(redisTotals, country, today))
                repo.findByDay(today)
                    .filter { entity -> matchesCountryLocale(entity, country, locale) }
                    .collectList()
                    .map { list ->
                        val map = aggregateCounts(list)
                        buildLiveResponse(map, country, today)
                    }
            }
        }
    }

    fun byDay(date: LocalDate, country: String?, locale: String?): Mono<LiveStatsDto> {
        val today = LocalDate.now(ZoneOffset.UTC)
        if (date == today) return live(country, locale)
        val repo = moodRepoProvider.ifAvailable
            ?: return Mono.just(buildLiveResponse(emptyMap(), country, date))
        return repo.findByDay(date)
            .filter { entity -> matchesCountryLocale(entity, country, locale) }
            .collectList()
            .map { list ->
                val map = aggregateCounts(list)
                buildLiveResponse(map, country, date)
            }
    }

    fun range(period: String, country: String?, locale: String?): Mono<RangeStatsDto> {
        val today = LocalDate.now(ZoneOffset.UTC)
        val days = when (period.lowercase()) {
            "week", "7d" -> 7L
            "month", "30d" -> 30L
            "year", "365d" -> 365L
            else -> 7L
        }
        val start = today.minusDays(days - 1)
        val repo = moodRepoProvider.ifAvailable
            ?: return Mono.just(
                RangeStatsDto(
                    scope = if (country.isNullOrBlank()) "GLOBAL" else "COUNTRY",
                    country = country?.uppercase(),
                    dateFrom = start.toString(),
                    dateTo = today.toString(),
                    totals = buildLiveResponse(emptyMap(), country, today).totals,
                    top = emptyList(),
                    totalCount = 0,
                    series = emptyList()
                )
            )
        return repo.findByDayBetween(start, today)
            .filter { entity -> matchesCountryLocale(entity, country, locale) }
            .collectList()
            .map { list ->
                // Group by day
                val byDay = list.groupBy { it.day }
                // Build series
                val series = mutableListOf<DayTotals>()
                val aggCounts = HashMap<String, Long>(MoodType.entries.size)
                var d = start
                while (!d.isAfter(today)) {
                    val entities = byDay[d].orEmpty()
                    val dayMap = aggregateCounts(entities)
                    // accumulate
                    for (mt in MoodType.entries) {
                        val key = mt.name
                        aggCounts[key] = (aggCounts[key] ?: 0L) + (dayMap[key] ?: 0L)
                    }
                    val dayDto = buildLiveResponse(dayMap, country, d)
                    series.add(DayTotals(date = dayDto.date, totals = dayDto.totals, top = dayDto.top, totalCount = dayDto.totalCount))
                    d = d.plusDays(1)
                }
                val overall = buildLiveResponse(aggCounts, country, today)
                RangeStatsDto(
                    scope = overall.scope,
                    country = overall.country,
                    dateFrom = start.toString(),
                    dateTo = today.toString(),
                    totals = overall.totals,
                    top = overall.top,
                    totalCount = overall.totalCount,
                    series = series
                )
            }
    }

    private fun fetchFromRedis(country: String?): Mono<Map<String, Long>> {
        val types = MoodType.entries
        val monos = types.map { type ->
            val key = if (country.isNullOrBlank())
                "mooda:cnt:today:mood:${type.name}"
            else
                "mooda:cnt:today:country:${country.uppercase()}:${type.name}"
            redis.get(key).map { it.toLongOrNull() ?: 0L }.defaultIfEmpty(0L).map { type.name to it }
        }
        return Mono.zip(monos) { arr ->
            (arr.toList() as List<Pair<String, Long>>).toMap()
        }
    }

    private fun matchesCountryLocale(entity: MoodEntity, country: String?, locale: String?): Boolean {
        val countryOk = country?.let { c -> entity.country.equals(c, ignoreCase = true) } ?: true
        val localeOk = locale?.let { l -> entity.locale?.startsWith(l, ignoreCase = true) == true } ?: true
        return countryOk && localeOk
    }

    private fun aggregateCounts(entities: List<MoodEntity>): Map<String, Long> {
        if (entities.isEmpty()) return emptyMap()
        val counts = HashMap<String, Long>(MoodType.entries.size)
        entities.forEach { e ->
            val mt = runCatching { MoodType.valueOf(e.moodType) }.getOrNull() ?: return@forEach
            val key = mt.name
            counts[key] = (counts[key] ?: 0L) + 1
        }
        return counts
    }

    private fun buildTodayResponse(countsByType: Map<String, Long>): TodayStats {
        val total = countsByType.values.sum().coerceAtLeast(0L)
        val totals = MoodType.entries.map { type ->
            val count = countsByType[type.name] ?: 0L
            val percent = if (total > 0) ((count.toDouble() * 100.0) / total.toDouble()) else 0.0
            val rounded = (percent * 10.0).toInt() / 10.0
            TotalItem(moodType = type.name, count = count, percent = rounded)
        }
        val top = totals.sortedByDescending { it.count }.take(5).map { it.moodType }
        return TodayStats(totals = totals, top = top)
    }

    private fun buildLiveResponse(
        countsByType: Map<String, Long>,
        country: String?,
        today: LocalDate
    ): LiveStatsDto {
        val total = countsByType.values.sum().coerceAtLeast(0L)
        val totals = MoodType.entries.map { type ->
            val count = countsByType[type.name] ?: 0L
            val percent = if (total > 0) ((count.toDouble() * 100.0) / total.toDouble()) else 0.0
            val rounded = (percent * 10.0).toInt() / 10.0
            LiveTotalItem(moodType = type.name, count = count, percent = rounded, emoji = type.defaultEmoji)
        }
        val sorted = totals.sortedByDescending { it.count }
        val top = sorted.take(5).map { it.moodType }
        return LiveStatsDto(
            scope = if (country.isNullOrBlank()) "GLOBAL" else "COUNTRY",
            country = country?.uppercase(),
            date = today.toString(),
            totals = sorted,
            top = top,
            totalCount = total
        )
    }
    data class LeaderboardItem(
        val country: String,
        val score: Double,
        val topMood: String,
        val sample: Long
    )
    data class LeaderboardDto(
        val date: String,
        val items: List<LeaderboardItem>
    )

    fun leaderboard(limit: Int = 20): Mono<LeaderboardDto> {
        val today = LocalDate.now(ZoneOffset.UTC)
        // discover countries by scanning HAPPY keys
        val pattern = "mooda:cnt:today:country:*:HAPPY"
        return redis.scan(pattern)
            .mapNotNull { key ->
                val parts = key.split(":")
                // mooda:cnt:today:country:{CC}:{TYPE}
                if (parts.size >= 6) parts[4] else null
            }
            .distinct()
            .flatMap { ccNullable: String? ->
                val cc = ccNullable ?: return@flatMap Mono.empty()
                // fetch all mood counts for this country
                val types = MoodType.entries
                val monos = types.map { t ->
                    val k = "mooda:cnt:today:country:${cc}:${t.name}"
                    redis.get(k).map { v -> t.name to (v.toLongOrNull() ?: 0L) }.defaultIfEmpty(t.name to 0L)
                }
                Mono.zip(monos) { arr ->
                    val map = (arr.toList() as List<Pair<String, Long>>).toMap()
                    cc to map
                }
            }
            .mapNotNull { (cc, map) ->
                val total = map.values.sum()
                if (total < minSampleCountry) null else {
                    val positive = listOf("HAPPY","CALM","GRATEFUL","EXCITED").sumOf { map[it] ?: 0L }
                    val score = if (total > 0) positive.toDouble() / total.toDouble() else 0.0
                    val top = map.entries.maxByOrNull { it.value }?.key ?: "HAPPY"
                    LeaderboardItem(country = cc, score = String.format("%.2f", score).toDouble(), topMood = top, sample = total)
                }
            }
            .filter { it != null }
            .map { it!! }
            .sort { a, b -> b.score.compareTo(a.score) }
            .take(limit.toLong())
            .collectList()
            .map { list -> LeaderboardDto(date = today.toString(), items = list) }
    }
}