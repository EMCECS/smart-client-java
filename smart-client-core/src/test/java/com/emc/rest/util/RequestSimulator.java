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
package com.emc.rest.util;

import com.emc.rest.smart.Host;
import com.emc.rest.smart.LoadBalancer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class RequestSimulator implements Runnable {
    private final LoadBalancer loadBalancer;
    private final int callCount;
    private RequestExecutor requestExecutor;
    private final List<Throwable> errors = new ArrayList<>();

    public RequestSimulator(LoadBalancer loadBalancer, int callCount) {
        this.loadBalancer = loadBalancer;
        this.callCount = callCount;
    }

    @Override
    public void run() {
        final Random random = new Random();

        // simulate callCount successful calls with identical response times
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < callCount; i++) {
            futures.add(executorService.submit(() -> {
                int waitMs;
                synchronized (random) {
                    waitMs = random.nextInt(20);
                }
                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Host host = loadBalancer.getTopHost(null);
                host.connectionOpened();
                try {
                    if (requestExecutor != null) requestExecutor.execute(host);
                    host.callComplete(false);
                } catch (Throwable t) {
                    host.callComplete(true);
                }
                host.connectionClosed();
            }));
        }

        // wait for tasks to finish
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Throwable t) {
                errors.add(t);
            }
        }
    }

    public RequestSimulator withRequestExecutor(RequestExecutor requestExecutor) {
        this.requestExecutor = requestExecutor;
        return this;
    }

    public List<Throwable> getErrors() {
        return errors;
    }

    public interface RequestExecutor {
        void execute(Host host);
    }
}
