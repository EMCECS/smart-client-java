/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.rest.smart;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandler;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.client.apache4.ApacheHttpClient4;
import com.sun.jersey.client.apache4.ApacheHttpClient4Handler;
import com.sun.jersey.client.apache4.config.ApacheHttpClient4Config;
import com.sun.jersey.core.impl.provider.entity.ByteArrayProvider;
import com.sun.jersey.core.impl.provider.entity.FileProvider;
import com.sun.jersey.core.impl.provider.entity.StringProvider;
import com.sun.jersey.core.impl.provider.entity.XMLRootElementProvider;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;

import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import java.util.List;

public final class SmartClientFactory {
    public static Client createSmartClient(SmartConfig smartConfig) {
        return createSmartClient(smartConfig, null, null);
    }

    public static Client createSmartClient(SmartConfig smartConfig,
                                           List<Class<MessageBodyReader<?>>> readers,
                                           List<Class<MessageBodyWriter<?>>> writers) {
        return createSmartClient(smartConfig, createApacheClientHandler(smartConfig), readers, writers);
    }

    public static Client createSmartClient(SmartConfig smartConfig,
                                           ClientHandler clientHandler,
                                           List<Class<MessageBodyReader<?>>> readers,
                                           List<Class<MessageBodyWriter<?>>> writers) {
        Client client = createStandardClient(smartConfig, clientHandler, readers, writers);

        // inject SmartFilter (this is the Jersey integration point of the load balancer)
        client.addFilter(new SmartFilter(smartConfig.getLoadBalancer()));

        return client;
    }

    /**
     * This creates a standard apache-based Jersey client, configured with a SmartConfig, but without any load balancing
     * or node polling.
     */
    public static Client createStandardClient(SmartConfig smartConfig) {
        return createStandardClient(smartConfig, null, null);
    }

    /**
     * This creates a standard apache-based Jersey client, configured with a SmartConfig, but without any load balancing
     * or node polling.
     */
    public static Client createStandardClient(SmartConfig smartConfig,
                                              List<Class<MessageBodyReader<?>>> readers,
                                              List<Class<MessageBodyWriter<?>>> writers) {
        return createStandardClient(smartConfig, createApacheClientHandler(smartConfig), readers, writers);
    }

    /**
     * This creates a standard apache-based Jersey client, configured with a SmartConfig, but without any load balancing
     * or node polling.
     */
    public static Client createStandardClient(SmartConfig smartConfig,
                                              ClientHandler clientHandler,
                                              List<Class<MessageBodyReader<?>>> readers,
                                              List<Class<MessageBodyWriter<?>>> writers) {
        // init Jersey config
        ClientConfig clientConfig = new DefaultClientConfig();

        // pass in jersey parameters from calling code (allows customization of client)
        for (String propName : smartConfig.getProperties().keySet()) {
            clientConfig.getProperties().put(propName, smartConfig.property(propName));
        }

        // add entity handlers
        addHandlers(clientConfig, readers, writers);

        // build Jersey client
        Client client = new Client(clientHandler, clientConfig);

        // TODO: do we need a custom retry handler?

        // set up polling for updated host list (if polling is disabled in smartConfig or there's no host list provider,
        // nothing will happen)
        PollingDaemon pollingDaemon = new PollingDaemon(smartConfig);
        pollingDaemon.start();

        return client;
    }

    static ApacheHttpClient4Handler createApacheClientHandler(SmartConfig smartConfig) {
        ClientConfig clientConfig = new DefaultClientConfig();

        // set up multi-threaded connection pool
        ThreadSafeClientConnManager connectionManager = new ThreadSafeClientConnManager();
        // 200 maximum active connections (should be more than enough for any JVM instance)
        connectionManager.setDefaultMaxPerRoute(200);
        connectionManager.setMaxTotal(200);
        clientConfig.getProperties().put(ApacheHttpClient4Config.PROPERTY_CONNECTION_MANAGER, connectionManager);

        // set proxy config
        if (smartConfig.getProxyUri() != null)
            clientConfig.getProperties().put(ApacheHttpClient4Config.PROPERTY_PROXY_URI, smartConfig.getProxyUri());
        if (smartConfig.getProxyUser() != null)
            clientConfig.getProperties().put(ApacheHttpClient4Config.PROPERTY_PROXY_USERNAME, smartConfig.getProxyUser());
        if (smartConfig.getProxyPass() != null)
            clientConfig.getProperties().put(ApacheHttpClient4Config.PROPERTY_PROXY_PASSWORD, smartConfig.getProxyPass());

        return ApacheHttpClient4.create(clientConfig).getClientHandler();
    }

    static void addHandlers(ClientConfig clientConfig,
                            List<Class<MessageBodyReader<?>>> readers,
                            List<Class<MessageBodyWriter<?>>> writers) {
        // add our message body handlers
        clientConfig.getClasses().clear();

        // custom types and buffered writers to ensure content-length is set
        clientConfig.getClasses().add(MeasuredStringWriter.class);
        clientConfig.getClasses().add(MeasuredJaxbWriter.App.class);
        clientConfig.getClasses().add(MeasuredJaxbWriter.Text.class);
        clientConfig.getClasses().add(MeasuredJaxbWriter.General.class);
        clientConfig.getClasses().add(SizedInputStreamWriter.class);

        // Jersey providers for types we support
        clientConfig.getClasses().add(ByteArrayProvider.class);
        clientConfig.getClasses().add(FileProvider.class);
        clientConfig.getClasses().add(StringProvider.class);
        clientConfig.getClasses().add(XMLRootElementProvider.App.class);
        clientConfig.getClasses().add(XMLRootElementProvider.Text.class);
        clientConfig.getClasses().add(XMLRootElementProvider.General.class);

        // user-defined types
        if (readers != null) {
            for (Class<MessageBodyReader<?>> reader : readers) {
                clientConfig.getClasses().add(reader);
            }
        }
        if (writers != null) {
            for (Class<MessageBodyWriter<?>> writer : writers) {
                clientConfig.getClasses().add(writer);
            }
        }
    }

    private SmartClientFactory() {
    }
}
