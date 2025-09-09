package tech.nuqta.mooda.domain.service

import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import tech.nuqta.mooda.domain.model.MoodType
import tech.nuqta.mooda.infrastructure.persistence.repository.MoodRepository
import tech.nuqta.mooda.infrastructure.redis.RedisService
import java.time.LocalDate
import java.time.ZoneOffset

@Service
class StatsService(
    private val redis: RedisService,
    private val moodRepoProvider: ObjectProvider<MoodRepository>
) {
    data class TotalItem(val moodType: String, val count: Long, val percent: Double)
    data class TodayStats(val totals: List<TotalItem>, val top: List<String>)

    fun today(country: String?, locale: String?): Mono<TodayStats> {
        val today = LocalDate.now(ZoneOffset.UTC)
        return fetchFromRedis(country).flatMap { redisTotals ->
            val sum = redisTotals.values.sum()
            if (sum > 0L) {
                Mono.just(buildResponse(redisTotals))
            } else {
                val repo = moodRepoProvider.ifAvailable
                    ?: return@flatMap Mono.just(buildResponse(redisTotals))
                repo.findByDay(today)
                    .filter { entity ->
                        val countryOk = country?.let { c -> entity.country?.equals(c, ignoreCase = true) == true } ?: true
                        val localeOk = locale?.let { l -> entity.locale?.startsWith(l, ignoreCase = true) == true } ?: true
                        countryOk && localeOk
                    }
                    .collectList()
                    .map { list ->
                        val map = MoodType.entries.associateWith { 0L }.toMutableMap()
                        list.forEach { e ->
                            val mt = runCatching { MoodType.valueOf(e.moodType) }.getOrNull()
                            if (mt != null) map[mt] = map.getOrDefault(mt, 0L) + 1
                        }
                        buildResponse(map.mapKeys { it.key.name })
                    }
            }
        }
    }

    private fun fetchFromRedis(country: String?): Mono<Map<String, Long>> {
        val types = MoodType.entries
        val monos = types.map { type ->
            val key = if (country.isNullOrBlank())
                "mooda:cnt:today:mood:${type.name}"
            else
                "mooda:cnt:today:mood:${type.name}:${country.uppercase()}"
            redis.get(key).map { it.toLongOrNull() ?: 0L }.defaultIfEmpty(0L).map { type.name to it }
        }
        return Mono.zip(monos) { arr ->
            arr.associate { it as Pair<String, Long> }
        }
    }

    private fun buildResponse(countsByType: Map<String, Long>): TodayStats {
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
}