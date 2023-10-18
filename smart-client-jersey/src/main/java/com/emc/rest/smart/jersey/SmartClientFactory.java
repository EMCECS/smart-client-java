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
import com.fasterxml.jackson.jaxrs.xml.JacksonJaxbXMLProvider;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.glassfish.jersey.apache.connector.ApacheClientProperties;
import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.client.*;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import org.glassfish.jersey.message.internal.ByteArrayProvider;
import org.glassfish.jersey.message.internal.FileProvider;
import org.glassfish.jersey.message.internal.InputStreamProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Configuration;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.emc.rest.smart.jersey.SmartFilter.PRIORITY_SMART;

public final class SmartClientFactory {

    private static final Logger log = LoggerFactory.getLogger(SmartClientFactory.class);

    public static final String CONNECTOR_PROVIDER = "com.emc.rest.smart.connector";
    public static final String DISABLE_APACHE_RETRY = "com.emc.rest.smart.disableApacheRetry";
    public static final String MAX_CONNECTIONS = "com.emc.rest.smart.apacheMaxConnections";
    public static final String MAX_CONNECTIONS_PER_HOST = "com.emc.rest.smart.apacheMaxConnectionsPerHost";
    public static final int MAX_CONNECTIONS_DEFAULT = 999;
    public static final int MAX_CONNECTIONS_PER_HOST_DEFAULT = 999;

    public static final String IDLE_CONNECTION_MONITOR_PROPERTY_KEY = "com.emc.rest.smart.idleConnectionsExecSvc";

    public static final String APACHE_TRANSPORT_CONNECTOR = "APACHE";
    public static final String HTTPURLCONNECTION_TRANSPORT_CONNECTOR = "HTTPURLCONNECTION";

    public static JerseyClient createSmartClient(SmartConfig smartConfig, JerseyClient client) {

        // If you register a second ClientConfig object on the same Client, it will overwrite the first config,
        // and the providers that were registered on the first config will be lost.
        ClientConfig clientConfig = new ClientConfig();
        for (String propName : smartConfig.getProperties().keySet())
            clientConfig.property(propName, smartConfig.getProperty(propName));
        // not only smartConfig properties, we also need involve ClientConfig properties into th new Client.
        // By now we tend to have a Client by JerseyClientBuilder.createClient and many copies.
        if (client != null) {
            for (String propName : client.getConfiguration().getProperties().keySet())
                clientConfig.property(propName, client.getConfiguration().getProperty(propName));
        }

        // inject SmartFilter (this is the Jersey integration point of the load balancer)
        clientConfig.register(new SmartFilter(smartConfig), PRIORITY_SMART);

        // set up polling for updated host list (if polling is disabled in smartConfig or there's no host list provider,
        // nothing will happen)
        PollingDaemon pollingDaemon = new PollingDaemon(smartConfig);
        pollingDaemon.start();

        // attach the daemon thread to the client so users can stop it when finished with the client
        clientConfig.property(PollingDaemon.PROPERTY_KEY, pollingDaemon);

        if (smartConfig.getProperty(CONNECTOR_PROVIDER) != null && smartConfig.getProperty(CONNECTOR_PROVIDER).equals("APACHE"))
            clientConfig.connectorProvider(new ApacheConnectorProvider());

        return JerseyClientBuilder.createClient(clientConfig);
    }

    /**
     * This creates a standard apache-based Jersey client, configured with a SmartConfig, but without any load balancing
     * or node polling.
     */
    public static JerseyClient createStandardClient(SmartConfig smartConfig) {
        return createStandardClient(smartConfig, APACHE_TRANSPORT_CONNECTOR);
    }

