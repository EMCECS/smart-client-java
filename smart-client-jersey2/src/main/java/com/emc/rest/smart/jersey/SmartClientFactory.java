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
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.glassfish.jersey.apache.connector.ApacheClientProperties;
import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.apache.connector.ApacheHttpClientBuilderConfigurator;
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

    private static final Logger log = LoggerFactory.getLogger(SmartClientFactory.class);

    public static final String DISABLE_APACHE_RETRY = "com.emc.rest.smart.disableApacheRetry";

    public static Client createSmartClient(SmartConfig smartConfig) {
        Client client = createStandardClient(smartConfig);

        // inject SmartFilter (this is the Jersey integration point of the load balancer)
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
        // init Jersey config
        ClientConfig clientConfig = new ClientConfig();

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
        clientConfig.register(jsonProvider);

        // Note, that internally the HttpUrlConnection instances are pooled, so (un)setting the property after already creating a target typically does not have any effect.
        // The property influences all the connections created after the property has been (un)set,
        // but there is no guarantee, that your request will use a connection created after the property change.
        //In a simple environment, setting the property before creating the first target is sufficient,
        // but in complex environments (such as application servers), where some poolable connections might exist before your application even bootstraps,
        // this approach is not 100% reliable and we recommend using a different client transport connector, such as Apache Connector.
        // These limitations have to be considered especially when invoking CORS (Cross Origin Resource Sharing) requests.
        clientConfig.connectorProvider(new ApacheConnectorProvider());
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
        log.debug("destroying Jersey client");
        client.close();
    }

    // TODO: apache configuration
    static ClientConfig createApacheClientHandler(SmartConfig smartConfig) {
        ClientConfig clientConfig = new ClientConfig();

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
            clientConfig.getProperties().put(ClientProperties.PROXY_URI, smartConfig.getProxyUri());
        if (smartConfig.getProxyUser() != null)
            clientConfig.getProperties().put(ClientProperties.PROXY_USERNAME, smartConfig.getProxyUser());
        if (smartConfig.getProxyPass() != null)
            clientConfig.getProperties().put(ClientProperties.PROXY_PASSWORD, smartConfig.getProxyPass());

        // pass in jersey parameters from calling code (allows customization of client)
        for (String propName : smartConfig.getProperties().keySet()) {
            clientConfig.getProperties().put(propName, smartConfig.getProperty(propName));
        }

        // TODO
        clientConfig.register((ApacheHttpClientBuilderConfigurator) httpClientBuilder -> null);

        // disable the retry handler if necessary
//        if (smartConfig.getProperty(DISABLE_APACHE_RETRY) != null) {
//            org.apache.http.impl.client.AbstractHttpClient httpClient = (org.apache.http.impl.client.AbstractHttpClient) handler.getHttpClient();
//            httpClient.setHttpRequestRetryHandler(new org.apache.http.impl.client.DefaultHttpRequestRetryHandler(0, false));
//        }

        return clientConfig;
    }

    private SmartClientFactory() {
    }
}
