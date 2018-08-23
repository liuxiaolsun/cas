package org.apereo.cas.config;

import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.configuration.support.Beans;
import org.apereo.cas.ticket.TicketCatalog;
import org.apereo.cas.ticket.TicketDefinition;
import org.apereo.cas.ticket.registry.EhCacheTicketRegistry;
import org.apereo.cas.ticket.registry.TicketRegistry;
import org.apereo.cas.util.CollectionUtils;
import org.apereo.cas.util.CoreTicketUtils;
import org.apereo.cas.util.ResourceUtils;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.bootstrap.BootstrapCacheLoader;
import net.sf.ehcache.config.PersistenceConfiguration;
import net.sf.ehcache.distribution.CacheReplicator;
import net.sf.ehcache.distribution.RMIAsynchronousCacheReplicator;
import net.sf.ehcache.distribution.RMIBootstrapCacheLoader;
import net.sf.ehcache.distribution.RMISynchronousCacheReplicator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.ehcache.EhCacheFactoryBean;
import org.springframework.cache.ehcache.EhCacheManagerFactoryBean;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * This is {@link EhcacheTicketRegistryConfiguration}.
 *
 * @author Misagh Moayyed
 * @since 5.0.0
 */
@Configuration("ehcacheTicketRegistryConfiguration")
@EnableConfigurationProperties(CasConfigurationProperties.class)
@Slf4j
public class EhcacheTicketRegistryConfiguration {
    @Autowired
    private CasConfigurationProperties casProperties;

    @RefreshScope
    @Bean
    @ConditionalOnMissingBean(name = "ticketRMISynchronousCacheReplicator")
    public CacheReplicator ticketRMISynchronousCacheReplicator() {
        val cache = casProperties.getTicket().getRegistry().getEhcache();
        return new RMISynchronousCacheReplicator(
            cache.isReplicatePuts(),
            cache.isReplicatePutsViaCopy(),
            cache.isReplicateUpdates(),
            cache.isReplicateUpdatesViaCopy(),
            cache.isReplicateRemovals());
    }

    @RefreshScope
    @Bean
    @ConditionalOnMissingBean(name = "ticketRMIAsynchronousCacheReplicator")
    public CacheReplicator ticketRMIAsynchronousCacheReplicator() {
        val cache = casProperties.getTicket().getRegistry().getEhcache();
        return new RMIAsynchronousCacheReplicator(
            cache.isReplicatePuts(),
            cache.isReplicatePutsViaCopy(),
            cache.isReplicateUpdates(),
            cache.isReplicateUpdatesViaCopy(),
            cache.isReplicateRemovals(),
            (int) Beans.newDuration(cache.getReplicationInterval()).toMillis(),
            cache.getMaximumBatchSize());
    }

    @RefreshScope
    @Bean
    @ConditionalOnMissingBean(name = "ticketCacheBootstrapCacheLoader")
    public BootstrapCacheLoader ticketCacheBootstrapCacheLoader() {
        val cache = casProperties.getTicket().getRegistry().getEhcache();
        return new RMIBootstrapCacheLoader(cache.isLoaderAsync(), cache.getMaxChunkSize());
    }

    @Bean
    public EhCacheManagerFactoryBean ehcacheTicketCacheManager() {
        val cache = casProperties.getTicket().getRegistry().getEhcache();
        val bean = new EhCacheManagerFactoryBean();

        val configExists = ResourceUtils.doesResourceExist(cache.getConfigLocation());
        if (configExists) {
            bean.setConfigLocation(cache.getConfigLocation());
        } else {
            LOGGER.warn("Ehcache configuration file [{}] cannot be found", cache.getConfigLocation());
        }

        bean.setShared(cache.isShared());
        bean.setCacheManagerName(cache.getCacheManagerName());
        return bean;
    }

