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
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandler;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.client.apache4.ApacheHttpClient4;
import com.sun.jersey.client.apache4.ApacheHttpClient4Handler;
import com.sun.jersey.client.apache4.config.ApacheHttpClient4Config;
import com.sun.jersey.core.impl.provider.entity.ByteArrayProvider;
import com.sun.jersey.core.impl.provider.entity.FileProvider;
import com.sun.jersey.core.impl.provider.entity.InputStreamProvider;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

public final class SmartClientFactory {

    private static final Logger log = LoggerFactory.getLogger(SmartClientFactory.class);

    public static final String DISABLE_APACHE_RETRY = "com.emc.rest.smart.disableApacheRetry";
    public static final String MAX_CONNECTIONS = "com.emc.rest.smart.apacheMaxConnections";
    public static final String MAX_CONNECTIONS_PER_HOST = "com.emc.rest.smart.apacheMaxConnectionsPerHost";
    public static final int MAX_CONNECTIONS_DEFAULT = 999;
    public static final int MAX_CONNECTIONS_PER_HOST_DEFAULT = 999;

    public static Client createSmartClient(SmartConfig smartConfig) {
        return createSmartClient(smartConfig, createApacheClientHandler(smartConfig));
    }

    public static Client createSmartClient(SmartConfig smartConfig,
                                           ClientHandler clientHandler) {
        Client client = createStandardClient(smartConfig, clientHandler);

        // inject SmartFilter (this is the Jersey integration point of the load balancer)
        client.addFilter(new SmartFilter(smartConfig));

        // set up polling for updated host list (if polling is disabled in smartConfig or there's no host list provider,
        // nothing will happen)
        PollingDaemon pollingDaemon = new PollingDaemon(smartConfig);
        pollingDaemon.start();

        // attach the daemon thread to the client so users can stop it when finished with the client
        client.getProperties().put(PollingDaemon.PROPERTY_KEY, pollingDaemon);

        return client;
    }

    /**
     * This creates a standard apache-based Jersey client, configured with a SmartConfig, but without any load balancing
     * or node polling.
     */
    public static Client createStandardClient(SmartConfig smartConfig) {
        return createStandardClient(smartConfig, createApacheClientHandler(smartConfig));
    }

    /**
     * This creates a standard apache-based Jersey client, configured with a SmartConfig, but without any load balancing
     * or node polling.
     */
    public static Client createStandardClient(SmartConfig smartConfig,
                                              ClientHandler clientHandler) {
        // init Jersey config
        ClientConfig clientConfig = new DefaultClientConfig();

        // pass in jersey parameters from calling code (allows customization of client)
        for (String propName : smartConfig.getProperties().keySet()) {
            clientConfig.getProperties().put(propName, smartConfig.getProperty(propName));
        }

        // replace sized writers with override writers to allow dynamic content-length (i.e. for transformations)
        clientConfig.getClasses().remove(ByteArrayProvider.class);
        clientConfig.getClasses().remove(FileProvider.class);
        clientConfig.getClasses().remove(InputStreamProvider.class);
        clientConfig.getClasses().add(SizeOverrideWriter.ByteArray.class);
        clientConfig.getClasses().add(SizeOverrideWriter.File.class);
        clientConfig.getClasses().add(SizeOverrideWriter.SizedInputStream.class);
        clientConfig.getClasses().add(SizeOverrideWriter.InputStream.class);
        clientConfig.getClasses().add(ByteArrayProvider.class);
        clientConfig.getClasses().add(FileProvider.class);
        clientConfig.getClasses().add(InputStreamProvider.class);

        // add support for XML with no content-type
        clientConfig.getClasses().add(OctetStreamXmlProvider.class);

        // add JSON support (using Jackson's ObjectMapper instead of JAXB marshalling)
        JacksonJaxbJsonProvider jsonProvider = new JacksonJaxbJsonProvider();
        // make sure we don't try to serialize any of these type hierarchies (clearly a bug in JacksonJsonProvider)
        jsonProvider.addUntouchable(InputStream.class);
        jsonProvider.addUntouchable(OutputStream.class);
        jsonProvider.addUntouchable(File.class);
        clientConfig.getSingletons().add(jsonProvider);

        // build Jersey client
        return new Client(clientHandler, clientConfig);
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
        PollingDaemon pollingDaemon = (PollingDaemon) client.getProperties().get(PollingDaemon.PROPERTY_KEY);
        if (pollingDaemon != null) {
            log.debug("terminating polling daemon");
            pollingDaemon.terminate();
            if (pollingDaemon.getSmartConfig().getHostListProvider() != null) {
                log.debug("destroying host list provider");
                pollingDaemon.getSmartConfig().getHostListProvider().destroy();
            }
        }
        log.debug("destroying Jersey client");
        client.destroy();
    }

    static ApacheHttpClient4Handler createApacheClientHandler(SmartConfig smartConfig) {
        ClientConfig clientConfig = new DefaultClientConfig();

        // set up multi-threaded connection pool
        // TODO: find a non-deprecated connection manager that works (swapping out with
        //       PoolingHttpClientConnectionManager will break threading)
        org.apache.http.impl.conn.PoolingClientConnectionManager connectionManager = new org.apache.http.impl.conn.PoolingClientConnectionManager();
        // 999 maximum active connections (max allowed)
        connectionManager.setDefaultMaxPerRoute(smartConfig.getIntProperty(MAX_CONNECTIONS_PER_HOST, MAX_CONNECTIONS_PER_HOST_DEFAULT));
        connectionManager.setMaxTotal(smartConfig.getIntProperty(MAX_CONNECTIONS, MAX_CONNECTIONS_DEFAULT));
        clientConfig.getProperties().put(ApacheHttpClient4Config.PROPERTY_CONNECTION_MANAGER, connectionManager);

        // set proxy config
        if (smartConfig.getProxyUri() != null)
            clientConfig.getProperties().put(ApacheHttpClient4Config.PROPERTY_PROXY_URI, smartConfig.getProxyUri());
        if (smartConfig.getProxyUser() != null)
            clientConfig.getProperties().put(ApacheHttpClient4Config.PROPERTY_PROXY_USERNAME, smartConfig.getProxyUser());
        if (smartConfig.getProxyPass() != null)
            clientConfig.getProperties().put(ApacheHttpClient4Config.PROPERTY_PROXY_PASSWORD, smartConfig.getProxyPass());

        // pass in jersey parameters from calling code (allows customization of client)
        for (String propName : smartConfig.getProperties().keySet()) {
            clientConfig.getProperties().put(propName, smartConfig.getProperty(propName));
        }

        ApacheHttpClient4Handler handler = ApacheHttpClient4.create(clientConfig).getClientHandler();

        // disable the retry handler if necessary
        if (smartConfig.getProperty(DISABLE_APACHE_RETRY) != null) {
            org.apache.http.impl.client.AbstractHttpClient httpClient = (org.apache.http.impl.client.AbstractHttpClient) handler.getHttpClient();
            httpClient.setHttpRequestRetryHandler(new org.apache.http.impl.client.DefaultHttpRequestRetryHandler(0, false));
        }

        return handler;
    }

    private SmartClientFactory() {
    }
}
