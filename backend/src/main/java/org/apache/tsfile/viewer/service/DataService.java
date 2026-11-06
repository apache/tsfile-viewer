/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.tsfile.viewer.service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import org.apache.tsfile.viewer.config.TsFileProperties;
import org.apache.tsfile.viewer.dto.AdvancedCondition;
import org.apache.tsfile.viewer.dto.AggregationType;
import org.apache.tsfile.viewer.dto.ChartDataRequest;
import org.apache.tsfile.viewer.dto.ChartDataResponse;
import org.apache.tsfile.viewer.dto.ChartSeries;
import org.apache.tsfile.viewer.dto.DataPreviewRequest;
import org.apache.tsfile.viewer.dto.DataPreviewResponse;
import org.apache.tsfile.viewer.dto.DataRow;
import org.apache.tsfile.viewer.dto.ExportFormat;
import org.apache.tsfile.viewer.dto.FilterConditions;
import org.apache.tsfile.viewer.dto.TimeRange;
import org.apache.tsfile.viewer.dto.ValueRange;
import org.apache.tsfile.viewer.exception.AccessDeniedException;
import org.apache.tsfile.viewer.exception.QueryTimeoutException;
import org.apache.tsfile.viewer.exception.TsFileNotFoundException;
import org.apache.tsfile.viewer.tsfile.TsFileDataReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service for data query operations.
 *
 * <p>Provides functionality for:
 *
 * <ul>
 *   <li>Paginated data preview with filtering
 *   <li>Chart data queries with aggregation
 *   <li>Data downsampling for visualization
 * </ul>
 *
 * <p>Validates: Requirement 3.1-3.6, 4.1-4.4, 5.5 (Data filtering, chart data, timeout)
 */
@Service
public class DataService {

  private static final Logger logger = LoggerFactory.getLogger(DataService.class);
  private static final int DEFAULT_MAX_POINTS = 1000;

  /**
   * Hard upper bound on the number of chart series built per request. Acts as a performance safety
   * net so a request that selects thousands of measurements (e.g. a wide table model file) cannot
   * make the backend build and serialize an unbounded number of series. The frontend additionally
   * refuses to render beyond its own (lower) display threshold.
   */
  private static final int MAX_CHART_SERIES = 200;

  /**
   * Page size used while streaming an export. The reader is asked for one page at a time, so peak
   * memory depends on this constant rather than on the size of the file being exported.
   */
  private static final int EXPORT_PAGE_SIZE = 2000;

  /** Buffer size of the export writer; matched to a typical socket/TCP window size. */
  private static final int EXPORT_BUFFER_SIZE = 64 * 1024;

  /**
   * Timestamp layout of the CSV export: ISO-8601 in UTC with millisecond precision, i.e. what the
   * browser produced with {@code new Date(ms).toISOString()}.
   */
  private static final DateTimeFormatter EXPORT_TIMESTAMP_FORMAT =
      DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

  private static final String CSV_COLUMN_SEPARATOR = ",";
  private static final String CSV_LINE_SEPARATOR = "\n";
  private static final String CSV_TIMESTAMP_HEADER = "Timestamp";
  private static final String CSV_DEVICE_HEADER = "Device";

  private final TsFileProperties tsFileProperties;
  private final FileService fileService;
  private final TsFileDataReader dataReader;

  /**
   * ObjectMapper for the JSON export. The application context provides no ObjectMapper bean, so one
   * is owned here just like {@link ScanService} does for its SSE payloads.
   */
  private final ObjectMapper objectMapper = new ObjectMapper();

  public DataService(
      TsFileProperties tsFileProperties, FileService fileService, TsFileDataReader dataReader) {
    this.tsFileProperties = tsFileProperties;
    this.fileService = fileService;
    this.dataReader = dataReader;
  }

  /**
   * Everything an export needs that can be resolved before a single byte is written.
   *
   * <p>Resolved up front so that an unknown file id (404) or a forbidden path (403) still produces
   * a regular JSON error response instead of a download that breaks halfway.
   *
   * @param format the requested representation
   * @param filePath the resolved absolute path of the TsFile
   * @param filters the filters to apply, with {@code limit}/{@code offset} ignored
   * @param columns the CSV measurement columns, in dictionary order (unused for JSON)
   */
  public record ExportPlan(
      ExportFormat format, String filePath, FilterConditions filters, List<String> columns) {}

