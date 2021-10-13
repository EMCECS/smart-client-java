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

import java.io.IOException;
import java.io.InputStream;

/**
 * A simple delegating class to constrain an input stream to a certain size.  The stream will act as though it has
 * reached its end when the specified size has been read.
 */
public class SizedInputStream extends InputStream {
    private final InputStream source;
    private final long size;
    private long read = 0;

    public SizedInputStream(InputStream source, long size) {
        this.source = source;
        this.size = size;
    }

    public long getSize() {
        return size;
    }

    public long getRead() {
        return read;
    }

    @Override
    public int read() throws IOException {
        byte[] single = new byte[1];
        return read(single) == -1 ? -1 : (int) single[0] & 0xFF;
    }

    @Override
    public int read(byte[] bytes) throws IOException {
        return read(bytes, 0, bytes.length);
    }

    @Override
    public int read(byte[] bytes, int off, int len) throws IOException {
        long remaining = remaining();
        if (remaining <= 0) return -1;
        if (len > remaining) len = (int) remaining;
        int count = source.read(bytes, off, len);
        if (count != -1) read += count;
        return count;
    }

    @Override
    public long skip(long len) throws IOException {
        long remaining = remaining();
        if (remaining <= 0) return -1;
        if (len > remaining) len = remaining;
        long count = source.skip(len);
        if (count != -1) read += count;
        return count;
    }

    @Override
    public int available() throws IOException {
        int available = source.available();
        return available > remaining() ? (int) remaining() : available;
    }

    @Override
    public void close() throws IOException {
        source.close();
    }

    @Override
    public void mark(int i) {
        source.mark(i);
    }

    @Override
    public void reset() throws IOException {
        source.reset();
    }

    @Override
    public boolean markSupported() {
        return source.markSupported();
    }

    protected long remaining() {
        return size - read;
    }
}
