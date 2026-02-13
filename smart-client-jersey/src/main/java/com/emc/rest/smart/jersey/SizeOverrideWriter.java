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

import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyWriter;
import org.glassfish.jersey.message.internal.ReaderWriter;
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
        public ByteArray() {
            super(new DefaultByteArrayWriter());
        }
    }

    @Produces({"application/octet-stream", "*/*"})
    public static class File extends SizeOverrideWriter<java.io.File> {
        public File() {
            super(new DefaultFileWriter());
        }
    }

    @Produces({"application/octet-stream", "*/*"})
    public static class InputStream extends SizeOverrideWriter<java.io.InputStream> {
        public InputStream() {
            super(new DefaultInputStreamWriter());
        }
    }

    @Produces({"application/octet-stream", "*/*"})
    public static class SizedInputStream extends SizeOverrideWriter<com.emc.rest.util.SizedInputStream> {
        public SizedInputStream() {
            super(new SizedInputStreamWriter());
        }
    }

    private static class DefaultByteArrayWriter implements MessageBodyWriter<byte[]> {
        @Override
        public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
            return type == byte[].class;
        }

        @Override
        public long getSize(byte[] bytes, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
            return bytes.length;
        }

        @Override
        public void writeTo(byte[] bytes, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
            entityStream.write(bytes);
        }
    }

    private static class DefaultFileWriter implements MessageBodyWriter<java.io.File> {
        @Override
        public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
            return java.io.File.class.isAssignableFrom(type);
        }

        @Override
        public long getSize(java.io.File file, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
            return file.length();
        }

        @Override
        public void writeTo(java.io.File file, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
            try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                ReaderWriter.writeTo(fis, entityStream);
            }
        }
    }

    private static class DefaultInputStreamWriter implements MessageBodyWriter<java.io.InputStream> {
        @Override
        public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
            return java.io.InputStream.class.isAssignableFrom(type);
        }

        @Override
        public long getSize(java.io.InputStream inputStream, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
            return -1;
        }

        @Override
        public void writeTo(java.io.InputStream inputStream, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
            ReaderWriter.writeTo(inputStream, entityStream);
        }
    }
}
