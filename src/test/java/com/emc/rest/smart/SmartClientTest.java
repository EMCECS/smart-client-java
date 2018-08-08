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

import com.emc.util.TestConfig;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.client.apache4.config.ApacheHttpClient4Config;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SmartClientTest {
    private static final Logger l4j = Logger.getLogger(SmartClientTest.class);

    public static final String PROP_ATMOS_ENDPOINTS = "atmos.endpoints";
    public static final String PROP_ATMOS_UID = "atmos.uid";
    public static final String PROP_ATMOS_SECRET = "atmos.secret";

    private static final String HEADER_FORMAT = "EEE, d MMM yyyy HH:mm:ss z";
    private static final ThreadLocal<DateFormat> headerFormat = new ThreadLocal<DateFormat>();

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

        List<Host> initialHosts = new ArrayList<Host>();
        for (String endpoint : endpoints) {
            initialHosts.add(new Host(new URI(endpoint).getHost()));
        }

        SmartConfig smartConfig = new SmartConfig(initialHosts);
        final Client client = SmartClientFactory.createSmartClient(smartConfig);

        ExecutorService service = Executors.newFixedThreadPool(10);

        final AtomicInteger successCount = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<Future<?>>();

        for (int i = 0; i < 100; i++) {
            futures.add(service.submit(new Runnable() {
                @Override
                public void run() {
                    getServiceInfo(client, serverUri, uid, secret);
                    successCount.incrementAndGet();
                }
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
        List<Host> initialHosts = new ArrayList<Host>();
        for (String endpoint : endpoints) {
            initialHosts.add(new Host(new URI(endpoint).getHost()));
        }
        byte[] data = "JSON Stream Test".getBytes();

        SmartConfig smartConfig = new SmartConfig(initialHosts);
        Client client = SmartClientFactory.createSmartClient(smartConfig);

        // this is an illegal use of this resource, but we just want to make sure the request is sent
        // (no exception when finding a MessageBodyWriter)
        ClientResponse response = client.resource(endpoints[0]).path("/rest/service").type("application/json")
                 .put(ClientResponse.class, new ByteArrayInputStream(data));

        Assert.assertEquals(403, response.getStatus());
    }

    @Test
    public void testConnTimeout() throws Exception {
        int CONNECTION_TIMEOUT_MILLIS = 10000; // 10 seconds

        HttpParams httpParams = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(httpParams, CONNECTION_TIMEOUT_MILLIS);

        SmartConfig smartConfig = new SmartConfig("8.8.4.4:9020");
        smartConfig.setProperty(ApacheHttpClient4Config.PROPERTY_HTTP_PARAMS, httpParams);

        final Client client = SmartClientFactory.createStandardClient(smartConfig);

        Future future = Executors.newSingleThreadExecutor().submit(new Runnable() {
            @Override
            public void run() {
                client.resource("http://8.8.4.4:9020/?ping").get(String.class);
                Assert.fail("response was not expected; choose an IP that is not in use");
            }
        });

        try {
            future.get(CONNECTION_TIMEOUT_MILLIS + 1000, TimeUnit.MILLISECONDS); // give an extra second leeway
        } catch (TimeoutException e) {
            Assert.fail("connection did not timeout");
        } catch (ExecutionException e) {
            Assert.assertTrue(e.getCause() instanceof ClientHandlerException);
            Assert.assertTrue(e.getMessage().contains("timed out"));
        }
    }

    private void getServiceInfo(Client client, URI serverUri, String uid, String secretKey) {
        String path = "/rest/service";
        String date = getDateFormat().format(new Date());

        String signature = sign("GET\n\n\n" + date + "\n" + path + "\nx-emc-date:" + date + "\nx-emc-uid:" + uid, secretKey);

        WebResource.Builder request = client.resource(serverUri).path(path).getRequestBuilder();

        request.header("Date", date);
        request.header("x-emc-date", date);
        request.header("x-emc-uid", uid);
        request.header("x-emc-signature", signature);

        ClientResponse response = request.get(ClientResponse.class);

        if (response.getStatus() > 299) throw new RuntimeException("error response: " + response.getStatus());

        String responseStr = response.getEntity(String.class);
        if (!responseStr.contains("Atmos")) throw new RuntimeException("unrecognized response string: " + responseStr);
    }

    private String sign(String canonicalString, String secretKey) {
        try {
            byte[] hashKey = Base64.decodeBase64(secretKey.getBytes("UTF-8"));
            byte[] input = canonicalString.getBytes("UTF-8");

            Mac mac = Mac.getInstance("HmacSHA1");
            SecretKeySpec key = new SecretKeySpec(hashKey, "HmacSHA1");
            mac.init(key);

            byte[] hashBytes = mac.doFinal(input);

            return new String(Base64.encodeBase64(hashBytes), "UTF-8");
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
