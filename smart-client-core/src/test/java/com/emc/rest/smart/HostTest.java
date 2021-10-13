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

import org.junit.Assert;
import org.junit.Test;

public class HostTest {
    @Test
    public void testHost() throws Exception {
        Host host = new Host("foo");
        int errorWaitTime = host.getErrorWaitTime();

        // simulate some calls
        int callCount = 100;
        for (int i = 0; i < callCount; i++) {
            host.connectionOpened();
            host.callComplete(false);
            host.connectionClosed();
        }

        Assert.assertEquals(callCount, host.getTotalConnections());
        Assert.assertEquals(0, host.getTotalErrors());
        Assert.assertEquals(0, host.getConsecutiveErrors());
        Assert.assertEquals(0, host.getOpenConnections());
        Assert.assertEquals(0, host.getResponseIndex());

        // test open connections
        host.connectionOpened();
        host.connectionOpened();

        Assert.assertEquals(2, host.getOpenConnections());
        Assert.assertEquals(2, host.getResponseIndex());

        // test error
        host.callComplete(false);
        host.connectionClosed();
        host.callComplete(true);
        host.connectionClosed();

        Assert.assertEquals(0, host.getOpenConnections());
        Assert.assertEquals(1, host.getConsecutiveErrors());
        Assert.assertEquals(1, host.getTotalErrors());
        Assert.assertEquals(0, host.getResponseIndex());
        Assert.assertFalse(host.isHealthy()); // host should enter cool down period for error

        Thread.sleep(errorWaitTime - 500); // wait until just before the error is cooled down
        Assert.assertFalse(host.isHealthy()); // host should still be in cool down period

        Thread.sleep(600); // wait until cool down period is over
        Assert.assertTrue(host.isHealthy());

        // test another error
        host.connectionOpened();
        host.callComplete(true);
        host.connectionClosed();

        Assert.assertEquals(0, host.getOpenConnections());
        Assert.assertEquals(2, host.getConsecutiveErrors());
        Assert.assertEquals(2, host.getTotalErrors());
        Assert.assertEquals(0, host.getResponseIndex());
        Assert.assertFalse(host.isHealthy());

        // cool down should be compounded for consecutive errors (multiplied by powers of 2)
        Thread.sleep(2L * errorWaitTime - 500); // wait until just before cool down is over
        Assert.assertFalse(host.isHealthy());

        Thread.sleep(600); // wait until cool down period is over
        Assert.assertTrue(host.isHealthy());

        // test one more error
        host.connectionOpened();
        host.callComplete(true);
        host.connectionClosed();

        Assert.assertEquals(0, host.getOpenConnections());
        Assert.assertEquals(3, host.getConsecutiveErrors());
        Assert.assertEquals(3, host.getTotalErrors());
        Assert.assertEquals(0, host.getResponseIndex());
        Assert.assertFalse(host.isHealthy());

        // cool down should be compounded for consecutive errors (multiplied by powers of 2)
        Thread.sleep(2L * 2 * errorWaitTime - 500); // wait until just before cool down is over
        Assert.assertFalse(host.isHealthy());

        Thread.sleep(600); // wait until cool down period is over
        Assert.assertTrue(host.isHealthy());

        // test no more errors
        host.connectionOpened();
        host.callComplete(false);
        host.connectionClosed();

        Assert.assertEquals(0, host.getConsecutiveErrors());
        Assert.assertEquals(3, host.getTotalErrors());
        Assert.assertEquals(callCount + 5, host.getTotalConnections());
        Assert.assertEquals(0, host.getResponseIndex());
        Assert.assertTrue(host.isHealthy());
    }

    @Test
    public void testErrorWaitLimit() throws Exception {
        Host host = new Host("bar");
        host.setErrorWaitTime(100); // don't want this test to take forever

        Assert.assertTrue(host.isHealthy());

        // 8 consecutive errors
        long errors = 8;
        for (int i = 0; i < errors; i++) {
            host.connectionOpened();
            host.callComplete(true);
            host.connectionClosed();
        }

        Assert.assertEquals(errors, host.getConsecutiveErrors());
        long maxCoolDownMs = host.getErrorWaitTime() * (long) Math.pow(2, Host.MAX_COOL_DOWN_EXP) + 10; // add a few ms

        Thread.sleep(maxCoolDownMs);

        Assert.assertTrue(host.isHealthy());
    }
}
