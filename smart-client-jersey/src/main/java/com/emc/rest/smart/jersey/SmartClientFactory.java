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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.glassfish.jersey.apache.connector.ApacheClientProperties;
import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.spi.ConnectorProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.rest.smart.PollingDaemon;
import com.emc.rest.smart.SmartConfig;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;

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
        return createSmartClient(smartConfig, new ApacheConnectorProvider());
    }

    public static Client createSmartClient(SmartConfig smartConfig,
                                           ConnectorProvider baseConnectorProvider) {
        // set up polling for updated host list (if polling is disabled in smartConfig or there's no host list provider,
        // nothing will happen)
        PollingDaemon pollingDaemon = new PollingDaemon(smartConfig);
        pollingDaemon.start();

        // build client config with smart filter connector wrapping the base connector
        ClientConfig clientConfig = createClientConfig(smartConfig);
        if (baseConnectorProvider instanceof ApacheConnectorProvider) {
            configureApacheConnector(smartConfig, clientConfig);
        }

        // inject SmartFilter as a connector wrapper (this is the Jersey integration point of the load balancer)
        clientConfig.connectorProvider((jaxRsClient, runtimeConfig) -> {
            org.glassfish.jersey.client.spi.Connector baseConnector =
                    baseConnectorProvider.getConnector(jaxRsClient, runtimeConfig);
            return new SmartFilter(baseConnector, smartConfig);
        });

        // store polling daemon and other resources in the config so they can be retrieved later in destroy()
        clientConfig.property(PollingDaemon.PROPERTY_KEY, pollingDaemon);

        return ClientBuilder.newClient(clientConfig);
    }

    /**
     * This creates a standard apache-based Jersey client, configured with a SmartConfig, but without any load balancing
     * or node polling.
     */
    public static Client createStandardClient(SmartConfig smartConfig) {
        return createStandardClient(smartConfig, new ApacheConnectorProvider());
    }

    /**
     * This creates a standard apache-based Jersey client, configured with a SmartConfig, but without any load balancing
     * or node polling.
     */
    public static Client createStandardClient(SmartConfig smartConfig,
                                              ConnectorProvider connectorProvider) {
        // init Jersey config
        ClientConfig clientConfig = createClientConfig(smartConfig);
        if (connectorProvider instanceof ApacheConnectorProvider) {
            configureApacheConnector(smartConfig, clientConfig);
        }
        clientConfig.connectorProvider(connectorProvider);

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
    public static void destroy(Client client) {
        PollingDaemon pollingDaemon = (PollingDaemon) client.getConfiguration().getProperty(PollingDaemon.PROPERTY_KEY);
        if (pollingDaemon != null) {
            log.debug("terminating polling daemon");
            pollingDaemon.terminate();
            if (pollingDaemon.getSmartConfig().getHostListProvider() != null) {
                log.debug("destroying host list provider");
                pollingDaemon.getSmartConfig().getHostListProvider().destroy();
            }
        }

        ScheduledExecutorService sched = (ScheduledExecutorService) client.getConfiguration().getProperty(IDLE_CONNECTION_MONITOR_PROPERTY_KEY);
        if (sched != null) {
            log.debug("shutting down scheduled idle connections monitoring task");
            sched.shutdownNow();
        }

        PoolingHttpClientConnectionManager connectionManager = (PoolingHttpClientConnectionManager) client.getConfiguration().getProperty(CONNECTION_MANAGER_PROPERTY_KEY);
        if (connectionManager != null) {
            log.debug("shutting down connection pool");
            connectionManager.close();
        }

        log.debug("destroying Jersey client");
        client.close();
    }

    static ClientConfig createClientConfig(SmartConfig smartConfig) {
        ClientConfig clientConfig = new ClientConfig();

        // pass in jersey parameters from calling code (allows customization of client)
        for (String propName : smartConfig.getProperties().keySet()) {
            clientConfig.property(propName, smartConfig.getProperty(propName));
        }

        // register sized writers with override writers to allow dynamic content-length (i.e. for transformations)
        clientConfig.register(SizeOverrideWriter.ByteArray.class);
        clientConfig.register(SizeOverrideWriter.File.class);
        clientConfig.register(SizeOverrideWriter.SizedInputStream.class);
        clientConfig.register(SizeOverrideWriter.InputStream.class);

        // add support for XML with no content-type
        clientConfig.register(OctetStreamXmlProvider.class);

        // add JSON support (using Jackson's ObjectMapper instead of JAXB marshalling)
        JacksonJaxbJsonProvider jsonProvider = new JacksonJaxbJsonProvider();
        // make sure we don't try to serialize any of these type hierarchies (clearly a bug in JacksonJsonProvider)
        jsonProvider.addUntouchable(java.io.InputStream.class);
        jsonProvider.addUntouchable(java.io.OutputStream.class);
        jsonProvider.addUntouchable(java.io.File.class);
        clientConfig.register(jsonProvider);

        return clientConfig;
    }

    static void configureApacheConnector(SmartConfig smartConfig, ClientConfig clientConfig) {
        // set up multi-threaded connection pool
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setDefaultMaxPerRoute(smartConfig.getIntProperty(MAX_CONNECTIONS_PER_HOST, MAX_CONNECTIONS_PER_HOST_DEFAULT));
        connectionManager.setMaxTotal(smartConfig.getIntProperty(MAX_CONNECTIONS, MAX_CONNECTIONS_DEFAULT));
        clientConfig.property(ApacheClientProperties.CONNECTION_MANAGER, connectionManager);
        // stash connection manager in config for cleanup later in destroy()
        clientConfig.property(CONNECTION_MANAGER_PROPERTY_KEY, connectionManager);

        if (smartConfig.getMaxConnectionIdleTime() > 0) {
            ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor();
            sched.scheduleWithFixedDelay(() -> {
                connectionManager.closeIdleConnections(smartConfig.getMaxConnectionIdleTime(), TimeUnit.SECONDS);
            }, 0, 60, TimeUnit.SECONDS);
            clientConfig.property(IDLE_CONNECTION_MONITOR_PROPERTY_KEY, sched);
        }

        // set proxy config
        if (smartConfig.getProxyUri() != null)
            clientConfig.property(ClientProperties.PROXY_URI, smartConfig.getProxyUri());
        if (smartConfig.getProxyUser() != null)
            clientConfig.property(ClientProperties.PROXY_USERNAME, smartConfig.getProxyUser());
        if (smartConfig.getProxyPass() != null)
            clientConfig.property(ClientProperties.PROXY_PASSWORD, smartConfig.getProxyPass());

        // disable the retry handler if necessary
        if (smartConfig.getProperty(DISABLE_APACHE_RETRY) != null) {
            clientConfig.property(ApacheClientProperties.RETRY_HANDLER,
                    new org.apache.http.impl.client.DefaultHttpRequestRetryHandler(0, false));
        }
    }

    private SmartClientFactory() {
    }
}
