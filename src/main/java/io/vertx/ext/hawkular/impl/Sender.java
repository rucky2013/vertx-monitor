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

import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.hawkular.VertxHawkularOptions;
import org.hawkular.metrics.client.common.Batcher;
import org.hawkular.metrics.client.common.MetricType;
import org.hawkular.metrics.client.common.SingleMetric;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.concurrent.TimeUnit.*;

/**
 * Sends collected metrics to the Hawkular server.
 *
 * @author Thomas Segismont
 */
public class Sender implements Handler<List<SingleMetric>> {
  private static final Logger LOG = LoggerFactory.getLogger(Sender.class);

  private final Vertx vertx;
  private final String host;
  private final int port;
  private final String metricsURI;
  private final String tenant;
  private final int batchSize;
  private final long batchDelay;
  private final List<SingleMetric> queue;

  private HttpClient httpClient;
  private long timerId;

  private long sendTime;

  /**
   * @param vertx   the {@link Vertx} managed instance
   * @param options Vertx Hawkular options
   * @param context the metric collection and sending execution context
   */
  public Sender(Vertx vertx, VertxHawkularOptions options, Context context) {
    this.vertx = vertx;
    host = options.getHost();
    port = options.getPort();
    metricsURI = options.getMetricsServiceUri() + "/gauges/data";
    tenant = options.getTenant();
    batchSize = options.getBatchSize();
    batchDelay = NANOSECONDS.convert(options.getBatchDelay(), SECONDS);
    queue = new ArrayList<>(batchSize);
    context.runOnContext(aVoid -> {
      httpClient = vertx.createHttpClient(options.getHttpOptions());
      timerId = vertx.setPeriodic(MILLISECONDS.convert(batchDelay, NANOSECONDS), this::flushIfIdle);

      // Configure the metrics bridge. It just transforms the received metrics (json) to a Single Metric to enqueue it.
      if (options.isMetricsBridgeEnabled() && options.getMetricsBridgeAddress() != null) {
        vertx.eventBus().consumer(options.getMetricsBridgeAddress(), message -> {
          // By spec, it is a json object.
          JsonObject json = (JsonObject) message.body();

          // source and value has to be set.
          // the timestamp can have been set in the message using the 'timestamp' field. If not use 'now'
          // the type of metrics can have been set in the message using the 'type' field. It not use 'gauge'. Only
          // "counter" and "gauge" are supported.
          SingleMetric metric = new SingleMetric(json.getString("source"),
              json.getLong("timestamp", System.currentTimeMillis()),
              json.getDouble("value"),
              "counter".equals(json.getString("type", "")) ? MetricType.COUNTER : MetricType.GAUGE);

          context.runOnContext(v -> handle(Collections.singletonList(metric)));
        });
      }
    });
    sendTime = System.nanoTime();
  }

  @Override
  public void handle(List<SingleMetric> metrics) {
    if (queue.size() + metrics.size() < batchSize) {
      queue.addAll(metrics);
      return;
    }
    List<SingleMetric> temp = new ArrayList<>(queue.size() + metrics.size());
    temp.addAll(queue);
    temp.addAll(metrics);
    queue.clear();
    do {
      List<SingleMetric> subList = temp.subList(0, batchSize);
      send(subList);
      subList.clear();
    } while (temp.size() >= batchSize);
    queue.addAll(temp);
  }

  private void send(List<SingleMetric> metrics) {
    String json = Batcher.metricListToJson(metrics);
    Buffer buffer = Buffer.buffer(json);
    HttpClientRequest req = httpClient.post(port, host, metricsURI, this::onResponse);
    req.putHeader("Content-Length", String.valueOf(buffer.length()));
    req.putHeader("Content-Type", "application/json");
    req.putHeader("Hawkular-Tenant", tenant);
    req.exceptionHandler(err -> LOG.trace("Could not send metrics", err));
    req.write(buffer);
    req.end();
    sendTime = System.nanoTime();
  }

  private void onResponse(HttpClientResponse response) {
    if (response.statusCode() != 200 && LOG.isTraceEnabled()) {
      response.bodyHandler(msg -> {
        LOG.trace("Could not send metrics: " + response.statusCode() + " : " + msg.toString());
      });
    }
  }

  private void flushIfIdle(Long timerId) {
    if (System.nanoTime() - sendTime > batchDelay && !queue.isEmpty()) {
      send(queue);
      queue.clear();
    }
  }

  public void stop() {
    vertx.cancelTimer(timerId);
    httpClient.close();
  }
}