  /**
   * Previews data from a TsFile with filtering and pagination.
   *
   * @param request the data preview request
   * @return DataPreviewResponse with paginated data
   * @throws TsFileNotFoundException if the file is not found
   * @throws AccessDeniedException if the path is not allowed
   * @throws IOException if the file cannot be read
   * @throws QueryTimeoutException if the query times out
   */
  public DataPreviewResponse previewData(DataPreviewRequest request)
      throws TsFileNotFoundException, AccessDeniedException, IOException, QueryTimeoutException {
    logger.debug("Previewing data for fileId={}", request.getFileId());

    String filePath = fileService.getFilePath(request.getFileId());
    FilterConditions filters = request.toFilterConditions();

    long startTime = System.currentTimeMillis();
    int timeoutSeconds = tsFileProperties.getQuery().getTimeoutSeconds();

    try {
      // Determine read limit: if no post-read filters are needed, only read offset+limit rows
      // to avoid loading the entire dataset into memory (OOM risk for large files).
      //
      // The extra +1 row is what makes hasMore meaningful: reading exactly offset+limit rows
      // makes the result indistinguishable from "the dataset ends here", which pinned the UI
      // to a single page. The extra row is probed only, never returned.
      boolean hasPostReadFilters =
          (filters.getDevices() != null && !filters.getDevices().isEmpty())
              || (filters.getMeasurements() != null && !filters.getMeasurements().isEmpty())
              || (filters.getValueRange() != null && filters.getValueRange().hasBounds())
              || (filters.getAdvancedConditions() != null
                  && !filters.getAdvancedConditions().isEmpty());
      int readLimit =
          hasPostReadFilters
              ? tsFileProperties.getQuery().getMaxResultSize()
              : Math.min(
                  filters.getOffset() + filters.getLimit() + 1,
                  tsFileProperties.getQuery().getMaxResultSize());

      // Read data with filters using file path
      var readResult =
          dataReader.readDataByTimeRange(
              new java.io.File(filePath),
              filters.getTimeRange() != null ? filters.getTimeRange().getStartTime() : null,
              filters.getTimeRange() != null ? filters.getTimeRange().getEndTime() : null,
              readLimit,
              0);

      // Check timeout
      checkTimeout(startTime, timeoutSeconds);

      // Convert to DTO DataRows
      List<DataRow> allData = convertToDataRows(readResult.getData());

      // Apply additional filters
      List<DataRow> filteredData = applyFilters(allData, filters);

      // Check timeout again
      checkTimeout(startTime, timeoutSeconds);

      // Apply pagination.
      //
      // `rowsRead` is how many rows this query actually materialized, which is NOT the size of
      // the dataset: without post-read filters the reader stops at offset+limit+1 rows. It equals
      // the true filtered total only once the reader ran dry (hasMore == false); while hasMore is
      // true it is merely a lower bound. Clients must not derive a page count from it.
      int rowsRead = filteredData.size();
      int offset = filters.getOffset();
      int limit = filters.getLimit();

      List<DataRow> pagedData;
      if (offset >= rowsRead) {
        pagedData = new ArrayList<>();
      } else {
        int endIndex = Math.min(offset + limit, rowsRead);
        pagedData = filteredData.subList(offset, endIndex);
      }

      boolean hasMore = offset + limit < rowsRead;

      return DataPreviewResponse.builder()
          .data(pagedData)
          .total(rowsRead)
          .limit(limit)
          .offset(offset)
          .hasMore(hasMore)
          .warnings(readResult.getWarnings())
          .build();

    } catch (java.util.concurrent.TimeoutException e) {
      throw new QueryTimeoutException("Query timed out after " + timeoutSeconds + " seconds");
    }
  }

