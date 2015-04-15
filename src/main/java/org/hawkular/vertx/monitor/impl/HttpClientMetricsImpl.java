/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hawkular.vertx.monitor.impl;

import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.WebSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.metrics.HttpClientMetrics;

/**
 * @author Thomas Segismont
 */
public class HttpClientMetricsImpl implements HttpClientMetrics<Void, Void, Void> {
    @Override
    public Void requestBegin(Void socketMetric, SocketAddress localAddress, SocketAddress remoteAddress,
        HttpClientRequest request) {
        return null;
    }

    @Override
    public void responseEnd(Void requestMetric, HttpClientResponse response) {
    }

    @Override
    public Void connected(Void socketMetric, WebSocket webSocket) {
        return null;
    }

    @Override
    public void disconnected(Void webSocketMetric) {
    }

    @Override
    public Void connected(SocketAddress remoteAddress) {
        return null;
    }

    @Override
    public void disconnected(Void socketMetric, SocketAddress remoteAddress) {
    }

    @Override
    public void bytesRead(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
    }

    @Override
    public void bytesWritten(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
    }

    @Override
    public void exceptionOccurred(Void socketMetric, SocketAddress remoteAddress, Throwable t) {
    }

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public void close() {
    }
}