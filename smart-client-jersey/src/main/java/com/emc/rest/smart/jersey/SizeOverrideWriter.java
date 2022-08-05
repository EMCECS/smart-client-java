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

import com.sun.jersey.core.impl.provider.entity.ByteArrayProvider;
import com.sun.jersey.core.impl.provider.entity.FileProvider;
import com.sun.jersey.core.impl.provider.entity.InputStreamProvider;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

public class SizeOverrideWriter<T> implements MessageBodyWriter<T> {
    private static final ThreadLocal<Long> entitySize = new ThreadLocal<>();

    public static Long getEntitySize() {
        return entitySize.get();
    }

    public static void setEntitySize(Long size) {
        entitySize.set(size);
    }

    private final MessageBodyWriter<T> delegate;

    public SizeOverrideWriter(MessageBodyWriter<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return delegate.isWriteable(type, genericType, annotations, mediaType);
    }

    @Override
    public long getSize(T t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        Long size = entitySize.get();
        if (size != null) {
            return size;
        }
        return delegate.getSize(t, type, genericType, annotations, mediaType);
    }

    @Override
    public void writeTo(T t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
        delegate.writeTo(t, type, genericType, annotations, mediaType, httpHeaders, entityStream);
    }

    @Produces({"application/octet-stream", "*/*"})
    public static class ByteArray extends SizeOverrideWriter<byte[]> {
        private static final ByteArrayProvider delegate = new ByteArrayProvider();

        public ByteArray() {
            super(delegate);
        }
    }

    @Produces({"application/octet-stream", "*/*"})
    public static class File extends SizeOverrideWriter<java.io.File> {
        private static final FileProvider delegate = new FileProvider();

        public File() {
            super(delegate);
        }
    }

    @Produces({"application/octet-stream", "*/*"})
    public static class InputStream extends SizeOverrideWriter<java.io.InputStream> {
        private static final InputStreamProvider delegate = new InputStreamProvider();

        public InputStream() {
            super(delegate);
        }
    }

    @Produces({"application/octet-stream", "*/*"})
    public static class SizedInputStream extends SizeOverrideWriter<com.emc.rest.util.SizedInputStream> {
        private static final SizedInputStreamWriter delegate = new SizedInputStreamWriter();

        public SizedInputStream() {
            super(delegate);
        }
    }
}
