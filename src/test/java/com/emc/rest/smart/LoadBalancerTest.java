/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.rest.smart;

import org.apache.log4j.Level;
import org.apache.log4j.LogMF;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
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
        final Random random = new Random();

        SmartConfig smartConfig = new SmartConfig(Arrays.asList(hostList));
        smartConfig.setPollInterval(1);

        final LoadBalancer loadBalancer = smartConfig.getLoadBalancer();

        // simulate callCount successful calls with identical response times
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        List<Future> futures = new ArrayList<Future>();
        for (int i = 0; i < callCount; i++) {
            futures.add(executorService.submit(new Runnable() {
                @Override
                public void run() {
                    int waitMs;
                    synchronized (random) {
                        waitMs = random.nextInt(20);
                    }
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException e) {
                        l4j.warn("thread interrupted", e);
                    }
                    Host host = loadBalancer.getTopHost();
                    host.connectionOpened();
                    host.callComplete(callDuration, false);
                    host.connectionClosed();
                }
            }));
        }

        // wait for tasks to finish
        for (Future future : futures) {
            future.get();
        }

        l4j.info(Arrays.toString(loadBalancer.getHostStats()));

        for (HostStats stats : loadBalancer.getHostStats()) {
            Assert.assertTrue("unbalanced call count", Math.abs(callCount / hostList.length - stats.getTotalConnections()) <= 2);
            Assert.assertEquals("average response wrong", callDuration, stats.getResponseQueueAverage());
        }
    }

    @Test
    public void testEfficiency() throws Exception {
        // turn down logging (will skew result drastically)
        Level logLevel = Logger.getRootLogger().getLevel();
        Logger.getRootLogger().setLevel(Level.WARN);

        SmartConfig smartConfig = new SmartConfig(Arrays.asList("foo", "bar", "baz", "biz"));

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
        Logger.getRootLogger().setLevel(logLevel);

        Assert.assertTrue("call overhead too high", perCallOverhead < 100000); // must be less than .1ms
    }

    class LBOverheadTask implements Callable<Long> {
        LoadBalancer loadBalancer;

        public LBOverheadTask(LoadBalancer loadBalancer) {
            this.loadBalancer = loadBalancer;
        }

        @Override
        public Long call() throws Exception {
            long start = System.nanoTime();
            Host host = loadBalancer.getTopHost();
            host.connectionOpened();
            host.callComplete(0, false);
            host.connectionClosed();
            return System.nanoTime() - start;
        }
    }
}
