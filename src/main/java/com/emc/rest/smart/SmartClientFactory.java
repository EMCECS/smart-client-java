/*
 * Copyright (c) 2015, EMC Corporation.
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *     + Redistributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *     + Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     + The name of EMC Corporation may not be used to endorse or promote
 *       products derived from this software without specific prior written
 *       permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
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
import com.sun.jersey.core.impl.provider.entity.InputStreamProvider;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;

public final class SmartClientFactory {
    public static Client createSmartClient(SmartConfig smartConfig) {
        return createSmartClient(smartConfig, createApacheClientHandler(smartConfig));
    }

    public static Client createSmartClient(SmartConfig smartConfig,
                                           ClientHandler clientHandler) {
        Client client = createStandardClient(smartConfig, clientHandler);

        // inject SmartFilter (this is the Jersey integration point of the load balancer)
        client.addFilter(new SmartFilter(smartConfig.getLoadBalancer()));

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
            clientConfig.getProperties().put(propName, smartConfig.property(propName));
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

        // build Jersey client
        Client client = new Client(clientHandler, clientConfig);

        // TODO: do we need a custom retry handler?

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

    private SmartClientFactory() {
    }
}
