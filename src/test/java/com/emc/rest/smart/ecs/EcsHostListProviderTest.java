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
package com.emc.rest.smart.ecs;

import com.emc.rest.smart.Host;
import com.emc.rest.smart.SmartConfig;
import com.emc.util.TestConfig;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.client.apache4.ApacheHttpClient4;
import com.sun.jersey.client.apache4.config.ApacheHttpClient4Config;
import org.junit.Assert;
import org.junit.Test;

import java.net.URI;
import java.util.List;
import java.util.Properties;

public class EcsHostListProviderTest {
    public static final String S3_ENDPOINT = "s3.endpoint";
    public static final String S3_ACCESS_KEY = "s3.access_key";
    public static final String S3_SECRET_KEY = "s3.secret_key";

    public static final String PROXY_URI = "http.proxyUri";

    @Test
    public void testEcsHostListProvider() throws Exception {
        Properties properties = TestConfig.getProperties();

        URI serverURI = new URI(TestConfig.getPropertyNotEmpty(properties, S3_ENDPOINT));
        String user = TestConfig.getPropertyNotEmpty(properties, S3_ACCESS_KEY);
        String secret = TestConfig.getPropertyNotEmpty(properties, S3_SECRET_KEY);
        String proxyUri = properties.getProperty(PROXY_URI);

        ClientConfig clientConfig = new DefaultClientConfig();
        if (proxyUri != null) clientConfig.getProperties().put(ApacheHttpClient4Config.PROPERTY_PROXY_URI, proxyUri);
        Client client = ApacheHttpClient4.create(clientConfig);

        SmartConfig smartConfig = new SmartConfig(serverURI.getHost());

        EcsHostListProvider hostListProvider = new EcsHostListProvider(client, smartConfig.getLoadBalancer(), user, secret);
        hostListProvider.setProtocol(serverURI.getScheme());
        hostListProvider.setPort(serverURI.getPort());

        List<Host> hostList = hostListProvider.getHostList();

        Assert.assertTrue("server list is empty", hostList.size() > 0);
    }

    @Test
    public void testHealthCheck() throws Exception {
        Properties properties = TestConfig.getProperties();

        URI serverURI = new URI(TestConfig.getPropertyNotEmpty(properties, S3_ENDPOINT));
        String user = TestConfig.getPropertyNotEmpty(properties, S3_ACCESS_KEY);
        String secret = TestConfig.getPropertyNotEmpty(properties, S3_SECRET_KEY);
        String proxyUri = properties.getProperty(PROXY_URI);

        ClientConfig clientConfig = new DefaultClientConfig();
        if (proxyUri != null) clientConfig.getProperties().put(ApacheHttpClient4Config.PROPERTY_PROXY_URI, proxyUri);
        Client client = ApacheHttpClient4.create(clientConfig);

        SmartConfig smartConfig = new SmartConfig(serverURI.getHost());

        EcsHostListProvider hostListProvider = new EcsHostListProvider(client, smartConfig.getLoadBalancer(), user, secret);
        hostListProvider.setProtocol(serverURI.getScheme());
        hostListProvider.setPort(serverURI.getPort());

        for (Host host : hostListProvider.getHostList()) {
            hostListProvider.runHealthCheck(host);
        }

        try {
            hostListProvider.runHealthCheck(new Host("localhost"));
            Assert.fail("health check against bad host should fail");
        } catch (Exception e) {
            // expected
        }
    }

    @Test
    public void testVdcs() throws Exception {
        Properties properties = TestConfig.getProperties();

        URI serverURI = new URI(TestConfig.getPropertyNotEmpty(properties, S3_ENDPOINT));
        String user = TestConfig.getPropertyNotEmpty(properties, S3_ACCESS_KEY);
        String secret = TestConfig.getPropertyNotEmpty(properties, S3_SECRET_KEY);
        String proxyUri = properties.getProperty(PROXY_URI);

        ClientConfig clientConfig = new DefaultClientConfig();
        if (proxyUri != null) clientConfig.getProperties().put(ApacheHttpClient4Config.PROPERTY_PROXY_URI, proxyUri);
        Client client = ApacheHttpClient4.create(clientConfig);

        SmartConfig smartConfig = new SmartConfig(serverURI.getHost());

        EcsHostListProvider hostListProvider = new EcsHostListProvider(client, smartConfig.getLoadBalancer(), user, secret);
        hostListProvider.setProtocol(serverURI.getScheme());
        hostListProvider.setPort(serverURI.getPort());

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
}
