/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.rest.smart;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * Base class to disable chunked encoding in requests by always specifying an accurate byte count in getSize().
 * Subclasses should provide a default constructor, which calls super() with an instance of the underlying writer
 * implementation to be wrapped.
 * <p/>
 * XXX: this is inefficient and should be replaced by a different mechanism.  However, it is the simplest solution to
 * the apache client's insistence on using chunked encoding for all requests with a size of -1 and Jersey's insistence
 * on returning -1 from all message body providers (as well as not allowing users to override the content-length
 * header).
 */
public class MeasuredMessageBodyWriter<T> implements MessageBodyWriter<T> {
    protected MessageBodyWriter<T> wrapped;
    private IOException delayedIOException;
    private WebApplicationException delayedWebAppException;

    public MeasuredMessageBodyWriter( MessageBodyWriter<T> wrapped ) {
        this.wrapped = wrapped;
    }

    @Override
    public boolean isWriteable( Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType ) {
        return wrapped.isWriteable( type, genericType, annotations, mediaType );
    }

    @Override
    public void writeTo( T t,
                         Class<?> type,
                         Type genericType,
                         Annotation[] annotations,
                         MediaType mediaType,
                         MultivaluedMap<String, Object> httpHeaders,
                         OutputStream entityStream ) throws IOException, WebApplicationException {
        if ( delayedIOException != null ) throw delayedIOException;
        if ( delayedWebAppException != null ) throw delayedWebAppException;
        entityStream.write( getBuffer( t, type, genericType, annotations, mediaType, httpHeaders ) );
    }

    @Override
    public long getSize( T t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType ) {
        try {
            return getBuffer( t, type, genericType, annotations, mediaType, null ).length;
        } catch ( IOException e ) {
            delayedIOException = e;
        } catch ( WebApplicationException e ) {
            delayedWebAppException = e;
        }
        return -1;
    }

    protected synchronized byte[] getBuffer( T t,
                                             Class<?> type,
                                             Type genericType,
                                             Annotation[] annotations,
                                             MediaType mediaType,
                                             MultivaluedMap<String, Object> httpHeaders )
            throws IOException, WebApplicationException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        wrapped.writeTo( t, type, genericType, annotations, mediaType, httpHeaders, baos );
        return baos.toByteArray();
    }
}