  /**
   * Resolves everything an export needs before the first byte is written.
   *
   * @param request the data preview request carrying the filters; its {@code limit} and {@code
   *     offset} are ignored on purpose, an export always covers every matching row
   * @param format the requested representation
   * @return the resolved export plan
   * @throws TsFileNotFoundException if the file is not found
   * @throws AccessDeniedException if the path is not allowed
   * @throws IOException if the file metadata cannot be read
   */
  public ExportPlan prepareExport(DataPreviewRequest request, ExportFormat format)
      throws TsFileNotFoundException, AccessDeniedException, IOException {
    String filePath = fileService.getFilePath(request.getFileId());
    FilterConditions filters = request.toFilterConditions();

    // CSV columns are the union of the file's measurements in dictionary order; when the user
    // narrowed the selection to specific measurements only those survive, so the export does not
    // grow columns that are empty for every row.
    List<String> columns = new ArrayList<>(dataReader.getAllColumnNames(new File(filePath)));
    if (filters.getMeasurements() != null && !filters.getMeasurements().isEmpty()) {
      Set<String> selected = new HashSet<>(filters.getMeasurements());
      columns.removeIf(column -> !selected.contains(column));
    }
    columns.sort(Comparator.naturalOrder());

    return new ExportPlan(format, filePath, filters, columns);
  }

  /**
   * Streams every row matching the plan's filters as CSV or JSON.
   *
   * <p>Rows are read one page at a time with the page offset pushed down into the reader, filtered
   * per page and written right away, so neither the file nor the result is ever materialized in
   * memory. That is also why an export is not capped by {@code tsfile.query.max-result-size}: the
   * property bounds a single read (one preview page), not the number of pages streamed here.
   *
   * @param plan the resolved export plan
   * @param outputStream the stream to write to, flushed after every page
   * @return the number of data rows written, excluding the CSV header
   * @throws IOException if writing fails
   */
  public long exportData(ExportPlan plan, OutputStream outputStream) throws IOException {
    File file = new File(plan.filePath());
    TimeRange timeRange = plan.filters().getTimeRange();
    Long startTime = timeRange != null ? timeRange.getStartTime() : null;
    Long endTime = timeRange != null ? timeRange.getEndTime() : null;

    long startedAt = System.currentTimeMillis();
    long exportedRows = 0;
    int rawOffset = 0;
    boolean firstJsonRow = true;
    Set<String> warnings = new LinkedHashSet<>();

    Writer writer =
        new BufferedWriter(
            new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), EXPORT_BUFFER_SIZE);

    if (plan.format() == ExportFormat.CSV) {
      writeCsvHeader(writer, plan.columns());
    } else {
      writer.write("[");
    }

    while (true) {
      var page =
          dataReader.readDataByTimeRange(file, startTime, endTime, EXPORT_PAGE_SIZE, rawOffset);
      warnings.addAll(page.getWarnings());

      // Exactly the filters the preview applies, so the export contains the rows it shows.
      List<DataRow> rows = applyFilters(convertToDataRows(page.getData()), plan.filters());

      for (DataRow row : rows) {
        if (plan.format() == ExportFormat.CSV) {
          writeCsvRow(writer, row, plan.columns());
        } else {
          writer.write(firstJsonRow ? "\n" : ",\n");
          writer.write(objectMapper.writeValueAsString(row));
          firstJsonRow = false;
        }
      }
      exportedRows += rows.size();

      // Flush after every page so the browser starts receiving data immediately.
      writer.flush();
      outputStream.flush();

      rawOffset += page.getData().size();
      if (!page.hasMore() || page.getData().isEmpty()) {
        break;
      }
    }

    if (plan.format() == ExportFormat.JSON) {
      writer.write(firstJsonRow ? "]\n" : "\n]\n");
    }
    writer.flush();

