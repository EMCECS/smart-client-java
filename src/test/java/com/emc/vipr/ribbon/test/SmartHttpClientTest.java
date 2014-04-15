/*
 * Copyright 2014 EMC Corporation. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 * http://www.apache.org/licenses/LICENSE-2.0.txt
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.emc.vipr.ribbon.test;

import com.emc.vipr.ribbon.SmartClientConfig;
import com.emc.vipr.ribbon.SmartHttpClient;
import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.LoadBalancerStats;
import com.netflix.loadbalancer.Server;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpGet;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertNull;

public class SmartHttpClientTest {
    @Test
    public void testRibbonClient() throws Exception {
        String clientName = "testRibbonClient";
        SmartHttpClient client = new SmartHttpClient(clientName,
                new SmartClientConfig().withInitialNodes("www.yahoo.com:80,www.google.com:80,www.bing.com:80,www.icann.org:80,www.linkedin.com:80")
                        .withVipAddresses("www.foo.com")
        );
        // fire off 20 requests
        for (int i = 0; i < 20; i++) {
            HttpEntity e = client.execute(new HttpGet("http://www.foo.com/")).getEntity();
            if (e != null) readAndClose(e.getContent());
        }
        BaseLoadBalancer lb = (BaseLoadBalancer) client.getLoadBalancer();
        LoadBalancerStats lbStats = lb.getLoadBalancerStats();
        assertNull("No requests should go to www.foo.com", lbStats.getServerStats().get(new Server("www.foo.com", 80)));
        System.out.println(lbStats);
    }

    private void readAndClose(InputStream is) throws IOException {
        byte[] buffer = new byte[4096];
        int read = 0;
        while (read >= 0) {
            read = is.read(buffer);
        }
        is.close();
    }
}
