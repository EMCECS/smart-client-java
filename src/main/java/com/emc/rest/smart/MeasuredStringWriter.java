/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.rest.smart;

import com.sun.jersey.core.impl.provider.entity.StringProvider;

import javax.ws.rs.Produces;

@Produces( {"text/plain", "*/*"} )
public class MeasuredStringWriter extends MeasuredMessageBodyWriter<String> {
    private static final StringProvider provider = new StringProvider();

    public MeasuredStringWriter() {
        super( provider );
    }
}
