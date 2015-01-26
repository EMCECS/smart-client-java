package com.emc.rest.smart;

import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.glassfish.jersey.apache.connector.ApacheClientProperties;
import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.RequestEntityProcessing;
import org.glassfish.jersey.client.spi.ConnectorProvider;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

public final class SmartClientFactory {
    public static Client createSmartClient(SmartConfig smartConfig) {
        return createSmartClient(smartConfig, new ApacheConnectorProvider());
    }

    public static Client createSmartClient(SmartConfig smartConfig, ConnectorProvider baseConnectorProvider) {
        // init Jersey config
        ClientConfig clientConfig = new ClientConfig();

        // set up multi-threaded connection pool
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        // 200 maximum active connections (should be more than enough for any JVM instance)
        connectionManager.setDefaultMaxPerRoute(200);
        connectionManager.setMaxTotal(200);
        clientConfig.property(ApacheClientProperties.CONNECTION_MANAGER, connectionManager);

        // enable request buffering to ensure content-length is always set
        clientConfig.property(ClientProperties.REQUEST_ENTITY_PROCESSING, RequestEntityProcessing.BUFFERED);
        clientConfig.property(ClientProperties.OUTBOUND_CONTENT_LENGTH_BUFFER, 32768); // 32k entity buffer

        // pass in jersey parameters from calling code (allows customization of client)
        for (String propName : smartConfig.getProperties().keySet()) {
            clientConfig.property(propName, smartConfig.property(propName));
        }

        // inject SmartConnector provider (this is the Jersey integration point of the load balancer)
        clientConfig.connectorProvider(new SmartConnectorProvider(baseConnectorProvider, smartConfig));

        // set up polling for updated host list (if polling is disabled in smartConfig or there's no host list provider,
        // nothing will happen)
        PollingDaemon pollingDaemon = new PollingDaemon(smartConfig);
        pollingDaemon.start();

        // build Jersey client
        return ClientBuilder.newClient(clientConfig);
    }

    private SmartClientFactory() {
    }
}
