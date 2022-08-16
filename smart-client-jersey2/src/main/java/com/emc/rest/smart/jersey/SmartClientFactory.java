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
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.glassfish.jersey.apache.connector.ApacheClientProperties;
import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import org.glassfish.jersey.message.internal.ByteArrayProvider;
import org.glassfish.jersey.message.internal.FileProvider;
import org.glassfish.jersey.message.internal.InputStreamProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

public final class SmartClientFactory {

    public static final String DISABLE_APACHE_RETRY = "com.emc.rest.smart.disableApacheRetry";
    private static final Logger log = LoggerFactory.getLogger(SmartClientFactory.class);

    private SmartClientFactory() {
    }

    public static Client createSmartClient(SmartConfig smartConfig) {
        return createSmartClient(smartConfig, createApacheClient(smartConfig));
    }

    public static Client createSmartClient(SmartConfig smartConfig, Client client) {
        Client smartClient = createStandardClient(smartConfig, client);

        // inject SmartFilter (this is the Jersey integration point of the load balancer)
        smartClient.register(new SmartFilter(smartConfig));

        // set up polling for updated host list (if polling is disabled in smartConfig or there's no host list provider,
        // nothing will happen)
        PollingDaemon pollingDaemon = new PollingDaemon(smartConfig);
        pollingDaemon.start();

        // attach the daemon thread to the client so users can stop it when finished with the client
        smartClient.property(PollingDaemon.PROPERTY_KEY, pollingDaemon);

        return smartClient;
    }

    /**
     * This creates a standard apache-based Jersey client, configured with a SmartConfig, but without any load balancing
     * or node polling.
     */
    public static Client createStandardClient(SmartConfig smartConfig) {
        return createStandardClient(smartConfig, createApacheClient(smartConfig));
    }

    /**
     * This creates a standard apache-based Jersey client, configured with a SmartConfig, but without any load balancing
     * or node polling.
     */
    public static Client createStandardClient(SmartConfig smartConfig, Client client) {
        // init Jersey config
        ClientConfig clientConfig = new ClientConfig();

        // pass in jersey parameters from calling code (allows customization of client)
        for (String propName : smartConfig.getProperties().keySet()) {
            clientConfig.property(propName, smartConfig.getProperty(propName));
        }

        // replace sized writers with override writers to allow dynamic content-length (i.e. for transformations)
        clientConfig.register(SizeOverrideWriter.ByteArray.class);
        clientConfig.register(SizeOverrideWriter.File.class);
        clientConfig.register(SizeOverrideWriter.SizedInputStream.class);
        clientConfig.register(SizeOverrideWriter.InputStream.class);
        clientConfig.register(ByteArrayProvider.class);
        clientConfig.register(FileProvider.class);
        clientConfig.register(InputStreamProvider.class);

        // add support for XML with no content-type
        clientConfig.register(OctetStreamXmlProvider.class);

        // add JSON support (using Jackson's ObjectMapper instead of JAXB marshalling)
        JacksonJaxbJsonProvider jsonProvider = new JacksonJaxbJsonProvider();
        // make sure we don't try to serialize any of these type hierarchies (clearly a bug in JacksonJsonProvider)
        jsonProvider.addUntouchable(InputStream.class);
        jsonProvider.addUntouchable(OutputStream.class);
        jsonProvider.addUntouchable(File.class);
        clientConfig.register(jsonProvider);

        client.register(clientConfig);

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
        PollingDaemon pollingDaemon = (PollingDaemon) client.getConfiguration().getProperty(PollingDaemon.PROPERTY_KEY);
        if (pollingDaemon != null) {
            log.debug("terminating polling daemon");
            pollingDaemon.terminate();
            if (pollingDaemon.getSmartConfig().getHostListProvider() != null) {
                log.debug("destroying host list provider");
                pollingDaemon.getSmartConfig().getHostListProvider().destroy();
            }
        }
        log.debug("destroying Jersey client");
        client.close();
    }

    static Client createApacheClient(SmartConfig smartConfig) {
        ClientConfig clientConfig = new ClientConfig().connectorProvider(new ApacheConnectorProvider());

        // set up multi-threaded connection pool
        // TODO: find a non-deprecated connection manager that works (swapping out with
        //       PoolingHttpClientConnectionManager will break threading)
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        // 999 maximum active connections (max allowed)
        connectionManager.setDefaultMaxPerRoute(999);
        connectionManager.setMaxTotal(999);
        clientConfig.property(ApacheClientProperties.CONNECTION_MANAGER, connectionManager);

        // set proxy config
        if (smartConfig.getProxyUri() != null)
            clientConfig.property(ClientProperties.PROXY_URI, smartConfig.getProxyUri());
        if (smartConfig.getProxyUser() != null)
            clientConfig.property(ClientProperties.PROXY_USERNAME, smartConfig.getProxyUser());
        if (smartConfig.getProxyPass() != null)
            clientConfig.property(ClientProperties.PROXY_PASSWORD, smartConfig.getProxyPass());

        // pass in jersey parameters from calling code (allows customization of client)
        for (String propName : smartConfig.getProperties().keySet()) {
            clientConfig.property(propName, smartConfig.getProperty(propName));
        }

        // disable the retry handler if necessary
        if (smartConfig.getProperty(DISABLE_APACHE_RETRY) != null) {
            clientConfig.property(ApacheClientProperties.RETRY_HANDLER, new DefaultHttpRequestRetryHandler(0, false));
        }

        return ClientBuilder.newClient(clientConfig);
    }
}
