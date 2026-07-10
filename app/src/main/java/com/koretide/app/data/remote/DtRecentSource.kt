package com.koretide.app.data.remote

import com.koretide.app.BuildConfig
import com.koretide.app.data.remote.dto.KhoaTideRecentItem
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * dtRecent(복합해양환경관측소 실시간) 응답을 짧게 캐시해 공유.
 *
 * dtRecent 응답 한 건에 조위(bscTdlvHgt)와 바람(wspd/wndrct)이 모두 들어있는데,
 * 조위 조회(TideRepository)와 바람 조회(WeatherRepository)가 각각 dtRecent를 부르면
 * 같은 관측소를 두 번 호출하게 된다. 이 소스로 (obsCode,date)당 한 번만 호출한다.
 *
 * 병렬(조위·바람 동시) 호출에서도 중복을 막기 위해 Mutex로 in-flight 요청을 직렬화한다.
 * 관측소 선택 시 바람의 최근접 관측소는 자기 자신이라 키가 같아 실질적으로 1회로 합쳐진다.
 */
@Singleton
class DtRecentSource @Inject constructor(
    private val api: KhoaDataApiService
) {
    private val key get() = BuildConfig.KHOA_API_KEY

    private data class Entry(val items: List<KhoaTideRecentItem>, val time: Long)

    private val mutex = Mutex()
    private val cache = HashMap<String, Entry>()

    suspend fun items(obsCode: String, date: String): List<KhoaTideRecentItem> {
        val k = "$obsCode|$date"
        cacheHit(k)?.let { return it }
        return mutex.withLock {
            cacheHit(k)?.let { return@withLock it }
            val items = api.getTideRecent(
                serviceKey = key,
                obsCode    = obsCode,
                date       = date,
                numOfRows  = 100
            ).body?.items?.item ?: emptyList()
            if (items.isNotEmpty()) cache[k] = Entry(items, System.currentTimeMillis())
            items
        }
    }

    private fun cacheHit(k: String): List<KhoaTideRecentItem>? =
        cache[k]?.takeIf { System.currentTimeMillis() - it.time < TTL_MS }?.items

    companion object { private const val TTL_MS = 60_000L }
}
