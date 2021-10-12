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

import com.emc.rest.util.RequestSimulator;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class TestHealthCheck {
    private static final Logger l4j = Logger.getLogger(TestHealthCheck.class);

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