    if (!warnings.isEmpty()) {
      logger.warn("Export of {} finished with warnings: {}", plan.filePath(), warnings);
    }
    logger.info(
        "Exported {} rows as {} from {} in {} ms",
        exportedRows,
        plan.format(),
        plan.filePath(),
        System.currentTimeMillis() - startedAt);
    return exportedRows;
  }

  /** Writes the CSV header line: {@code Timestamp,Device,<measurement columns...>}. */
  private static void writeCsvHeader(Writer writer, List<String> columns) throws IOException {
    StringBuilder line =
        new StringBuilder(CSV_TIMESTAMP_HEADER)
            .append(CSV_COLUMN_SEPARATOR)
            .append(CSV_DEVICE_HEADER);
    for (String column : columns) {
      line.append(CSV_COLUMN_SEPARATOR).append(escapeCsv(column));
    }
    writer.write(line.append(CSV_LINE_SEPARATOR).toString());
  }

  /** Writes one CSV data line; a value missing from a row becomes an empty field. */
  private static void writeCsvRow(Writer writer, DataRow row, List<String> columns)
      throws IOException {
    Map<String, Object> measurements = row.getMeasurements();
    StringBuilder line = new StringBuilder(64);
    line.append(formatTimestamp(row.getTimestamp()))
        .append(CSV_COLUMN_SEPARATOR)
        .append(escapeCsv(row.getDevice()));
    for (String column : columns) {
      Object value = measurements != null ? measurements.get(column) : null;
      line.append(CSV_COLUMN_SEPARATOR).append(escapeCsv(csvText(value)));
    }
    writer.write(line.append(CSV_LINE_SEPARATOR).toString());
  }

  private static String csvText(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  /**
   * Makes a field safe for CSV: a value containing a separator, a quote or a line break is wrapped
   * in double quotes with every embedded quote doubled (RFC 4180).
   */
  static String escapeCsv(String value) {
    if (value == null) {
      return "";
    }
    boolean needsQuoting =
        value.indexOf(',') >= 0
            || value.indexOf('"') >= 0
            || value.indexOf('\n') >= 0
            || value.indexOf('\r') >= 0;
    return needsQuoting ? '"' + value.replace("\"", "\"\"") + '"' : value;
  }

  /**
   * Formats a timestamp as ISO-8601 in UTC with millisecond precision, i.e. the same text the
   * export used to produce in the browser with {@code new Date(ms).toISOString()}.
   */
  static String formatTimestamp(long timestamp) {
    try {
      return EXPORT_TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(normalizeToMillis(timestamp)));
    } catch (DateTimeException e) {
      return Long.toString(timestamp);
    }
  }

  /**
   * Normalizes a TsFile timestamp of unknown precision to milliseconds.
   *
   * <p>TsFile keeps whatever unit the writer chose (s/ms/us/ns) and the value's magnitude is the
   * only hint available, exactly as in the frontend's {@code normalizeToMs}.
   */
  static long normalizeToMillis(long timestamp) {
    long magnitude = Math.abs(timestamp);
    if (magnitude >= 100_000_000_000_000_000L) {
      return timestamp / 1_000_000L; // nanoseconds
    }
    if (magnitude >= 100_000_000_000_000L) {
      return timestamp / 1_000L; // microseconds
    }
    if (magnitude >= 100_000_000_000L) {
      return timestamp; // milliseconds
    }
    if (magnitude >= 100_000_000L) {
      return timestamp * 1_000L; // seconds
    }
    return timestamp; // anything else is treated as milliseconds
  }

  /**
   * Queries chart data from a TsFile with optional aggregation.
   *
   * @param request the chart data request
   * @return ChartDataResponse with series data
   * @throws TsFileNotFoundException if the file is not found
   * @throws AccessDeniedException if the path is not allowed
   * @throws IOException if the file cannot be read
   * @throws QueryTimeoutException if the query times out
   */
  public ChartDataResponse queryChartData(ChartDataRequest request)
      throws TsFileNotFoundException, AccessDeniedException, IOException, QueryTimeoutException {
    logger.debug("Querying chart data for fileId={}", request.getFileId());

    String filePath = fileService.getFilePath(request.getFileId());

    long startTime = System.currentTimeMillis();
    int timeoutSeconds = tsFileProperties.getQuery().getTimeoutSeconds();

    try {
      // Read data using file path
      var readResult =
          dataReader.readDataByTimeRange(
              new java.io.File(filePath),
              request.getStartTime(),
              request.getEndTime(),
              tsFileProperties.getQuery().getMaxResultSize(),
              0);

      // Check timeout
      checkTimeout(startTime, timeoutSeconds);

      // Convert to DTO DataRows
      List<DataRow> allData = convertToDataRows(readResult.getData());

      // Filter by table name if specified (device IDs start with tableName.)
      if (request.getTableName() != null && !request.getTableName().isEmpty()) {
        String prefix = request.getTableName() + ".";
        String exact = request.getTableName();
        allData =
            allData.stream()
                .filter(
                    row ->
                        row.getDevice() != null
                            && (row.getDevice().equals(exact)
                                || row.getDevice().startsWith(prefix)))
                .toList();
      }

      // Apply device filter if specified
      if (request.getDevices() != null && !request.getDevices().isEmpty()) {
        allData = applyDeviceFilter(allData, request.getDevices());
      }

      // Group data by device
      Map<String, List<DataRow>> deviceGroups = new java.util.LinkedHashMap<>();
      for (DataRow row : allData) {
        deviceGroups.computeIfAbsent(row.getDevice(), k -> new ArrayList<>()).add(row);
      }

      // Build series for each device+measurement combination
      List<ChartSeries> series = new ArrayList<>();
      int totalPoints = 0;
      boolean downsampled = false;

      int maxPoints = request.getMaxPoints() != null ? request.getMaxPoints() : DEFAULT_MAX_POINTS;

      boolean seriesCapped = false;
      outer:
      for (Map.Entry<String, List<DataRow>> deviceEntry : deviceGroups.entrySet()) {
        String device = deviceEntry.getKey();
        List<DataRow> deviceData = deviceEntry.getValue();

        for (String measurement : request.getMeasurements()) {
          // Performance safety net: never build more than MAX_CHART_SERIES series.
          if (series.size() >= MAX_CHART_SERIES) {
            seriesCapped = true;
            break outer;
          }

          List<double[]> dataPoints = extractMeasurementData(deviceData, measurement);
          if (dataPoints.isEmpty()) continue;

          // Apply aggregation if specified
          if (request.getAggregation() != null && request.getWindowSize() != null) {
            dataPoints =
                aggregateData(dataPoints, request.getAggregation(), request.getWindowSize());
          }

          // Downsample if needed
          if (dataPoints.size() > maxPoints) {
            dataPoints = downsampleData(dataPoints, maxPoints);
            downsampled = true;
          }

          // Always include device in series name: "device / measurement"
          String seriesName = device + " / " + measurement;

          totalPoints += dataPoints.size();
          series.add(ChartSeries.builder().name(seriesName).data(dataPoints).build());

          // Check timeout
          checkTimeout(startTime, timeoutSeconds);
        }
      }

      if (seriesCapped) {
        logger.warn(
            "Chart series capped at {} for fileId={} (requested {} measurements across {} devices)",
            MAX_CHART_SERIES,
            request.getFileId(),
            request.getMeasurements().size(),
            deviceGroups.size());
      }

      // Calculate time range
      TimeRange timeRange = calculateTimeRange(allData);

      return ChartDataResponse.builder()
          .series(series)
          .timeRange(timeRange)
          .totalPoints(totalPoints)
          .downsampled(downsampled)
          .build();

    } catch (java.util.concurrent.TimeoutException e) {
      throw new QueryTimeoutException("Query timed out after " + timeoutSeconds + " seconds");
    }
  }

  /**
   * Converts TsFileDataReader.DataRow to DTO DataRow.
   *
   * @param readerRows the reader data rows
   * @return list of DTO data rows
   */
  private List<DataRow> convertToDataRows(List<TsFileDataReader.DataRow> readerRows) {
    List<DataRow> result = new ArrayList<>();
    for (TsFileDataReader.DataRow row : readerRows) {
      result.add(
          DataRow.builder()
              .timestamp(row.getTimestamp())
              .device(row.getTableName())
              .measurements(row.getValues())
              .build());
    }
    return result;
  }

  /**
   * Applies all filters to the data.
   *
   * @param data the data to filter
   * @param filters the filter conditions
   * @return filtered data
   */
  private List<DataRow> applyFilters(List<DataRow> data, FilterConditions filters) {
    List<DataRow> result = data;

    if (filters.getDevices() != null && !filters.getDevices().isEmpty()) {
      result = applyDeviceFilter(result, filters.getDevices());
    }

    if (filters.getMeasurements() != null && !filters.getMeasurements().isEmpty()) {
      result = applyMeasurementFilter(result, filters.getMeasurements());
    }

    if (filters.getValueRange() != null && filters.getValueRange().hasBounds()) {
      result = applyValueFilter(result, filters.getValueRange());
    }

    if (filters.getAdvancedConditions() != null && !filters.getAdvancedConditions().isEmpty()) {
      result = applyAdvancedConditions(result, filters.getAdvancedConditions());
    }

    return result;
  }

  /**
   * Filters data by device list.
   *
   * @param data the data to filter
   * @param devices list of allowed devices
   * @return filtered data
   */
  private List<DataRow> applyDeviceFilter(List<DataRow> data, List<String> devices) {
    return data.stream().filter(row -> devices.contains(row.getDevice())).toList();
  }

  /**
   * Filters data by measurement list.
   *
   * @param data the data to filter
   * @param measurements list of allowed measurements
   * @return filtered data with only specified measurements
   */
  private List<DataRow> applyMeasurementFilter(List<DataRow> data, List<String> measurements) {
    return data.stream()
        .map(
            row -> {
              Map<String, Object> filteredMeasurements = new HashMap<>();
              for (String measurement : measurements) {
                if (row.getMeasurements().containsKey(measurement)) {
                  filteredMeasurements.put(measurement, row.getMeasurements().get(measurement));
                }
              }
              return DataRow.builder()
                  .timestamp(row.getTimestamp())
                  .device(row.getDevice())
                  .measurements(filteredMeasurements)
                  .build();
            })
        .filter(row -> !row.getMeasurements().isEmpty())
        .toList();
  }

  /**
   * Filters data by value range.
   *
   * @param data the data to filter
   * @param valueRange the value range filter
   * @return filtered data
   */
  private List<DataRow> applyValueFilter(List<DataRow> data, ValueRange valueRange) {
    return data.stream()
        .filter(
            row -> {
              for (Object value : row.getMeasurements().values()) {
                if (value instanceof Number number) {
                  if (valueRange.contains(number.doubleValue())) {
                    return true;
                  }
                }
              }
              return false;
            })
        .toList();
  }

  /**
   * Extracts data points for a specific measurement.
   *
   * @param data the data rows
   * @param measurement the measurement name
   * @return list of [timestamp, value] pairs
   */
  private List<double[]> extractMeasurementData(List<DataRow> data, String measurement) {
    List<double[]> points = new ArrayList<>();
    for (DataRow row : data) {
      Double value = toChartValue(row.getMeasurements().get(measurement));
      if (value != null) {
        points.add(new double[] {row.getTimestamp(), value});
      }
    }
    return points;
  }

  /**
   * Converts a measurement value into a number that can be plotted.
   *
   * <p>BOOLEAN fields are mapped to 0/1. Without this, a table whose only field is a boolean (e.g.
   * a fault flag) yields no series at all, so the chart stays empty while the data preview shows
   * the rows.
   *
   * @param value the measurement value
   * @return the numeric value, or null when the value cannot be plotted
   */
  private Double toChartValue(Object value) {
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    if (value instanceof Boolean bool) {
      return bool ? 1.0d : 0.0d;
    }
    return null;
  }

  /**
   * Aggregates data points using the specified aggregation type.
   *
   * @param data the data points
   * @param aggregationType the aggregation type
   * @param windowSize the window size in milliseconds
   * @return aggregated data points
   */
  private List<double[]> aggregateData(
      List<double[]> data, AggregationType aggregationType, int windowSize) {
    if (data.isEmpty() || windowSize <= 0) {
      return data;
    }

    List<double[]> result = new ArrayList<>();
    double windowStart = data.get(0)[0];
    List<Double> windowValues = new ArrayList<>();

    for (double[] point : data) {
      if (point[0] >= windowStart + windowSize) {
        // Emit aggregated value for current window
        if (!windowValues.isEmpty()) {
          double aggregatedValue = aggregate(windowValues, aggregationType);
          result.add(new double[] {windowStart + windowSize / 2.0, aggregatedValue});
        }
        // Start new window
        windowStart = point[0];
        windowValues.clear();
      }
      windowValues.add(point[1]);
    }

    // Emit last window
    if (!windowValues.isEmpty()) {
      double aggregatedValue = aggregate(windowValues, aggregationType);
      result.add(new double[] {windowStart + windowSize / 2.0, aggregatedValue});
    }

    return result;
  }

  /**
   * Computes the aggregate value for a list of values.
   *
   * @param values the values to aggregate
   * @param aggregationType the aggregation type
   * @return the aggregated value
   */
  private double aggregate(List<Double> values, AggregationType aggregationType) {
    return switch (aggregationType) {
      case MIN -> values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
      case MAX -> values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
      case AVG -> values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
      case COUNT -> values.size();
    };
  }

  /**
   * Downsamples data using LTTB (Largest Triangle Three Buckets) algorithm.
   *
   * @param data the data points
   * @param targetPoints target number of points
   * @return downsampled data
   */
  private List<double[]> downsampleData(List<double[]> data, int targetPoints) {
    if (data.size() <= targetPoints) {
      return data;
    }

    List<double[]> result = new ArrayList<>();

    // Always keep first point
    result.add(data.get(0));

    // Calculate bucket size
    double bucketSize = (double) (data.size() - 2) / (targetPoints - 2);

    int a = 0; // Previous selected point index

    for (int i = 0; i < targetPoints - 2; i++) {
      // Calculate bucket boundaries
      int bucketStart = (int) Math.floor((i + 1) * bucketSize) + 1;
      int bucketEnd = (int) Math.floor((i + 2) * bucketSize) + 1;
      if (bucketEnd > data.size() - 1) {
        bucketEnd = data.size() - 1;
      }

      // Calculate average point in next bucket
      double avgX = 0;
      double avgY = 0;
      int nextBucketStart = bucketEnd;
      int nextBucketEnd = (int) Math.floor((i + 3) * bucketSize) + 1;
      if (nextBucketEnd > data.size() - 1) {
        nextBucketEnd = data.size() - 1;
      }
      int nextBucketSize = nextBucketEnd - nextBucketStart;
      if (nextBucketSize > 0) {
        for (int j = nextBucketStart; j < nextBucketEnd; j++) {
          avgX += data.get(j)[0];
          avgY += data.get(j)[1];
        }
        avgX /= nextBucketSize;
        avgY /= nextBucketSize;
      } else {
        avgX = data.get(data.size() - 1)[0];
        avgY = data.get(data.size() - 1)[1];
      }

      // Find point in current bucket with largest triangle area
      double maxArea = -1;
      int maxAreaIndex = bucketStart;

      double[] pointA = data.get(a);

      for (int j = bucketStart; j < bucketEnd; j++) {
        double[] pointB = data.get(j);
        double area =
            Math.abs(
                    (pointA[0] - avgX) * (pointB[1] - pointA[1])
                        - (pointA[0] - pointB[0]) * (avgY - pointA[1]))
                / 2.0;
        if (area > maxArea) {
          maxArea = area;
          maxAreaIndex = j;
        }
      }

      result.add(data.get(maxAreaIndex));
      a = maxAreaIndex;
    }

    // Always keep last point
    result.add(data.get(data.size() - 1));

    return result;
  }

  /**
   * Calculates the time range from data rows.
   *
   * @param data the data rows
   * @return time range
   */
  private TimeRange calculateTimeRange(List<DataRow> data) {
    if (data.isEmpty()) {
      return new TimeRange(0, 0);
    }

    long minTime = Long.MAX_VALUE;
    long maxTime = Long.MIN_VALUE;

    for (DataRow row : data) {
      if (row.getTimestamp() < minTime) {
        minTime = row.getTimestamp();
      }
      if (row.getTimestamp() > maxTime) {
        maxTime = row.getTimestamp();
      }
    }

    return new TimeRange(minTime, maxTime);
  }

  /**
   * Checks if the query has timed out.
   *
   * @param startTime the query start time
   * @param timeoutSeconds the timeout in seconds
   * @throws TimeoutException if the query has timed out
   */
  private void checkTimeout(long startTime, int timeoutSeconds) throws TimeoutException {
    long elapsed = System.currentTimeMillis() - startTime;
    if (elapsed > timeoutSeconds * 1000L) {
      throw new TimeoutException("Query timed out");
    }
  }

  /**
   * Applies advanced filter conditions to the data.
   *
   * @param data the data to filter
   * @param conditions the advanced conditions
   * @return filtered data
   */
  private List<DataRow> applyAdvancedConditions(
      List<DataRow> data, List<AdvancedCondition> conditions) {
    return data.stream()
        .filter(
            row -> ConditionEvaluator.matchesAdvancedConditions(row.getMeasurements(), conditions))
        .toList();
  }
}
