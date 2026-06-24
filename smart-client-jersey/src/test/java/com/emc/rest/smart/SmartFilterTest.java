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

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.client.ClientRequest;
import org.glassfish.jersey.client.ClientResponse;
import org.glassfish.jersey.client.spi.AsyncConnectorCallback;
import org.glassfish.jersey.client.spi.Connector;
import org.glassfish.jersey.client.spi.ConnectorProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.rest.smart.jersey.SmartClientFactory;
import com.emc.rest.smart.jersey.SmartFilter;

public class SmartFilterTest {
    private static final Logger log = LoggerFactory.getLogger(SmartFilterTest.class);

    // --- isHostError tests ---

    @Test
    public void testIsHostError_ConnectException() {
        Assertions.assertTrue(SmartFilter.isHostError(new ConnectException("Connection refused")));
    }

    @Test
    public void testIsHostError_ProcessingExceptionWrappingConnectException() {
        Assertions.assertTrue(SmartFilter.isHostError(
                new ProcessingException(new ConnectException("Connection refused"))));
    }

    @Test
    public void testIsHostError_SmartClientExceptionService() {
        SmartClientException sce = new SmartClientException("server error");
        sce.setErrorType(SmartClientException.ErrorType.Service);
        Assertions.assertTrue(SmartFilter.isHostError(sce));
    }

    @Test
    public void testIsHostError_SmartClientExceptionClient() {
        SmartClientException sce = new SmartClientException("not found");
        sce.setErrorType(SmartClientException.ErrorType.Client);
        Assertions.assertFalse(SmartFilter.isHostError(sce));
    }

    @Test
    public void testIsHostError_SmartClientExceptionUnknown() {
        SmartClientException sce = new SmartClientException("unknown");
        sce.setErrorType(SmartClientException.ErrorType.Unknown);
        Assertions.assertTrue(SmartFilter.isHostError(sce));
    }

    // --- isConnectError tests ---

    @Test
    public void testIsConnectError_ConnectException() {
        Assertions.assertTrue(SmartFilter.isConnectError(new ConnectException("Connection refused")));
    }

    @Test
    public void testIsConnectError_WrappedConnectException() {
        Assertions.assertTrue(SmartFilter.isConnectError(
                new ProcessingException(new ConnectException("Connection refused"))));
    }

    @Test
    public void testIsConnectError_NoRouteToHost() {
        Assertions.assertTrue(SmartFilter.isConnectError(new NoRouteToHostException("No route to host")));
    }

    @Test
    public void testIsConnectError_UnknownHost() {
        Assertions.assertTrue(SmartFilter.isConnectError(new UnknownHostException("badhost.example.com")));
    }

    @Test
    public void testIsConnectError_ConnectTimeout() {
        Assertions.assertTrue(SmartFilter.isConnectError(
                new SocketTimeoutException("Connect timed out")));
    }

    @Test
    public void testIsConnectError_ReadTimeout_NotConnectError() {
        Assertions.assertFalse(SmartFilter.isConnectError(
                new SocketTimeoutException("Read timed out")));
    }

    @Test
    public void testIsConnectError_RuntimeException_NotConnectError() {
        Assertions.assertFalse(SmartFilter.isConnectError(new RuntimeException("something else")));
    }

    @Test
    public void testIsConnectError_IOException_NotConnectError() {
        Assertions.assertFalse(SmartFilter.isConnectError(new IOException("stream closed")));
    }

    // --- Error counting tests ---

    @Test
    public void testConnectionErrorCountedAsHostError() {
        // Verify that after a ConnectException, the host has consecutiveErrors > 0
        // (Previously, the bug caused consecutiveErrors to be reset to 0)
        Host host = new Host("unreachable");
        host.connectionOpened();
        host.callComplete(true); // the fix ensures connection errors are marked as errors
        host.connectionClosed();

        Assertions.assertEquals(1, host.getConsecutiveErrors(),
                "Connection error should increment consecutiveErrors");
        Assertions.assertEquals(1, host.getTotalErrors(),
                "Connection error should increment totalErrors");
        Assertions.assertFalse(host.isHealthy(),
                "Host should be unhealthy immediately after a connection error");
    }

    @Test
    public void testOldBugBehavior_ConnectionErrorWouldResetErrors() {
        // This test demonstrates what the OLD buggy code did:
        // callComplete(false) resets consecutiveErrors to 0
        Host host = new Host("testhost");
        host.connectionOpened();
        host.callComplete(false); // <-- this is what the old code did for ConnectException
        host.connectionClosed();

        Assertions.assertEquals(0, host.getConsecutiveErrors(),
                "callComplete(false) resets consecutiveErrors - this was the bug");
        Assertions.assertTrue(host.isHealthy(),
                "Host incorrectly appears healthy after callComplete(false)");
    }

    // --- SmartFilter retry integration tests (using SmartClientFactory + mock ConnectorProvider) ---

