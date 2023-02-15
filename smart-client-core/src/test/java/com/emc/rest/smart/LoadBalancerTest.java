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
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.apache.logging.log4j.message.ParameterizedMessage;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class LoadBalancerTest {
    private static final Logger l4j = LogManager.getLogger(LoadBalancerTest.class);

    @Test
    public void testDistribution() {
        String[] hostList = new String[]{"foo", "bar", "baz", "biz"};
        final int callCount = 1000;

        SmartConfig smartConfig = new SmartConfig(hostList);
        smartConfig.setPollInterval(1);

        final LoadBalancer loadBalancer = smartConfig.getLoadBalancer();

        RequestSimulator simulator = new RequestSimulator(loadBalancer, callCount);
        simulator.run();

        Assert.assertEquals("errors during call simulation", 0, simulator.getErrors().size());

        l4j.info(Arrays.toString(loadBalancer.getHostStats()));

        for (HostStats stats : loadBalancer.getHostStats()) {
            Assert.assertTrue("unbalanced call count", Math.abs(callCount / hostList.length - stats.getTotalConnections()) <= 3);
        }
    }

    @Test
    public void testEfficiency() throws Exception {
        // turn down logging (will skew result drastically)
        Logger hostLogger = LogManager.getLogger(Host.class);
        Level logLevel = hostLogger.getLevel();
        hostLogger.atLevel(Level.WARN);


        SmartConfig smartConfig = new SmartConfig("foo", "bar", "baz", "biz");

        LoadBalancer loadBalancer = smartConfig.getLoadBalancer();

        // make one meeeeeellion calls ;)
        ExecutorService service = Executors.newFixedThreadPool(32);
        List<Future<Long>> futures = new ArrayList<>();

        for (int i = 0; i < 1000000; i++) {
            futures.add(service.submit(new LBOverheadTask(loadBalancer)));
        }

        long totalNs = 0;
        for (Future<Long> future : futures) {
            totalNs += future.get();
        }
        long perCallOverhead = totalNs / 1000000;

        l4j.info(Arrays.toString(loadBalancer.getHostStats()));

        l4j.warn(new ParameterizedMessage("per call overhead: {0}µs", perCallOverhead / 1000));
        hostLogger.atLevel(logLevel);

        Assert.assertTrue("call overhead too high", perCallOverhead < 100000); // must be less than .1ms
    }

    static class LBOverheadTask implements Callable<Long> {
        LoadBalancer loadBalancer;

        public LBOverheadTask(LoadBalancer loadBalancer) {
            this.loadBalancer = loadBalancer;
        }

        @Override
        public Long call() {
            long start = System.nanoTime();
            Host host = loadBalancer.getTopHost(null);
            host.connectionOpened();
            host.callComplete(false);
            host.connectionClosed();
            return System.nanoTime() - start;
        }
    }
}
