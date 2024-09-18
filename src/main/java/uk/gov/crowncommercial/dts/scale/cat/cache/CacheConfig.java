package uk.gov.crowncommercial.dts.scale.cat.cache;

import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.jsr107.Eh107Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.spi.CachingProvider;
import java.time.Duration;

/**
 * Cache setup and configuration for the application using Ehcache
 */
@Configuration
public class CacheConfig {
    @Value("${caching.primary.cacheLength}")
    String primaryCacheLength;

    @Value("${caching.primary.heapSize}")
    String primaryCacheSize;

    @Value("${caching.long.cacheLength}")
    String longCacheLength;

    @Value("${caching.long.heapSize}")
    String longCacheSize;

    /**
     * Initialise the caches we want to use based on life configuration settings
     */
    @Bean
    public CacheManager ehCacheManager() {
        CachingProvider provider = Caching.getCachingProvider();
        CacheManager cacheManager = provider.getCacheManager();

        javax.cache.configuration.Configuration<Object, Object> primaryCacheConfig = getCacheConfigForSpecifiedLifespan(primaryCacheLength, primaryCacheSize);
        javax.cache.configuration.Configuration<Object, Object> longCacheConfig = getCacheConfigForSpecifiedLifespan(longCacheLength, longCacheSize);

        // Establish primary caches
        cacheManager.createCache("agreementsCache", longCacheConfig);
        cacheManager.createCache("gcloudConfigCache", primaryCacheConfig);
        cacheManager.createCache("tendersCache", primaryCacheConfig);
        cacheManager.createCache("conclaveCache", primaryCacheConfig);

        return cacheManager;
    }

    /**
     * Builds a cache configuration object based on the specified life in seconds passed to it
     */
    private javax.cache.configuration.Configuration<Object, Object> getCacheConfigForSpecifiedLifespan(String cacheLength, String cacheSize) {
        CacheConfigurationBuilder<Object, Object> cacheConfigBuilder =
                CacheConfigurationBuilder.newCacheConfigurationBuilder(
                                Object.class,
                                Object.class,
                                ResourcePoolsBuilder
                                        .newResourcePoolsBuilder().heap(Integer.parseInt(cacheSize)))
                        .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofSeconds(Integer.parseInt(cacheLength))));

        javax.cache.configuration.Configuration<Object, Object> cacheConfig = Eh107Configuration.fromEhcacheCacheConfiguration(cacheConfigBuilder);

        return cacheConfig;
    }
}