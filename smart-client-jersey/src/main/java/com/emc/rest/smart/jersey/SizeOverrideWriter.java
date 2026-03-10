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
package com.emc.rest.smart.jersey;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.ext.WriterInterceptor;
import javax.ws.rs.ext.WriterInterceptorContext;
import java.io.IOException;

/**
 * A WriterInterceptor that allows overriding the Content-Length header for entities.
 * Set the entity size via the ThreadLocal before making a request to override the Content-Length.
 */
public class SizeOverrideWriter implements WriterInterceptor {
    private static final ThreadLocal<Long> entitySize = new ThreadLocal<>();

    public static Long getEntitySize() {
        return entitySize.get();
    }

    public static void setEntitySize(Long size) {
        entitySize.set(size);
    }

    @Override
    public void aroundWriteTo(WriterInterceptorContext context) throws IOException, WebApplicationException {
        Long size = entitySize.get();
        if (size != null) {
            context.getHeaders().putSingle("Content-Length", size);
        }
        context.proceed();
    }
}
