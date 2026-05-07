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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  private final TsFileProperties tsFileProperties;
  private final FileService fileService;
  private final TsFileDataReader dataReader;

  public DataService(
      TsFileProperties tsFileProperties, FileService fileService, TsFileDataReader dataReader) {
    this.tsFileProperties = tsFileProperties;
    this.fileService = fileService;
    this.dataReader = dataReader;
  }

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
                  filters.getOffset() + filters.getLimit(),
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

      // Apply pagination
      int total = filteredData.size();
      int offset = filters.getOffset();
      int limit = filters.getLimit();

      List<DataRow> pagedData;
      if (offset >= total) {
        pagedData = new ArrayList<>();
      } else {
        int endIndex = Math.min(offset + limit, total);
        pagedData = filteredData.subList(offset, endIndex);
      }

      boolean hasMore = offset + limit < total;

      return DataPreviewResponse.builder()
          .data(pagedData)
          .total(total)
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

      for (Map.Entry<String, List<DataRow>> deviceEntry : deviceGroups.entrySet()) {
        String device = deviceEntry.getKey();
        List<DataRow> deviceData = deviceEntry.getValue();

        for (String measurement : request.getMeasurements()) {
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
      Object value = row.getMeasurements().get(measurement);
      if (value instanceof Number number) {
        points.add(new double[] {row.getTimestamp(), number.doubleValue()});
      }
    }
    return points;
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