    private Ehcache buildCache(final TicketDefinition ticketDefinition) {
        val cache = casProperties.getTicket().getRegistry().getEhcache();
        val configExists = ResourceUtils.doesResourceExist(cache.getConfigLocation());

        val ehcacheProperties = casProperties.getTicket().getRegistry().getEhcache();
        val bean = new EhCacheFactoryBean();

        bean.setCacheName(ticketDefinition.getProperties().getStorageName());
        LOGGER.debug("Constructing Ehcache cache [{}]", bean.getName());

        if (configExists) {
            bean.setCacheEventListeners(CollectionUtils.wrapSet(ticketRMISynchronousCacheReplicator()));
            bean.setBootstrapCacheLoader(ticketCacheBootstrapCacheLoader());
        } else {
            LOGGER.warn("In registering ticket definition [{}], Ehcache configuration file [{}] cannot be found "
                + "so no cache event listeners will be configured to bootstrap. "
                + "The ticket registry will operate in standalone mode", ticketDefinition.getPrefix(), cache.getConfigLocation());
        }

        bean.setTimeToIdle((int) ticketDefinition.getProperties().getStorageTimeout());
        bean.setTimeToLive((int) ticketDefinition.getProperties().getStorageTimeout());
        bean.setDiskExpiryThreadIntervalSeconds(ehcacheProperties.getDiskExpiryThreadIntervalSeconds());
        bean.setEternal(ehcacheProperties.isEternal());
        bean.setMaxEntriesLocalHeap(ehcacheProperties.getMaxElementsInMemory());
        bean.setMaxEntriesInCache(ehcacheProperties.getMaxElementsInCache());
        bean.setMaxEntriesLocalDisk(ehcacheProperties.getMaxElementsOnDisk());
        bean.setMemoryStoreEvictionPolicy(ehcacheProperties.getMemoryStoreEvictionPolicy());
        val c = new PersistenceConfiguration();
        c.strategy(ehcacheProperties.getPersistence());
        c.setSynchronousWrites(ehcacheProperties.isSynchronousWrites());
        bean.persistence(c);

        bean.afterPropertiesSet();
        return bean.getObject();
    }

    @Autowired
    @Bean
    public TicketRegistry ticketRegistry(@Qualifier("ehcacheTicketCacheManager") final CacheManager manager,
                                         @Qualifier("ticketCatalog") final TicketCatalog ticketCatalog) {
        val crypto = casProperties.getTicket().getRegistry().getEhcache().getCrypto();

        val definitions = ticketCatalog.findAll();
        definitions.forEach(t -> {
            val ehcache = buildCache(t);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Created Ehcache cache [{}] for [{}]", ehcache.getName(), t);


                val config = ehcache.getCacheConfiguration();
                LOGGER.debug("TicketCache.maxEntriesLocalHeap=[{}]", config.getMaxEntriesLocalHeap());
                LOGGER.debug("TicketCache.maxEntriesLocalDisk=[{}]", config.getMaxEntriesLocalDisk());
                LOGGER.debug("TicketCache.maxEntriesInCache=[{}]", config.getMaxEntriesInCache());
                LOGGER.debug("TicketCache.persistenceConfiguration=[{}]", config.getPersistenceConfiguration().getStrategy());
                LOGGER.debug("TicketCache.synchronousWrites=[{}]", config.getPersistenceConfiguration().getSynchronousWrites());
                LOGGER.debug("TicketCache.timeToLive=[{}]", config.getTimeToLiveSeconds());
                LOGGER.debug("TicketCache.timeToIdle=[{}]", config.getTimeToIdleSeconds());
                LOGGER.debug("TicketCache.cacheManager=[{}]", ehcache.getCacheManager().getName());
            }
            manager.addDecoratedCacheIfAbsent(ehcache);
        });

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("The following caches are available: [{}]", (Object[]) manager.getCacheNames());
        }
        return new EhCacheTicketRegistry(ticketCatalog, manager, CoreTicketUtils.newTicketRegistryCipherExecutor(crypto, "ehcache"));
    }
}
