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
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.glassfish.jersey.apache.connector.ApacheClientProperties;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;

import org.glassfish.jersey.client.JerseyClient;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.core.Response;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
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
    private JerseyClient client;
    private EcsHostListProvider hostListProvider;

    @Before
    public void before() throws Exception {
        Properties properties = TestConfig.getProperties();

        serverURI = new URI(TestConfig.getPropertyNotEmpty(properties, S3_ENDPOINT));
        String user = TestConfig.getPropertyNotEmpty(properties, S3_ACCESS_KEY);
        String secret = TestConfig.getPropertyNotEmpty(properties, S3_SECRET_KEY);
        String proxyUri = properties.getProperty(PROXY_URI);

        ClientConfig clientConfig = new ClientConfig();
        clientConfig.property(ApacheClientProperties.CONNECTION_MANAGER, new PoolingHttpClientConnectionManager());
        if (proxyUri != null) clientConfig.getProperties().put(ClientProperties.PROXY_URI, proxyUri);
        client = JerseyClientBuilder.createClient(clientConfig);

        SmartConfig smartConfig = new SmartConfig(serverURI.getHost());

        hostListProvider = new EcsHostListProvider(client, smartConfig.getLoadBalancer(), user, secret);
        hostListProvider.setProtocol(serverURI.getScheme());
        hostListProvider.setPort(serverURI.getPort());
    }

    @After
    public void after() {
        if (hostListProvider != null) hostListProvider.destroy();
    }

    @Test
    public void testEcsHostListProvider() {
        List<Host> hostList = hostListProvider.getHostList();

        Assert.assertTrue("server list is empty", hostList.size() > 0);
    }

    // intended to make sure we do not keep connections alive for any maintenance-related calls
    // to ensure that each client instance does not consume an active connection on each ECS node just for health-checks
    @Test
    public void testNoKeepAlive() {
        // verify client has no open connections
        PoolingHttpClientConnectionManager connectionManager = (PoolingHttpClientConnectionManager) client.getConfiguration().getProperty(ApacheClientProperties.CONNECTION_MANAGER);

        Assert.assertEquals(0, connectionManager.getTotalStats().getAvailable());
        Assert.assertEquals(0, connectionManager.getTotalStats().getLeased());
        Assert.assertEquals(0, connectionManager.getTotalStats().getPending());

        // ?endpoint call
        List<Host> hosts = hostListProvider.getHostList();

        // verify client still has no open connections
        Assert.assertEquals(0, connectionManager.getTotalStats().getAvailable());
        Assert.assertEquals(0, connectionManager.getTotalStats().getLeased());
        Assert.assertEquals(0, connectionManager.getTotalStats().getPending());

        // ?ping calls
        for (Host host : hosts) {
            hostListProvider.runHealthCheck(host);
        }

        // verify client still has no open connections
        Assert.assertEquals(0, connectionManager.getTotalStats().getAvailable());
        Assert.assertEquals(0, connectionManager.getTotalStats().getLeased());
        Assert.assertEquals(0, connectionManager.getTotalStats().getPending());
    }

    @Test
    public void testHealthCheck() {
        for (Host host : hostListProvider.getHostList()) {
            hostListProvider.runHealthCheck(host);
        }

        // test non-VDC host
        Host host = new Host(serverURI.getHost());
        hostListProvider.runHealthCheck(host);
        Assert.assertTrue(host.isHealthy());

        // test VDC host
        Vdc vdc = new Vdc(serverURI.getHost());
        VdcHost vdcHost = vdc.getHosts().get(0);
        hostListProvider.runHealthCheck(vdcHost);
        Assert.assertTrue(vdcHost.isHealthy());
        Assert.assertFalse(vdcHost.isMaintenanceMode());

        try {
            hostListProvider.runHealthCheck(new Host("localhost"));
            Assert.fail("health check against bad host should fail");
        } catch (Exception e) {
            // expected
        }
    }

    @Test
    public void testMaintenanceMode() {
        Vdc vdc = new Vdc("foo.com");
        VdcHost host = vdc.getHosts().get(0);

        // assert the host is healthy first
        Assert.assertTrue(host.isHealthy());

        // maintenance mode should make the host appear offline
        host.setMaintenanceMode(true);
        Assert.assertFalse(host.isHealthy());

        host.setMaintenanceMode(false);
        Assert.assertTrue(host.isHealthy());
    }

    @Test
    public void testPing() {
        String portStr = serverURI.getPort() > 0 ? ":" + serverURI.getPort() : "";
        WebTarget webTarget = client.target(String.format("%s://%s%s/?ping", serverURI.getScheme(), serverURI.getHost(), portStr));
        PingResponse response = webTarget.request().header("x-emc-namespace", "foo").get(PingResponse.class);
        Assert.assertNotNull(response);
        Assert.assertEquals(PingItem.Status.OFF, response.getPingItemMap().get(PingItem.MAINTENANCE_MODE).getStatus());
    }

    @Test
    public void testVdcs() {
        Vdc vdc1 = new Vdc(serverURI.getHost()).withName("vdc1");
        Vdc vdc2 = new Vdc(serverURI.getHost()).withName("vdc2");
        Vdc vdc3 = new Vdc(serverURI.getHost()).withName("vdc3");

        hostListProvider.withVdcs(vdc1, vdc2, vdc3);

        List<Host> hostList = hostListProvider.getHostList();

        Assert.assertTrue("server list should have at least 3 entries", hostList.size() >= 3);
        Assert.assertTrue("VDC1 server list is empty", vdc1.getHosts().size() > 0);
        Assert.assertTrue("VDC2 server list is empty", vdc2.getHosts().size() > 0);
        Assert.assertTrue("VDC3 server list is empty", vdc3.getHosts().size() > 0);
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

        Assert.assertEquals(object.getPingItemMap().keySet(), xObject.getPingItemMap().keySet());
        PingItem pingItem = object.getPingItems().get(0), xPingItem = xObject.getPingItems().get(0);
        Assert.assertEquals(pingItem.getName(), xPingItem.getName());
        Assert.assertEquals(pingItem.getStatus(), xPingItem.getStatus());
        Assert.assertEquals(pingItem.getText(), xPingItem.getText());
        Assert.assertEquals(pingItem.getValue(), xPingItem.getValue());
        pingItem = object.getPingItems().get(1);
        xPingItem = xObject.getPingItems().get(1);
        Assert.assertEquals(pingItem.getName(), xPingItem.getName());
        Assert.assertEquals(pingItem.getStatus(), xPingItem.getStatus());
        Assert.assertEquals(pingItem.getText(), xPingItem.getText());
        Assert.assertEquals(pingItem.getValue(), xPingItem.getValue());

        // marshall and compare XML
        Marshaller marshaller = context.createMarshaller();
        StringWriter writer = new StringWriter();
        marshaller.marshal(object, writer);

        Assert.assertEquals(xml, writer.toString());
    }
}
