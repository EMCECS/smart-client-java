/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.rest.util;

import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class StreamUtil {
    private static final Logger l4j = Logger.getLogger(StreamUtil.class);

    /**
     * Closes streams no matter what.
     */
    public static String readAsString(InputStream inputStream) throws IOException {
        if (inputStream == null) return null;
        try {
            return new java.util.Scanner(inputStream, "UTF-8").useDelimiter("\\A").next();
        } catch (java.util.NoSuchElementException e) {
            return "";
        } finally {
            try {
                inputStream.close();
            } catch (Throwable t) {
                l4j.warn("could not close stream", t);
            }
        }
    }

    /**
     * Closes streams no matter what.
     */
    public static long copy(InputStream is, OutputStream os, long maxBytes) throws IOException {
        byte[] buffer = new byte[1024 * 64]; // 64k buffer
        long count = 0;
        int read, maxRead;

        try {
            while (count < maxBytes) {
                maxRead = (int) Math.min((long) buffer.length, maxBytes - count);
                if (-1 == (read = is.read(buffer, 0, maxRead))) break;
                os.write(buffer, 0, read);
                count += read;
            }
        } finally {
            try {
                is.close();
            } catch (Throwable t) {
                l4j.warn("could not close stream", t);
            }
            try {
                os.close();
            } catch (Throwable t) {
                l4j.warn("could not close stream", t);
            }
        }
        return count;
    }

    private StreamUtil() {
    }
}
