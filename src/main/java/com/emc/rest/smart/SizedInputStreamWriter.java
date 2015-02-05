/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.rest.smart;

import com.emc.rest.util.SizedInputStream;
import com.emc.rest.util.StreamUtil;

import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

@Produces("*/*")
public class SizedInputStreamWriter implements MessageBodyWriter<SizedInputStream> {
    @Override
    public long getSize(SizedInputStream mis,
                        Class<?> type,
                        Type genericType,
                        Annotation[] annotations,
                        MediaType mediaType) {
        return mis.getSize();
    }

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation annotations[], MediaType mediaType) {
        return SizedInputStream.class.isAssignableFrom(type);
    }

    @Override
    public void writeTo(SizedInputStream mis,
                        Class<?> type,
                        Type genericType,
                        Annotation annotations[],
                        MediaType mediaType,
                        MultivaluedMap<String, Object> httpHeaders,
                        OutputStream entityStream) throws IOException {
        StreamUtil.copy(mis, entityStream, mis.getSize());
    }
}
