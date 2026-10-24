package com.conreo.couchytv.data

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Process-wide, bounded cache for streamed aerial wallpapers.
 *
 * The cache is deliberately passive: ExoPlayer stores only bytes it actually
 * plays. That makes returning Home fast without downloading a multi-gigabyte
 * video collection in the background. Old clips are removed automatically by
 * an LRU evictor and Android may clear the cache directory at any time.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object AerialVideoCache {
    private const val MAX_BYTES = 896L * 1024L * 1024L
    private val lock = Any()

    @Volatile
    private var instance: SimpleCache? = null

    fun warm(context: Context) {
        get(context.applicationContext)
    }

    fun dataSourceFactory(
        context: Context,
        upstream: DataSource.Factory,
    ): DataSource.Factory = CacheDataSource.Factory()
        .setCache(get(context.applicationContext))
        .setUpstreamDataSourceFactory(upstream)
        // A corrupt/missing cache entry must never make the wallpaper fail: the
        // CDN remains the source of truth and playback transparently continues.
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    fun bytesUsed(): Long = instance?.cacheSpace ?: 0L

    private fun get(context: Context): SimpleCache = instance ?: synchronized(lock) {
        instance ?: SimpleCache(
            File(context.cacheDir, "aerial-video"),
            LeastRecentlyUsedCacheEvictor(MAX_BYTES),
            StandaloneDatabaseProvider(context),
        ).also { instance = it }
    }
}
