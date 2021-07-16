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
package com.emc.util;

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