    @Test
    public void testRetryOnConnectError() {
        List<Host> hostList = new ArrayList<>();
        hostList.add(new Host("unreachable-host"));
        hostList.add(new Host("reachable-host"));

        SmartConfig smartConfig = new SmartConfig(hostList);
        smartConfig.setMaxRetryAttempts(2);
        smartConfig.setHostUpdateEnabled(false);
        smartConfig.setHealthCheckEnabled(false);

        List<String> attemptedHosts = Collections.synchronizedList(new ArrayList<>());

        ConnectorProvider mockProvider = (jaxRsClient, runtimeConfig) -> new Connector() {
            @Override
            public ClientResponse apply(ClientRequest request) {
                String host = request.getUri().getHost();
                attemptedHosts.add(host);
                if ("unreachable-host".equals(host)) {
                    throw new ProcessingException(new ConnectException("Connection refused"));
                }
                return new ClientResponse(Response.Status.OK, request);
            }

            @Override
            public Future<?> apply(ClientRequest request, AsyncConnectorCallback callback) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getName() { return "MockConnector"; }

            @Override
            public void close() { }
        };

        Client client = SmartClientFactory.createSmartClient(smartConfig, mockProvider);

        try {
            Response response = client.target("http://original-host:9020/test").request().get();

            Assertions.assertEquals(200, response.getStatus(),
                    "Request should succeed after retry");
            log.info("Attempted hosts: {}", attemptedHosts);
            Assertions.assertTrue(attemptedHosts.size() >= 2,
                    "Should have attempted at least 2 hosts");
            Assertions.assertTrue(attemptedHosts.contains("reachable-host"),
                    "Should have eventually reached the reachable host");

            // Verify the unreachable host has errors counted
            Host unreachableHost = smartConfig.getLoadBalancer().getAllHosts().stream()
                    .filter(h -> "unreachable-host".equals(h.getName()))
                    .findFirst().orElseThrow();
            Assertions.assertTrue(unreachableHost.getConsecutiveErrors() > 0,
                    "Unreachable host should have consecutive errors");
            Assertions.assertFalse(unreachableHost.isHealthy(),
                    "Unreachable host should be marked unhealthy");
        } finally {
            SmartClientFactory.destroy(client);
        }
    }

    @Test
    public void testRetryExhausted() {
        List<Host> hostList = new ArrayList<>();
        hostList.add(new Host("bad1"));
        hostList.add(new Host("bad2"));
        hostList.add(new Host("bad3"));

        SmartConfig smartConfig = new SmartConfig(hostList);
        smartConfig.setMaxRetryAttempts(2);
        smartConfig.setHostUpdateEnabled(false);
        smartConfig.setHealthCheckEnabled(false);

        List<String> attemptedHosts = Collections.synchronizedList(new ArrayList<>());

        ConnectorProvider mockProvider = (jaxRsClient, runtimeConfig) -> new Connector() {
            @Override
            public ClientResponse apply(ClientRequest request) {
                attemptedHosts.add(request.getUri().getHost());
                throw new ProcessingException(new ConnectException("Connection refused"));
            }

            @Override
            public Future<?> apply(ClientRequest request, AsyncConnectorCallback callback) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getName() { return "MockConnector"; }

            @Override
            public void close() { }
        };

        Client client = SmartClientFactory.createSmartClient(smartConfig, mockProvider);

        try {
            Assertions.assertThrows(ProcessingException.class,
                    () -> client.target("http://original-host:9020/test").request().get(),
                    "Should throw after all retries exhausted");

            Assertions.assertEquals(3, attemptedHosts.size(),
                    "Should attempt 1 + maxRetries = 3 times");
            log.info("Attempted hosts (all failed): {}", attemptedHosts);
        } finally {
            SmartClientFactory.destroy(client);
        }
    }

    @Test
    public void testNoRetryOnNonConnectError() {
        List<Host> hostList = new ArrayList<>();
        hostList.add(new Host("host1"));
        hostList.add(new Host("host2"));

        SmartConfig smartConfig = new SmartConfig(hostList);
        smartConfig.setMaxRetryAttempts(2);
        smartConfig.setHostUpdateEnabled(false);
        smartConfig.setHealthCheckEnabled(false);

        List<String> attemptedHosts = Collections.synchronizedList(new ArrayList<>());

        ConnectorProvider mockProvider = (jaxRsClient, runtimeConfig) -> new Connector() {
            @Override
            public ClientResponse apply(ClientRequest request) {
                attemptedHosts.add(request.getUri().getHost());
                throw new ProcessingException(new SocketTimeoutException("Read timed out"));
            }

            @Override
            public Future<?> apply(ClientRequest request, AsyncConnectorCallback callback) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getName() { return "MockConnector"; }

            @Override
            public void close() { }
        };

        Client client = SmartClientFactory.createSmartClient(smartConfig, mockProvider);

        try {
            Assertions.assertThrows(ProcessingException.class,
                    () -> client.target("http://original-host:9020/test").request().get());

            Assertions.assertEquals(1, attemptedHosts.size(),
                    "Non-connect errors should not trigger retry");
        } finally {
            SmartClientFactory.destroy(client);
        }
    }

    @Test
    public void testMaxRetryAttemptsZeroDisablesRetry() {
        List<Host> hostList = new ArrayList<>();
        hostList.add(new Host("bad"));
        hostList.add(new Host("good"));

        SmartConfig smartConfig = new SmartConfig(hostList);
        smartConfig.setMaxRetryAttempts(0);
        smartConfig.setHostUpdateEnabled(false);
        smartConfig.setHealthCheckEnabled(false);

        List<String> attemptedHosts = Collections.synchronizedList(new ArrayList<>());

        ConnectorProvider mockProvider = (jaxRsClient, runtimeConfig) -> new Connector() {
            @Override
            public ClientResponse apply(ClientRequest request) {
                attemptedHosts.add(request.getUri().getHost());
                throw new ProcessingException(new ConnectException("Connection refused"));
            }

            @Override
            public Future<?> apply(ClientRequest request, AsyncConnectorCallback callback) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getName() { return "MockConnector"; }

            @Override
            public void close() { }
        };

        Client client = SmartClientFactory.createSmartClient(smartConfig, mockProvider);

        try {
            Assertions.assertThrows(ProcessingException.class,
                    () -> client.target("http://original-host:9020/test").request().get());

            Assertions.assertEquals(1, attemptedHosts.size(),
                    "With maxRetryAttempts=0, should only try once");
        } finally {
            SmartClientFactory.destroy(client);
        }
    }
}
