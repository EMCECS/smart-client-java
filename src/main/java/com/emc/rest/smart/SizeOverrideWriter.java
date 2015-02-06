/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.rest.smart;

import com.emc.rest.util.SizedInputStream;
import com.sun.jersey.core.impl.provider.entity.ByteArrayProvider;
import com.sun.jersey.core.impl.provider.entity.FileProvider;

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
    private static final ThreadLocal<Long> entitySize = new ThreadLocal<Long>();

    public static Long getEntitySize() {
        return entitySize.get();
    }

    public static void setEntitySize(Long size) {
        entitySize.set(size);
    }

    private MessageBodyWriter<T> delegate;

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
            entitySize.remove();
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
    public static class SizedIS extends SizeOverrideWriter<SizedInputStream> {
        private static final SizedInputStreamWriter delegate = new SizedInputStreamWriter();

        public SizedIS() {
            super(delegate);
        }
    }
}
