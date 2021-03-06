package org.apereo.cas.monitor.config;

import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.configuration.model.core.monitor.LdapMonitorProperties;
import org.apereo.cas.configuration.support.Beans;
import org.apereo.cas.monitor.PooledLdapConnectionFactoryHealthIndicator;
import org.apereo.cas.util.LdapUtils;

import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.ldaptive.SearchConnectionValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.actuate.health.CompositeHealthContributor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * This is {@link LdapMonitorConfiguration}.
 *
 * @author Misagh Moayyed
 * @since 5.0.0
 */
@Configuration(value = "ldapMonitorConfiguration", proxyBeanMethods = false)
@EnableConfigurationProperties(CasConfigurationProperties.class)
public class LdapMonitorConfiguration {
    private static final int MAP_SIZE = 8;

    @Autowired
    private CasConfigurationProperties casProperties;

    @Bean
    @ConditionalOnEnabledHealthIndicator("pooledLdapConnectionFactoryHealthIndicator")
    public CompositeHealthContributor pooledLdapConnectionFactoryHealthIndicator() {
        val ldaps = casProperties.getMonitor().getLdap();
        val contributors = new LinkedHashMap<>(MAP_SIZE);
        ldaps.stream()
            .filter(LdapMonitorProperties::isEnabled)
            .map(ldap -> {
                val executor = Beans.newThreadPoolExecutorFactoryBean(ldap.getPool());
                executor.afterPropertiesSet();
                val connectionFactory = LdapUtils.newLdaptivePooledConnectionFactory(ldap);
                val healthIndicator = new PooledLdapConnectionFactoryHealthIndicator(Beans.newDuration(ldap.getMaxWait()).toMillis(),
                    connectionFactory, executor.getObject(), new SearchConnectionValidator());
                val name = StringUtils.defaultIfBlank(ldap.getName(), UUID.randomUUID().toString());
                return Pair.of(name, healthIndicator);
            })
            .forEach(it -> contributors.put(it.getKey(), it.getValue()));
        return CompositeHealthContributor.fromMap((Map) contributors);
    }
}
