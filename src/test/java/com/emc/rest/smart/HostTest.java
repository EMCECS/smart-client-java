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
        int errorCoolDown = host.getErrorCoolDownSecs();

        // simulate some calls
        int callCount = 100, duration = 50;
        for (int i = 0; i < callCount; i++) {
            host.connectionOpened();
            host.callComplete(duration, false);
            host.connectionClosed();
        }

        Assert.assertEquals(callCount, host.getTotalConnections());
        Assert.assertEquals(0, host.getTotalErrors());
        Assert.assertEquals(0, host.getConsecutiveErrors());
        Assert.assertEquals(0, host.getOpenConnections());
        Assert.assertEquals(duration, host.getResponseQueueAverage());
        Assert.assertTrue(duration - host.getResponseIndex() <= 1); // 1ms margin of error (execution time)

        // test open connections
        host.connectionOpened();
        host.connectionOpened();

        Assert.assertEquals(2, host.getOpenConnections());

        // test error
        host.callComplete(duration, true);
        host.connectionClosed();

        Assert.assertEquals(1, host.getOpenConnections());
        Assert.assertEquals(1, host.getConsecutiveErrors());
        Assert.assertEquals(1, host.getTotalErrors());
        Assert.assertEquals(duration, host.getResponseQueueAverage());
        // response average + (errors * error-cool-down-in-secs) + (open-connections * error-cool-down-in-ms)
        Assert.assertTrue(host.getResponseIndex() - (50 + errorCoolDown * 1000 + errorCoolDown) <= 1); // 1ms margin of error (execution time)

        // test another error
        host.callComplete(duration, true);
        host.connectionClosed();

        Assert.assertEquals(0, host.getOpenConnections());
        Assert.assertEquals(2, host.getConsecutiveErrors());
        Assert.assertEquals(2, host.getTotalErrors());
        Assert.assertEquals(duration, host.getResponseQueueAverage());
        // response average + (errors * error-cool-down-in-secs) + (open-connections * error-cool-down-in-ms)
        Assert.assertTrue(host.getResponseIndex() - (50 + 2 * errorCoolDown * 1000) <= 1); // 1ms margin of error (execution time)

        // test cool-down
        Thread.sleep(500);

        Assert.assertTrue(host.getResponseIndex() - (50 + 2 * errorCoolDown * 1000 - 500) <= 1); // 1ms margin of error (execution time)

        // test no more errors
        host.connectionOpened();
        host.callComplete(duration, false);
        host.connectionClosed();

        Assert.assertEquals(0, host.getConsecutiveErrors());
        Assert.assertEquals(2, host.getTotalErrors());
        Assert.assertEquals(callCount + 3, host.getTotalConnections());
        Assert.assertEquals(duration, host.getResponseQueueAverage());
        Assert.assertTrue(host.getResponseIndex() - 50 <= 1); // 1ms margin of error (execution time)
    }
}
