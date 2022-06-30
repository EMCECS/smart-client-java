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
package com.emc.rest.smart;

import com.emc.rest.smart.jersey.SmartClientFactory;
import com.emc.util.TestConfig;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SmartClientTest {
    private static final Logger l4j = LogManager.getLogger(SmartClientTest.class);

    public static final String PROP_ATMOS_ENDPOINTS = "atmos.endpoints";
    public static final String PROP_ATMOS_UID = "atmos.uid";
    public static final String PROP_ATMOS_SECRET = "atmos.secret";

    private static final String HEADER_FORMAT = "EEE, d MMM yyyy HH:mm:ss z";
    private static final ThreadLocal<DateFormat> headerFormat = new ThreadLocal<>();

    @Test
    public void testAtmosOnEcs() throws Exception {
        Properties testProperties = null;
        try {
            testProperties = TestConfig.getProperties();
        } catch (Exception e) {
            Assume.assumeTrue(TestConfig.DEFAULT_PROJECT_NAME + " properties missing (look in src/test/resources for template)", false);
        }
        String endpointStr = TestConfig.getPropertyNotEmpty(testProperties, PROP_ATMOS_ENDPOINTS);
        final String uid = TestConfig.getPropertyNotEmpty(testProperties, PROP_ATMOS_UID);
        final String secret = TestConfig.getPropertyNotEmpty(testProperties, PROP_ATMOS_SECRET);

        String[] endpoints = endpointStr.split(",");
        final URI serverUri = new URI(endpointStr.split(",")[0]);

        List<Host> initialHosts = new ArrayList<>();
        for (String endpoint : endpoints) {
            initialHosts.add(new Host(new URI(endpoint).getHost()));
        }

        SmartConfig smartConfig = new SmartConfig(initialHosts);
        final Client client = SmartClientFactory.createSmartClient(smartConfig);

        ExecutorService service = Executors.newFixedThreadPool(10);

        final AtomicInteger successCount = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            futures.add(service.submit(() -> {
                getServiceInfo(client, serverUri, uid, secret);
                successCount.incrementAndGet();
            }));
        }

        // wait for all threads to complete
        for (Future<?> future : futures) {
            future.get();
        }

        l4j.info(Arrays.toString(smartConfig.getLoadBalancer().getHostStats()));

        Assert.assertEquals("at least one task failed", 100, successCount.intValue());
    }

    @Test
    public void testPutJsonStream() throws Exception {
        String endpointStr = TestConfig.getPropertyNotEmpty(PROP_ATMOS_ENDPOINTS);
        String[] endpoints = endpointStr.split(",");
        List<Host> initialHosts = new ArrayList<>();
        for (String endpoint : endpoints) {
            initialHosts.add(new Host(new URI(endpoint).getHost()));
        }
        byte[] data = "JSON Stream Test".getBytes();

        SmartConfig smartConfig = new SmartConfig(initialHosts);
        Client client = SmartClientFactory.createSmartClient(smartConfig);

        // this is an illegal use of this resource, but we just want to make sure the request is sent
        // (no exception when finding a MessageBodyWriter)
        WebTarget webTarget = client.target(endpoints[0]).path("/rest/namespace/foo");
        Invocation.Builder invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON);
        Response response = invocationBuilder.post(Entity.json(data));

        Assert.assertTrue(response.getStatus() > 299); // some versions of ECS return 500 instead of 403
    }

    @Test
    public void testConnTimeout() throws Exception {
        int CONNECTION_TIMEOUT_MILLIS = 10000; // 10 seconds

        HttpParams httpParams = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(httpParams, CONNECTION_TIMEOUT_MILLIS);

        SmartConfig smartConfig = new SmartConfig("8.8.4.4:9020");
//        smartConfig.setProperty(ApacheHttpClient4Config.PROPERTY_HTTP_PARAMS, httpParams);

        final Client client = SmartClientFactory.createStandardClient(smartConfig);

        Future<?> future = Executors.newSingleThreadExecutor().submit(() -> {
            client.target("http://8.8.4.4:9020/?ping").request().get(String.class);
            Assert.fail("response was not expected; choose an IP that is not in use");
        });

        try {
            future.get(CONNECTION_TIMEOUT_MILLIS + 1000, TimeUnit.MILLISECONDS); // give an extra second leeway
        } catch (TimeoutException e) {
            Assert.fail("connection did not timeout");
        } catch (ExecutionException e) {
//            Assert.assertTrue(e.getCause() instanceof ClientHandlerException);
            Assert.assertTrue(e.getMessage().contains("timed out"));
        }
    }

    private void getServiceInfo(Client client, URI serverUri, String uid, String secretKey) {
        String path = "/rest/service";
        String date = getDateFormat().format(new Date());

        String signature = sign("GET\n\n\n" + date + "\n" + path + "\nx-emc-date:" + date + "\nx-emc-uid:" + uid, secretKey);

        WebTarget webTarget = client.target(serverUri).path(path);
        Invocation invocation = webTarget.request("text/plain")
                .header("Date", date)
                .header("x-emc-date", date)
                .header("x-emc-uid", uid)
                .header("x-emc-signature", signature)
                .buildGet();

        Response response = invocation.invoke(Response.class);

        if (response.getStatus() > 299) throw new RuntimeException("error response: " + response.getStatus());

        String responseStr = (String) response.getEntity();
        if (!responseStr.contains("Atmos")) throw new RuntimeException("unrecognized response string: " + responseStr);
    }

    private String sign(String canonicalString, String secretKey) {
        try {
            byte[] hashKey = Base64.decodeBase64(secretKey.getBytes(StandardCharsets.UTF_8));
            byte[] input = canonicalString.getBytes(StandardCharsets.UTF_8);

            Mac mac = Mac.getInstance("HmacSHA1");
            SecretKeySpec key = new SecretKeySpec(hashKey, "HmacSHA1");
            mac.init(key);

            byte[] hashBytes = mac.doFinal(input);

            return new String(Base64.encodeBase64(hashBytes), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Error signing string:\n" + canonicalString + "\n", e);
        }
    }

    private DateFormat getDateFormat() {
        DateFormat format = headerFormat.get();
        if (format == null) {
            format = new SimpleDateFormat(HEADER_FORMAT, Locale.ENGLISH);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            headerFormat.set(format);
        }
        return format;
    }
}
