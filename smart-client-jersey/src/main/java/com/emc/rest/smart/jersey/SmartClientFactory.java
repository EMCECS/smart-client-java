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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import org.glassfish.jersey.apache.connector.ApacheClientProperties;
import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.jackson.JacksonFeature;

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
        return createSmartClient(smartConfig, createStandardClient(smartConfig));
    }

    public static Client createSmartClient(SmartConfig smartConfig, Client client) {
        client.register(new SmartFilter(smartConfig));

        // set up polling for updated host list (if polling is disabled in smartConfig or there's no host list provider,
        // nothing will happen)
        PollingDaemon pollingDaemon = new PollingDaemon(smartConfig);
        pollingDaemon.start();

        // attach the daemon thread to the client so users can stop it when finished with the client
        client.property(PollingDaemon.PROPERTY_KEY, pollingDaemon);

        return client;
    }

    /**
     * This creates a standard apache-based Jersey client, configured with a SmartConfig, but without any load balancing
     * or node polling.
     */
    public static Client createStandardClient(SmartConfig smartConfig) {
        ClientConfig clientConfig = createApacheClientConfig(smartConfig);
        Client client = ClientBuilder.newClient(clientConfig);

        client.register(JacksonFeature.class);
        client.register(OctetStreamXmlProvider.class);
        client.register(SizeOverrideWriter.ByteArray.class);
        client.register(SizeOverrideWriter.File.class);
        client.register(SizeOverrideWriter.InputStream.class);
        client.register(SizeOverrideWriter.SizedInputStream.class);

        return client;
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
        PollingDaemon pollingDaemon = (PollingDaemon) client.getConfiguration().getProperties().get(PollingDaemon.PROPERTY_KEY);
        if (pollingDaemon != null) {
            log.debug("terminating polling daemon");
            pollingDaemon.terminate();
            if (pollingDaemon.getSmartConfig().getHostListProvider() != null) {
                log.debug("destroying host list provider");
                pollingDaemon.getSmartConfig().getHostListProvider().destroy();
            }
        }

        ScheduledExecutorService sched = (ScheduledExecutorService) client.getConfiguration().getProperties().get(IDLE_CONNECTION_MONITOR_PROPERTY_KEY);
        if (sched != null) {
            log.debug("shutting down scheduled idle connections monitoring task");
            sched.shutdownNow();
        }

        org.apache.http.impl.conn.PoolingClientConnectionManager connectionManager = (org.apache.http.impl.conn.PoolingClientConnectionManager) client.getConfiguration().getProperties().get(CONNECTION_MANAGER_PROPERTY_KEY);
        if (connectionManager != null) {
            log.debug("shutting down connection pool");
            connectionManager.shutdown();
        }

        log.debug("destroying Jersey client");
        client.close();
    }

    static ClientConfig createApacheClientConfig(SmartConfig smartConfig) {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.connectorProvider(new ApacheConnectorProvider());

        // set up multi-threaded connection pool
        // TODO: find a non-deprecated connection manager that works (swapping out with
        //       PoolingHttpClientConnectionManager will break threading)
        org.apache.http.impl.conn.PoolingClientConnectionManager connectionManager = new org.apache.http.impl.conn.PoolingClientConnectionManager();
        // 999 maximum active connections (max allowed)
        connectionManager.setDefaultMaxPerRoute(smartConfig.getIntProperty(MAX_CONNECTIONS_PER_HOST, MAX_CONNECTIONS_PER_HOST_DEFAULT));
        connectionManager.setMaxTotal(smartConfig.getIntProperty(MAX_CONNECTIONS, MAX_CONNECTIONS_DEFAULT));
        clientConfig.property(ApacheClientProperties.CONNECTION_MANAGER, connectionManager);
        clientConfig.property(ApacheClientProperties.CONNECTION_MANAGER_SHARED, true);
        clientConfig.property(CONNECTION_MANAGER_PROPERTY_KEY, connectionManager);
        // stash connection manager in smartConfig for cleanup later in destroy()
        smartConfig.getProperties().put(CONNECTION_MANAGER_PROPERTY_KEY, connectionManager);

        if (smartConfig.getMaxConnectionIdleTime() > 0) {
            ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor();
            sched.scheduleWithFixedDelay(() -> {
                connectionManager.closeIdleConnections(smartConfig.getMaxConnectionIdleTime(), TimeUnit.SECONDS);
            }, 0, 60, TimeUnit.SECONDS);
            clientConfig.property(IDLE_CONNECTION_MONITOR_PROPERTY_KEY, sched);
            smartConfig.getProperties().put(IDLE_CONNECTION_MONITOR_PROPERTY_KEY, sched);
        }

        // set proxy config
        if (smartConfig.getProxyUri() != null) {
            org.apache.http.HttpHost proxy = new org.apache.http.HttpHost(smartConfig.getProxyUri().getHost(), smartConfig.getProxyUri().getPort(), smartConfig.getProxyUri().getScheme());
            org.apache.http.client.config.RequestConfig requestConfig = org.apache.http.client.config.RequestConfig.custom().setProxy(proxy).build();
            clientConfig.property(ApacheClientProperties.REQUEST_CONFIG, requestConfig);

            if (smartConfig.getProxyUser() != null && smartConfig.getProxyPass() != null) {
                org.apache.http.impl.client.BasicCredentialsProvider credentialsProvider = new org.apache.http.impl.client.BasicCredentialsProvider();
                credentialsProvider.setCredentials(new org.apache.http.auth.AuthScope(proxy),
                        new org.apache.http.auth.UsernamePasswordCredentials(smartConfig.getProxyUser(), smartConfig.getProxyPass()));
                clientConfig.property(ApacheClientProperties.CREDENTIALS_PROVIDER, credentialsProvider);
            }
        }

        // pass in jersey parameters from calling code (allows customization of client)
        for (String propName : smartConfig.getProperties().keySet()) {
            clientConfig.property(propName, smartConfig.getProperty(propName));
        }

        // disable the retry handler if necessary
        if (smartConfig.getProperty(DISABLE_APACHE_RETRY) != null) {
            clientConfig.property(ApacheClientProperties.RETRY_HANDLER, new org.apache.http.impl.client.DefaultHttpRequestRetryHandler(0, false));
        }

        return clientConfig;
    }

    private SmartClientFactory() {
    }
}
