/*
 * Copyright The Hypertrace Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.hypertrace.agent.smoketest;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.jar.Attributes;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.ResponseBody;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonProcessingException;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

public abstract class AbstractSmokeTest {

  private static final Logger log = LoggerFactory.getLogger(OpenTelemetryStorage.class);
  private static final String OTEL_COLLECTOR_IMAGE = "otel/opentelemetry-collector:0.96.0";
  private static final String MOCK_BACKEND_IMAGE =
      "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-fake-backend:20221127.3559314891";
  private static final String NETWORK_ALIAS_OTEL_COLLECTOR = "collector";
  private static final String NETWORK_ALIAS_OTEL_MOCK_STORAGE = "backend";
  private static final String OTEL_EXPORTER_ENDPOINT =
      String.format("http://%s:5442", NETWORK_ALIAS_OTEL_COLLECTOR);

  // note - with OTEL 1.13, the value of this manifest property is specified
  // as the version in the InstrumentationLibrary class.
  public static final Attributes.Name OTEL_INSTRUMENTATION_VERSION_MANIFEST_PROP =
      new Attributes.Name("OpenTelemetry-Instrumentation-Version");

  public static final String OTEL_LIBRARY_VERSION_ATTRIBUTE = "otel.library.version";
  public static final String agentPath = getPropertyOrEnv("smoketest.javaagent.path");

  private static final Network network = Network.newNetwork();
  private static OpenTelemetryCollector collector;
  private static OpenTelemetryStorage openTelemetryStorage;

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final int DEFAULT_TIMEOUT_SECONDS = 30;
  protected OkHttpClient client =
      new OkHttpClient.Builder()
          .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
          .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
          .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
          .followRedirects(true)
          .build();

  @BeforeAll
  public static void beforeAll() {
    openTelemetryStorage =
        new OpenTelemetryStorage(MOCK_BACKEND_IMAGE)
            .withImagePullPolicy(PullPolicy.alwaysPull())
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/health").forPort(8080))
            .withNetwork(network)
            .withNetworkAliases(NETWORK_ALIAS_OTEL_MOCK_STORAGE)
            .withLogConsumer(new Slf4jLogConsumer(log));
    openTelemetryStorage.start();

    collector =
        new OpenTelemetryCollector(OTEL_COLLECTOR_IMAGE)
            .withImagePullPolicy(PullPolicy.alwaysPull())
            .withNetwork(network)
            .withNetworkAliases(NETWORK_ALIAS_OTEL_COLLECTOR)
            .withLogConsumer(new Slf4jLogConsumer(log))
            .dependsOn(openTelemetryStorage)
            .withCopyFileToContainer(
                MountableFile.forClasspathResource("/otel.yaml"), "/etc/otel.yaml")
            .withCommand("--config /etc/otel.yaml");
    collector.start();
  }

  @AfterAll
  public static void afterAll() {
    collector.close();
    openTelemetryStorage.close();
    network.close();
  }

  @AfterEach
  void cleanData() throws IOException {
    client
        .newCall(
            new Request.Builder()
                .url(String.format("http://localhost:%d/clear", openTelemetryStorage.getPort()))
                .build())
        .execute()
        .close();
  }

  protected abstract String getTargetImage(int jdk);

  GenericContainer createAppUnderTest(int jdk) {
    if (agentPath == null || agentPath.isEmpty()) {
      throw new IllegalStateException(
          "agentPath is not set, configure it via env var SMOKETEST_JAVAAGENT_PATH");
    }
    log.info("Agent path {}", agentPath);
    return new GenericContainer<>(DockerImageName.parse(getTargetImage(jdk)))
        .withExposedPorts(8080)
        .withNetwork(network)
        .withLogConsumer(new Slf4jLogConsumer(log))
        .withCopyFileToContainer(MountableFile.forHostPath(agentPath), "/javaagent.jar")
        .withCopyFileToContainer(
            MountableFile.forClasspathResource("/ht-config.yaml"), "/etc/ht-config.yaml")
        .withEnv("JAVA_TOOL_OPTIONS", "-javaagent:/javaagent.jar")
        .withEnv("HT_CONFIG_FILE", "/etc/ht-config.yaml")
        .withEnv("OTEL_BSP_MAX_EXPORT_BATCH_SIZE", "1")
        .withEnv("OTEL_BSP_SCHEDULE_DELAY", "10")
        .withEnv("HT_REPORTING_ENDPOINT", OTEL_EXPORTER_ENDPOINT)
        .withEnv("HT_REPORTING_METRIC_ENDPOINT", OTEL_EXPORTER_ENDPOINT);
  }

  protected static int countSpansByName(
      Collection<ExportTraceServiceRequest> traces, String spanName) {
    return (int) getSpanStream(traces).filter(it -> it.getName().equals(spanName)).count();
  }

  protected static Stream<Span> getSpanStream(Collection<ExportTraceServiceRequest> traceRequest) {
    return getInstrumentationLibSpanStream(traceRequest)
        .flatMap(librarySpans -> librarySpans.getSpansList().stream());
  }

  protected static Stream<ScopeSpans> getInstrumentationLibSpanStream(
      Collection<ExportTraceServiceRequest> traceRequest) {
    return traceRequest.stream()
        .flatMap(request -> request.getResourceSpansList().stream())
        .flatMap(resourceSpans -> resourceSpans.getScopeSpansList().stream());
  }

  protected Collection<ExportTraceServiceRequest> waitForTraces(final int count) {
    return Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .until(
            this::waitForTraces,
            exportTraceServiceRequests -> exportTraceServiceRequests.size() == count);
  }

  protected Collection<ExportTraceServiceRequest> waitForTraces() throws IOException {
    String content = waitForContent("get-traces");

    return StreamSupport.stream(OBJECT_MAPPER.readTree(content).spliterator(), false)
        .map(
            it -> {
              ExportTraceServiceRequest.Builder builder = ExportTraceServiceRequest.newBuilder();
              try {
                JsonFormat.parser().merge(OBJECT_MAPPER.writeValueAsString(it), builder);
              } catch (InvalidProtocolBufferException | JsonProcessingException e) {
                e.printStackTrace();
              }
              return builder.build();
            })
        .collect(Collectors.toList());
  }

  protected Collection<ExportMetricsServiceRequest> waitForMetrics() throws IOException {
    String content = waitForContent("get-metrics");

    return StreamSupport.stream(OBJECT_MAPPER.readTree(content).spliterator(), false)
        .map(
            it -> {
              ExportMetricsServiceRequest.Builder builder =
                  ExportMetricsServiceRequest.newBuilder();
              try {
                JsonFormat.parser().merge(OBJECT_MAPPER.writeValueAsString(it), builder);
              } catch (InvalidProtocolBufferException | JsonProcessingException e) {
                e.printStackTrace();
              }
              return builder.build();
            })
        .collect(Collectors.toList());
  }

  private String waitForContent(String path) throws IOException {
    Request request =
        new Builder()
            .url(String.format("http://localhost:%d/%s", openTelemetryStorage.getPort(), path))
            .build();
    // TODO do not use local vars in lambda
    final AtomicLong previousSize = new AtomicLong();
    Awaitility.waitAtMost(Duration.ofSeconds(60))
        .until(
            () -> {
              try (ResponseBody body = client.newCall(request).execute().body()) {
                String content = body.string();
                if (content.length() > "[]".length() && content.length() == previousSize.get()) {
                  return true;
                }
                previousSize.set(content.length());
                log.debug("Current content size {}", previousSize.get());
              }
              return false;
            });
    return client.newCall(request).execute().body().string();
  }

  protected boolean hasMetricNamed(
      String metricName, Collection<ExportMetricsServiceRequest> metricRequests) {
    return metricRequests.stream()
        .flatMap(metricRequest -> metricRequest.getResourceMetricsList().stream())
        .flatMap(resourceMetrics -> resourceMetrics.getScopeMetricsList().stream())
        .flatMap(
            instrumentationLibraryMetrics ->
                instrumentationLibraryMetrics.getMetricsList().stream())
        .anyMatch(metric -> metric.getName().equals(metricName));
  }

  // Checks if a metric with the given name contains the specified attribute
  protected boolean hasMetricWithAttribute(
      String metricName,
      String attributeName,
      Collection<ExportMetricsServiceRequest> metricRequests) {

    return metricRequests.stream()
        .flatMap(metricRequest -> metricRequest.getResourceMetricsList().stream())
        .flatMap(resourceMetrics -> resourceMetrics.getScopeMetricsList().stream())
        .flatMap(scopeMetrics -> scopeMetrics.getMetricsList().stream())
        .filter(metric -> metric.getName().equals(metricName))
        .anyMatch(metric -> metricHasAttribute(metric, attributeName));
  }

  private boolean metricHasAttribute(Metric metric, String attributeName) {
    switch (metric.getDataCase()) {
      case GAUGE:
        return metric.getGauge().getDataPointsList().stream()
            .anyMatch(dataPoint -> hasAttribute(dataPoint.getAttributesList(), attributeName));
      case SUM:
        return metric.getSum().getDataPointsList().stream()
            .anyMatch(dataPoint -> hasAttribute(dataPoint.getAttributesList(), attributeName));
      case HISTOGRAM:
        return metric.getHistogram().getDataPointsList().stream()
            .anyMatch(dataPoint -> hasAttribute(dataPoint.getAttributesList(), attributeName));
      case EXPONENTIAL_HISTOGRAM:
        return metric.getExponentialHistogram().getDataPointsList().stream()
            .anyMatch(dataPoint -> hasAttribute(dataPoint.getAttributesList(), attributeName));
      case SUMMARY:
        return metric.getSummary().getDataPointsList().stream()
            .anyMatch(dataPoint -> hasAttribute(dataPoint.getAttributesList(), attributeName));
      default:
        return false;
    }
  }

  private boolean hasAttribute(List<KeyValue> attributes, String attributeName) {
    return attributes.stream().anyMatch(attribute -> attribute.getKey().equals(attributeName));
  }

  public static String getPropertyOrEnv(String propName) {
    String property = System.getProperty(propName);
    if (property != null && !property.isEmpty()) {
      return property;
    }
    return System.getenv(propName.toUpperCase().replaceAll("\\.", "_"));
  }
}
