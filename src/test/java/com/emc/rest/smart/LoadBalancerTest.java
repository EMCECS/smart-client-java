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

import com.emc.util.RequestSimulator;
import org.apache.log4j.Level;
import org.apache.log4j.LogMF;
import org.apache.log4j.Logger;
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
    private static final Logger l4j = Logger.getLogger(LoadBalancerTest.class);

    @Test
    public void testDistribution() throws Exception {
        String[] hostList = new String[]{"foo", "bar", "baz", "biz"};
        final int callCount = 1000, callDuration = 50;

        SmartConfig smartConfig = new SmartConfig(hostList);
        smartConfig.setPollInterval(1);

        final LoadBalancer loadBalancer = smartConfig.getLoadBalancer();

        RequestSimulator simulator = new RequestSimulator(loadBalancer, callCount, callDuration);
        simulator.run();

        Assert.assertEquals("errors during call simulation", 0, simulator.getErrors().size());

        l4j.info(Arrays.toString(loadBalancer.getHostStats()));

        for (HostStats stats : loadBalancer.getHostStats()) {
            Assert.assertTrue("unbalanced call count", Math.abs(callCount / hostList.length - stats.getTotalConnections()) <= 3);
            Assert.assertEquals("average response wrong", callDuration, stats.getResponseQueueAverage());
        }
    }

    @Test
    public void testEfficiency() throws Exception {
        // turn down logging (will skew result drastically)
        Logger hostLogger = Logger.getLogger(Host.class);
        Level logLevel = hostLogger.getLevel();
        hostLogger.setLevel(Level.WARN);


        SmartConfig smartConfig = new SmartConfig("foo", "bar", "baz", "biz");

        LoadBalancer loadBalancer = smartConfig.getLoadBalancer();

        // make one meeeeeellion calls ;)
        ExecutorService service = Executors.newFixedThreadPool(32);
        List<Future<Long>> futures = new ArrayList<Future<Long>>();

        for (int i = 0; i < 1000000; i++) {
            futures.add(service.submit(new LBOverheadTask(loadBalancer)));
        }

        long totalNs = 0;
        for (Future<Long> future : futures) {
            totalNs += future.get();
        }
        long perCallOverhead = totalNs / 1000000;

        l4j.info(Arrays.toString(loadBalancer.getHostStats()));

        LogMF.warn(l4j, "per call overhead: {0}µs", perCallOverhead / 1000);
        hostLogger.setLevel(logLevel);

        Assert.assertTrue("call overhead too high", perCallOverhead < 500000); // must be less than .5ms
    }

    class LBOverheadTask implements Callable<Long> {
        LoadBalancer loadBalancer;

        public LBOverheadTask(LoadBalancer loadBalancer) {
            this.loadBalancer = loadBalancer;
        }

        @Override
        public Long call() throws Exception {
            long start = System.nanoTime();
            Host host = loadBalancer.getTopHost(null);
            host.connectionOpened();
            host.callComplete(0, false);
            host.connectionClosed();
            return System.nanoTime() - start;
        }
    }
}
