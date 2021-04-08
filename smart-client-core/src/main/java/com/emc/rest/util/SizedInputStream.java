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
package com.emc.rest.util;

import java.io.IOException;
import java.io.InputStream;

/**
 * A simple delegating class to constrain an input stream to a certain size.  The stream will act as though it has
 * reached its end when the specified size has been read.
 */
public class SizedInputStream extends InputStream {
    private InputStream source;
    private long size;
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
