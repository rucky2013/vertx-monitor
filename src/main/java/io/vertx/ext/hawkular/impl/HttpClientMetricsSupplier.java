/*
 * Copyright 2015 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.ext.hawkular.impl;

import io.vertx.core.net.SocketAddress;
import io.vertx.ext.hawkular.impl.HttpClientConnectionsMeasurements.Snapshot;
import org.hawkular.metrics.client.common.MetricType;
import org.hawkular.metrics.client.common.SingleMetric;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import static org.hawkular.metrics.client.common.MetricType.*;

/**
 * Aggregates values from {@link HttpClientMetricsImpl} instances and exposes metrics for collection.
 *
 * @author Thomas Segismont
 */
public class HttpClientMetricsSupplier implements MetricSupplier {
  private final String baseName;
  private final Set<HttpClientMetricsImpl> metricsSet = new CopyOnWriteArraySet<>();

  public HttpClientMetricsSupplier(String prefix) {
    baseName = prefix + (prefix.isEmpty() ? "" : ".") + "vertx.http.client.";
  }

  @Override
  public List<SingleMetric> collect() {
    long timestamp = System.currentTimeMillis();

    Map<SocketAddress, Snapshot> values = new HashMap<>();

    for (HttpClientMetricsImpl httpClientMetrics : metricsSet) {
      httpClientMetrics.getMeasurementsSnapshot().forEach((address, snapshot) -> {
        values.merge(address, snapshot, Snapshot::merge);
      });
    }

    List<SingleMetric> res = new ArrayList<>();

    values.forEach((address, snapshot) -> {
      String addressId = address.host() + ":" + address.port();
      // TCP metrics
      res.add(metric(addressId + ".connections", timestamp, snapshot.getConnections(), GAUGE));
      res.add(metric(addressId + ".bytesReceived", timestamp, snapshot.getBytesReceived(), COUNTER));
      res.add(metric(addressId + ".bytesSent", timestamp, snapshot.getBytesSent(), COUNTER));
      res.add(metric(addressId + ".errorCount", timestamp, snapshot.getErrorCount(), COUNTER));
      // HTTP metrics
      res.add(metric(addressId + ".requests", timestamp, snapshot.getRequests(), GAUGE));
      res.add(metric(addressId + ".requestCount", timestamp, snapshot.getRequestCount(), COUNTER));
      res.add(metric(addressId + ".responseTime", timestamp, snapshot.getResponseTime(), COUNTER));
      res.add(metric(addressId + ".wsConnections", timestamp, snapshot.getWsConnections(), GAUGE));
    });
    return res;
  }

  private SingleMetric metric(String name, long timestamp, Number value, MetricType type) {
    return new SingleMetric(baseName + name, timestamp, value.doubleValue(), type);
  }

  public void register(HttpClientMetricsImpl httpClientMetrics) {
    metricsSet.add(httpClientMetrics);
  }

  public void unregister(HttpClientMetricsImpl httpClientMetrics) {
    metricsSet.remove(httpClientMetrics);
  }
}
