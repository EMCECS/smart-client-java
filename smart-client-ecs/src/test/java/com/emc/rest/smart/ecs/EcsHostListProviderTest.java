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
package com.emc.rest.smart.ecs;

import com.emc.rest.smart.Host;
import com.emc.rest.smart.SmartConfig;
import com.emc.util.TestConfig;
import javax.ws.rs.client.Client;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.apache.connector.ApacheClientProperties;
import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

public class EcsHostListProviderTest {
    public static final String S3_ENDPOINT = "s3.endpoint";
    public static final String S3_ACCESS_KEY = "s3.access_key";
    public static final String S3_SECRET_KEY = "s3.secret_key";

    public static final String PROXY_URI = "http.proxyUri";

    private URI serverURI;
    private Client client;
    private EcsHostListProvider hostListProvider;

    @BeforeEach
    public void before() throws Exception {
        Properties properties = TestConfig.getProperties();

        serverURI = new URI(TestConfig.getPropertyNotEmpty(properties, S3_ENDPOINT));
        String user = TestConfig.getPropertyNotEmpty(properties, S3_ACCESS_KEY);
        String secret = TestConfig.getPropertyNotEmpty(properties, S3_SECRET_KEY);
        String proxyUri = properties.getProperty(PROXY_URI);

        ClientConfig clientConfig = new ClientConfig();
        clientConfig.connectorProvider(new ApacheConnectorProvider());
        org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager connectionManager = 
            new org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager();
        clientConfig.property(ApacheClientProperties.CONNECTION_MANAGER, connectionManager);
        if (proxyUri != null) clientConfig.property(org.glassfish.jersey.client.ClientProperties.PROXY_URI, proxyUri);
        client = javax.ws.rs.client.ClientBuilder.newClient(clientConfig);

        SmartConfig smartConfig = new SmartConfig(serverURI.getHost());

        hostListProvider = new EcsHostListProvider(client, smartConfig.getLoadBalancer(), user, secret);
        hostListProvider.setProtocol(serverURI.getScheme());
        hostListProvider.setPort(serverURI.getPort());
    }

    @AfterEach
    public void after() {
        if (hostListProvider != null) hostListProvider.destroy();
    }

    @Test
    public void testEcsHostListProvider() {
        List<Host> hostList = hostListProvider.getHostList();

        assertTrue(hostList.size() > 0, "server list is empty");
    }

    // TODO: testNoKeepAlive needs to be reimplemented for Jersey 2.x/HttpClient 5.x
    // The internal API for accessing connection manager stats has changed significantly

    @Test
    public void testHealthCheck() {
        for (Host host : hostListProvider.getHostList()) {
            hostListProvider.runHealthCheck(host);
        }

        // test non-VDC host
        Host host = new Host(serverURI.getHost());
        hostListProvider.runHealthCheck(host);
        assertTrue(host.isHealthy());

        // test VDC host
        Vdc vdc = new Vdc(serverURI.getHost());
        VdcHost vdcHost = vdc.getHosts().get(0);
        hostListProvider.runHealthCheck(vdcHost);
        assertTrue(vdcHost.isHealthy());
        assertFalse(vdcHost.isMaintenanceMode());

        try {
            hostListProvider.runHealthCheck(new Host("localhost"));
            fail("health check against bad host should fail");
        } catch (Exception e) {
            // expected
        }
    }

    @Test
    public void testMaintenanceMode() {
        Vdc vdc = new Vdc("foo.com");
        VdcHost host = vdc.getHosts().get(0);

        // assert the host is healthy first
        assertTrue(host.isHealthy());

        // maintenance mode should make the host appear offline
        host.setMaintenanceMode(true);
        assertFalse(host.isHealthy());

        host.setMaintenanceMode(false);
        assertTrue(host.isHealthy());
    }

    @Test
    public void testPing() {
        String portStr = serverURI.getPort() > 0 ? ":" + serverURI.getPort() : "";

        PingResponse response = client.target(
                String.format("%s://%s%s/?ping", serverURI.getScheme(), serverURI.getHost(), portStr))
                .request().header("x-emc-namespace", "foo").get(PingResponse.class);
        assertNotNull(response);
        assertEquals(PingItem.Status.OFF, response.getPingItemMap().get(PingItem.MAINTENANCE_MODE).getStatus());
    }

    @Test
    public void testVdcs() {
        Vdc vdc1 = new Vdc(serverURI.getHost()).withName("vdc1");
        Vdc vdc2 = new Vdc(serverURI.getHost()).withName("vdc2");
        Vdc vdc3 = new Vdc(serverURI.getHost()).withName("vdc3");

        hostListProvider.withVdcs(vdc1, vdc2, vdc3);

        List<Host> hostList = hostListProvider.getHostList();

        assertTrue(hostList.size() >= 3, "server list should have at least 3 entries");
        assertTrue(vdc1.getHosts().size() > 0, "VDC1 server list is empty");
        assertTrue(vdc2.getHosts().size() > 0, "VDC2 server list is empty");
        assertTrue(vdc3.getHosts().size() > 0, "VDC3 server list is empty");
    }

    @Test
    public void testPingMarshalling() throws Exception {
        JAXBContext context = JAXBContext.newInstance(PingResponse.class);

        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<PingList xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">" +
                "<PingItem><Name>LOAD_FACTOR</Name><Value>1</Value></PingItem>" +
                "<PingItem><Name>MAINTENANCE_MODE</Name><Status>OFF</Status><Text>Data Node is Available</Text></PingItem>" +
                "</PingList>";

        PingResponse object = new PingResponse();
        Map<String, PingItem> map = new TreeMap<>();
        map.put("LOAD_FACTOR", new PingItem("LOAD_FACTOR", null, null, "1"));
        map.put("MAINTENANCE_MODE", new PingItem("MAINTENANCE_MODE", PingItem.Status.OFF, "Data Node is Available", null));
        object.setPingItemMap(map);

        // unmarshall and compare to object
        Unmarshaller unmarshaller = context.createUnmarshaller();
        PingResponse xObject = (PingResponse) unmarshaller.unmarshal(new StringReader(xml));

        assertEquals(object.getPingItemMap().keySet(), xObject.getPingItemMap().keySet());
        PingItem pingItem = object.getPingItems().get(0), xPingItem = xObject.getPingItems().get(0);
        assertEquals(pingItem.getName(), xPingItem.getName());
        assertEquals(pingItem.getStatus(), xPingItem.getStatus());
        assertEquals(pingItem.getText(), xPingItem.getText());
        assertEquals(pingItem.getValue(), xPingItem.getValue());
        pingItem = object.getPingItems().get(1);
        xPingItem = xObject.getPingItems().get(1);
        assertEquals(pingItem.getName(), xPingItem.getName());
        assertEquals(pingItem.getStatus(), xPingItem.getStatus());
        assertEquals(pingItem.getText(), xPingItem.getText());
        assertEquals(pingItem.getValue(), xPingItem.getValue());

        // marshall and compare XML
        Marshaller marshaller = context.createMarshaller();
        StringWriter writer = new StringWriter();
        marshaller.marshal(object, writer);

        assertEquals(xml, writer.toString());
    }
}
