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

import com.emc.rest.util.RequestSimulator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class TestHealthCheck {
    private static final Logger l4j = LogManager.getLogger(TestHealthCheck.class);

    @Test
    public void testUnhealthyHostIgnored() {
        String[] hostList = new String[]{"foo", "bar", "baz", "biz"};
        final int callCount = 1000;

        SmartConfig smartConfig = new SmartConfig(hostList);
        smartConfig.setPollInterval(1);

        final LoadBalancer loadBalancer = smartConfig.getLoadBalancer();

        // make one host unhealthy
        Host foo = loadBalancer.getAllHosts().get(0);
        foo.setHealthy(false);

        RequestSimulator simulator = new RequestSimulator(loadBalancer, callCount);
        simulator.run();

        Assert.assertEquals("errors during call simulation", 0, simulator.getErrors().size());

        l4j.info(Arrays.toString(loadBalancer.getHostStats()));

        for (HostStats stats : loadBalancer.getHostStats()) {
            if (stats.equals(foo)) {
                Assert.assertEquals("unhealthy host should be ignored", 0, stats.getTotalConnections());
            } else {
                Assert.assertTrue("unbalanced call count", Math.abs(callCount / (hostList.length - 1) - stats.getTotalConnections()) <= 3);
            }
        }
    }

    @Test
    public void testHealthyUpdate() throws Exception {
        String[] hostList = new String[]{"foo", "bar", "baz", "biz"};
        final int callCount = 1000;

        SmartConfig smartConfig = new SmartConfig(hostList);

        LoadBalancer loadBalancer = smartConfig.getLoadBalancer();

        // make one host unhealthy
        Host foo = loadBalancer.getAllHosts().get(0);

        TestHostListProvider testProvider = new TestHostListProvider(foo, false);
        smartConfig.setHostListProvider(testProvider);

        // first poll should keep list the same and set foo to unhealthy
        PollingDaemon poller = new PollingDaemon(smartConfig);
        poller.start(); // starting a thread!
        Thread.sleep(200); // give poller a chance to run
        poller.terminate();

        Assert.assertFalse(foo.isHealthy());
        Assert.assertEquals(4, loadBalancer.getAllHosts().size());

        // simulate calls
        RequestSimulator simulator = new RequestSimulator(loadBalancer, callCount);
        simulator.run();

        Assert.assertEquals("errors during call simulation", 0, simulator.getErrors().size());

        l4j.info(Arrays.toString(loadBalancer.getHostStats()));

        for (HostStats stats : loadBalancer.getHostStats()) {
            if (stats.equals(foo)) {
                Assert.assertEquals("unhealthy host should be ignored", 0, stats.getTotalConnections());
            } else {
                Assert.assertTrue("unbalanced call count", Math.abs(callCount / (hostList.length - 1) - stats.getTotalConnections()) <= 3);
            }
        }

        // second poll should keep list the same and set foo to healthy
        testProvider.healthy = true;
        poller = new PollingDaemon(smartConfig);
        poller.start(); // starting a thread!
        Thread.sleep(200); // give poller a chance to run
        poller.terminate();

        Assert.assertTrue(foo.isHealthy());
        Assert.assertEquals(4, loadBalancer.getAllHosts().size());

        // reset stats and simulate calls
        loadBalancer.resetStats();
        simulator = new RequestSimulator(loadBalancer, callCount);
        simulator.run();

        Assert.assertEquals("errors during call simulation", 0, simulator.getErrors().size());

        l4j.info(Arrays.toString(loadBalancer.getHostStats()));

        for (HostStats stats : loadBalancer.getHostStats()) {
            Assert.assertTrue("unbalanced call count", Math.abs(callCount / hostList.length - stats.getTotalConnections()) <= 3);
        }
    }

    static class TestHostListProvider implements HostListProvider {
        private final Host host;
        boolean healthy;

        public TestHostListProvider(Host host, boolean healthy) {
            this.host = host;
            this.healthy = healthy;
        }

        @Override
        public void destroy() {
        }

        @Override
        public List<Host> getHostList() {
            throw new RuntimeException("no host update");
        }

        @Override
        public void runHealthCheck(Host host) {
            if (this.host == host && !healthy) throw new RuntimeException("host is unhealthy");
        }
    }
}
