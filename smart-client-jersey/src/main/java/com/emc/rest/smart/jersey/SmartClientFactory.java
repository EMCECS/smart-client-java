/*
 * Copyright (c) 2015-2021 Dell Inc., or its subsidiaries. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.emc.rest.smart.jersey;

import com.emc.rest.smart.PollingDaemon;
import com.emc.rest.smart.SmartConfig;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.apache.connector.ApacheClientProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class SmartClientFactory {

    private static final Logger log = LoggerFactory.getLogger(SmartClientFactory.class);

    public static final String DISABLE_APACHE_RETRY = "com.emc.rest.smart.disableApacheRetry";
    public static final String MAX_CONNECTIONS = "com.emc.rest.smart.apacheMaxConnections";
    public static final String MAX_CONNECTIONS_PER_HOST = "com.emc.rest.smart.apacheMaxConnectionsPerHost";
    public static final int MAX_CONNECTIONS_DEFAULT = 999;
    public static final int MAX_CONNECTIONS_PER_HOST_DEFAULT = 999;

    public static final String IDLE_CONNECTION_MONITOR_PROPERTY_KEY = "com.emc.rest.smart.idleConnectionsExecSvc";
    public static final String CONNECTION_MANAGER_PROPERTY_KEY = "com.emc.rest.smart.apacheConnectionManager";

    public static Client createSmartClient(SmartConfig smartConfig) {
        return createSmartClient(smartConfig, createClientConfig(smartConfig));
    }

    public static Client createSmartClient(SmartConfig smartConfig,
                                           ClientConfig clientConfig) {
        Client client = createStandardClient(smartConfig, clientConfig);

        // inject SmartFilter (this is the Jersey integration point of the load balancer)
        client.register(new SmartFilter(smartConfig));

        // set up polling for updated host list (if polling is disabled in smartConfig or there's no host list provider,
        // nothing will happen)
        PollingDaemon pollingDaemon = new PollingDaemon(smartConfig);
        pollingDaemon.start();

        // store the daemon thread in smartConfig so users can stop it when finished with the client
        smartConfig.getProperties().put(PollingDaemon.PROPERTY_KEY, pollingDaemon);

        return client;
    }

    /**
     * This creates a standard apache-based Jersey client, configured with a SmartConfig, but without any load balancing
     * or node polling.
     */
    public static Client createStandardClient(SmartConfig smartConfig) {
        return createStandardClient(smartConfig, createClientConfig(smartConfig));
    }

    /**
     * This creates a standard apache-based Jersey client, configured with a SmartConfig, but without any load balancing
     * or node polling.
     */
    public static Client createStandardClient(SmartConfig smartConfig,
                                              ClientConfig clientConfig) {
        // pass in jersey parameters from calling code (allows customization of client)
        for (String propName : smartConfig.getProperties().keySet()) {
            clientConfig.property(propName, smartConfig.getProperty(propName));
        }

        // register providers
        clientConfig.register(SizeOverrideWriter.ByteArray.class);
        clientConfig.register(SizeOverrideWriter.File.class);
        clientConfig.register(SizeOverrideWriter.SizedInputStream.class);
        clientConfig.register(SizeOverrideWriter.InputStream.class);

        // add support for XML with no content-type
        clientConfig.register(OctetStreamXmlProvider.class);

        // JSON support is provided by jersey-media-json-jackson dependency

        // build Jersey client
        return ClientBuilder.newClient(clientConfig);
    }

    /**
     * Destroy this client. Any system resources associated with the client
     * will be cleaned up.
     * <p>
     * This method must be called when there are not responses pending otherwise
     * undefined behavior will occur.
     * <p>
     * The client must not be reused after this method is called otherwise
     * undefined behavior will occur.
     */
    public static void destroy(Client client, SmartConfig smartConfig) {
        PollingDaemon pollingDaemon = (PollingDaemon) smartConfig.getProperties().get(PollingDaemon.PROPERTY_KEY);
        if (pollingDaemon != null) {
            log.debug("terminating polling daemon");
            pollingDaemon.terminate();
            if (pollingDaemon.getSmartConfig().getHostListProvider() != null) {
                log.debug("destroying host list provider");
                pollingDaemon.getSmartConfig().getHostListProvider().destroy();
            }
        }

        ScheduledExecutorService sched = (ScheduledExecutorService) smartConfig.getProperties().get(IDLE_CONNECTION_MONITOR_PROPERTY_KEY);
        if (sched != null) {
            log.debug("shutting down scheduled idle connections monitoring task");
            sched.shutdownNow();
        }

        org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager connectionManager = 
            (org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager) smartConfig.getProperties().get(CONNECTION_MANAGER_PROPERTY_KEY);
        if (connectionManager != null) {
            log.debug("shutting down connection pool");
            connectionManager.close();
        }

        log.debug("closing Jersey client");
        client.close();
    }

    static ClientConfig createClientConfig(SmartConfig smartConfig) {
        ClientConfig clientConfig = new ClientConfig();

        // use Apache connector
        clientConfig.connectorProvider(new ApacheConnectorProvider());

        // set up multi-threaded connection pool
        org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager connectionManager = 
            new org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager();
        // 999 maximum active connections (max allowed)
        connectionManager.setDefaultMaxPerRoute(smartConfig.getIntProperty(MAX_CONNECTIONS_PER_HOST, MAX_CONNECTIONS_PER_HOST_DEFAULT));
        connectionManager.setMaxTotal(smartConfig.getIntProperty(MAX_CONNECTIONS, MAX_CONNECTIONS_DEFAULT));
        clientConfig.property(ApacheClientProperties.CONNECTION_MANAGER, connectionManager);
        // stash connection manager in smartConfig for cleanup later in destroy()
        smartConfig.getProperties().put(CONNECTION_MANAGER_PROPERTY_KEY, connectionManager);

        if (smartConfig.getMaxConnectionIdleTime() > 0) {
            ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor();
            sched.scheduleWithFixedDelay(() -> {
                connectionManager.closeExpired();
                connectionManager.closeIdle(org.apache.hc.core5.util.TimeValue.ofSeconds(smartConfig.getMaxConnectionIdleTime()));
            }, 0, 60, TimeUnit.SECONDS);
            smartConfig.getProperties().put(IDLE_CONNECTION_MONITOR_PROPERTY_KEY, sched);
        }

        // set proxy config
        if (smartConfig.getProxyUri() != null)
            clientConfig.property(ClientProperties.PROXY_URI, smartConfig.getProxyUri());
        if (smartConfig.getProxyUser() != null && smartConfig.getProxyPass() != null) {
            clientConfig.property(ClientProperties.PROXY_USERNAME, smartConfig.getProxyUser());
            clientConfig.property(ClientProperties.PROXY_PASSWORD, smartConfig.getProxyPass());
        }

        // Note: Retry handling in Jersey 3.x with HttpClient 5 is configured differently
        // The DISABLE_APACHE_RETRY property is noted but not currently applied in Jersey 3.x

        return clientConfig;
    }

    private SmartClientFactory() {
    }
}
