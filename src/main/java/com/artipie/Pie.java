/*
 * MIT License
 *
 * Copyright (c) 2020 Artipie
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.artipie;

import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.http.Connection;
import com.artipie.http.Response;
import com.artipie.http.Slice;
import com.artipie.http.rq.RequestLineFrom;
import com.artipie.http.rs.RsWithStatus;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow.Publisher;

/**
 * Pie of slices.
 * @since 0.1
 * @todo #12:30min Implement slice resolving strategy
 *  based on yaml configuration file. Now StupSlice
 *  is used instead of real slice implementation.
 *  We should parse publisher of bytes into yaml
 *  config, construct ASTO from this config and find
 *  corresponding slice implementation by type parameter.
 * @checkstyle MagicNumberCheck (500 lines)
 * @checkstyle ReturnCountCheck (500 lines)
 */
public final class Pie implements Slice {

    /**
     * Configuration storage.
     */
    private final Storage cfg;

    /**
     * Ctro.
     * @param cfg Configuration
     */
    public Pie(final Storage cfg) {
        this.cfg = cfg;
    }

    @Override
    @SuppressWarnings("PMD.OnlyOneReturn")
    public Response response(final String line, final Iterable<Map.Entry<String, String>> headers,
        final Publisher<ByteBuffer> body) {
        final URI uri;
        try {
            uri = new RequestLineFrom(line).uri();
        } catch (final IOException err) {
            return new RsWithStatus(400);
        }
        if (uri.getPath().equals("*")) {
            return new RsWithStatus(200);
        }
        final String[] path = uri.getPath().split("/");
        if (path.length == 0) {
            return new RsWithStatus(200);
        }
        final String repo = path[0];
        return new AsyncSlice(
            this.cfg.value(new Key.From(repo)).thenApply(something -> new SliceStub())
        ).response(line, headers, body);
    }

    /**
     * Slice stub.
     * @since 1.0
     */
    private static final class SliceStub implements Slice {

        @Override
        public Response response(final String line,
            final Iterable<Entry<String, String>> headers,
            final Publisher<ByteBuffer> body) {
            return new RsWithStatus(404);
        }
    }

    /**
     * Async slice.
     * @since 1.0
     */
    private static final class AsyncSlice implements Slice {

        /**
         * Async slice.
         */
        private final CompletionStage<Slice> slice;

        /**
         * Ctor.
         * @param slice Async slice.
         */
        AsyncSlice(final CompletionStage<Slice> slice) {
            this.slice = slice;
        }

        @Override
        public Response response(final String line,
            final Iterable<Entry<String, String>> headers,
            final Publisher<ByteBuffer> body) {
            return new RsAsync(
                this.slice.thenApply(target -> target.response(line, headers, body))
            );
        }
    }

    /**
     * Async response.
     * @since 1.0
     */
    private static final class RsAsync implements Response {

        /**
         * Async response.
         */
        private final CompletionStage<Response> rsp;

        /**
         * Ctor.
         * @param rsp Response
         */
        RsAsync(final CompletionStage<Response> rsp) {
            this.rsp = rsp;
        }

        @Override
        public void send(final Connection con) {
            this.rsp.thenAccept(target -> target.send(con));
        }
    }
}