    /**
     * This creates a standard apache-based Jersey client, configured with a SmartConfig, but without any load balancing
     * or node polling.
     */
    public static JerseyClient createStandardClient(SmartConfig smartConfig,
                                                    String clientTransportConnector) {

        JerseyClient client = JerseyClientBuilder.createClient();

        // register connection provider onto client
        if (clientTransportConnector == null || clientTransportConnector.equals(APACHE_TRANSPORT_CONNECTOR))
            client = createApacheClient(smartConfig);
        else if (clientTransportConnector.equals(HTTPURLCONNECTION_TRANSPORT_CONNECTOR)) {
            client = JerseyClientBuilder.createClient();
        }

        // pass in jersey parameters from calling code (allows customization of client)
        for (String propName : smartConfig.getProperties().keySet()) {
            client.property(propName, smartConfig.getProperty(propName));
        }

        // replace sized writers with override writers to allow dynamic content-length (i.e. for transformations)
        client.register(SizeOverrideWriter.ByteArray.class);
        client.register(SizeOverrideWriter.File.class);
        client.register(SizeOverrideWriter.SizedInputStream.class);
        client.register(SizeOverrideWriter.InputStream.class);
        client.register(ByteArrayProvider.class);
        client.register(FileProvider.class);
        client.register(InputStreamProvider.class);

        // add support for XML with no content-type
        client.register(OctetStreamXmlProvider.class);
        // add JSON support (using Jackson's ObjectMapper instead of JAXB marshalling)
        JacksonJaxbJsonProvider jsonProvider = new JacksonJaxbJsonProvider();
        // make sure we don't try to serialize any of these type hierarchies (clearly a bug in JacksonJsonProvider)
        jsonProvider.addUntouchable(InputStream.class);
        jsonProvider.addUntouchable(OutputStream.class);
        jsonProvider.addUntouchable(File.class);
        client.register(jsonProvider);
        client.register(JacksonJaxbXMLProvider.class);

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
    public static void destroy(JerseyClient client) {
        Configuration config = client.getConfiguration();
        PollingDaemon pollingDaemon = (PollingDaemon) config.getProperty(PollingDaemon.PROPERTY_KEY);
        if (pollingDaemon != null) {
            log.debug("terminating polling daemon");
            pollingDaemon.terminate();
            if (pollingDaemon.getSmartConfig().getHostListProvider() != null) {
                log.debug("destroying host list provider");
                pollingDaemon.getSmartConfig().getHostListProvider().destroy();
            }
        }

        ScheduledExecutorService sched = (ScheduledExecutorService)config.getProperty(IDLE_CONNECTION_MONITOR_PROPERTY_KEY);
        if (sched != null) {
            log.debug("shutting down scheduled idle connections monitoring task");
            sched.shutdownNow();
        }

        MyPoolingHttpClientConnectionManager cm = (MyPoolingHttpClientConnectionManager)config.getProperty(ApacheClientProperties.CONNECTION_MANAGER);
        if (cm != null) {
            log.debug("shutting down the connection manager");
            cm.realShutDown();
        }

        // closing the client would also shut down the connection manager
        log.info("destroying Jersey client");
        client.close();
    }

    static JerseyClient createApacheClient(SmartConfig smartConfig) {
        smartConfig.setProperty(CONNECTOR_PROVIDER, APACHE_TRANSPORT_CONNECTOR);

        ClientConfig clientConfig = new ClientConfig();

        // set up multi-threaded connection pool
        MyPoolingHttpClientConnectionManager connectionManager = new MyPoolingHttpClientConnectionManager();
        connectionManager.setDefaultMaxPerRoute(smartConfig.getIntProperty(MAX_CONNECTIONS_PER_HOST, MAX_CONNECTIONS_PER_HOST_DEFAULT));
        connectionManager.setMaxTotal(smartConfig.getIntProperty(MAX_CONNECTIONS, MAX_CONNECTIONS_DEFAULT));

        if (smartConfig.getMaxConnectionIdleTime() > 0) {
            ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor();
            sched.scheduleWithFixedDelay(() ->
                    connectionManager.closeIdleConnections(smartConfig.getMaxConnectionIdleTime(), TimeUnit.SECONDS), 0, 60, TimeUnit.SECONDS);
            smartConfig.setProperty(IDLE_CONNECTION_MONITOR_PROPERTY_KEY, sched);
        }

        clientConfig.property(ApacheClientProperties.CONNECTION_MANAGER, connectionManager);
        clientConfig.connectorProvider(new ApacheConnectorProvider());

        // set proxy config
        if (smartConfig.getProxyUri() != null)
            clientConfig.property(ClientProperties.PROXY_URI, smartConfig.getProxyUri());
        if (smartConfig.getProxyUser() != null)
            clientConfig.property(ClientProperties.PROXY_USERNAME, smartConfig.getProxyUser());
        if (smartConfig.getProxyPass() != null)
            clientConfig.property(ClientProperties.PROXY_PASSWORD, smartConfig.getProxyPass());

        // disable the retry handler if necessary
        if (smartConfig.getProperty(DISABLE_APACHE_RETRY) != null) {
            clientConfig.property(ApacheClientProperties.RETRY_HANDLER, new DefaultHttpRequestRetryHandler(0, false));
        }

        return JerseyClientBuilder.createClient(clientConfig);
    }

    private SmartClientFactory() {
    }
}
