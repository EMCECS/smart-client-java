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
        Thread.sleep(2 * errorWaitTime - 500); // wait until just before cool down is over
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
        Thread.sleep(2 * 2 * errorWaitTime - 500); // wait until just before cool down is over
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
