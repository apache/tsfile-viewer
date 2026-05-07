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

import type { AggregationType, ChartDataResponse, TimeRange } from "@/api/tsfile/types";

/**
 * Chart Store - 图表状态管理
 */
import { ref } from "vue";

import { defineStore } from "pinia";

export interface ChartConfig {
  series: Array<{
    data: number[][];
    name: string;
  }>;
  timeRange: TimeRange;
  totalPoints: number;
  downsampled: boolean;
}

export const useChartStore = defineStore("tsfile-chart", () => {
  const chartData = ref<ChartConfig | null>(null);
  const selectedMeasurements = ref<string[]>([]);
  const aggregationType = ref<AggregationType | null>(null);
  const windowSize = ref<null | number>(null);
  const maxPoints = ref<number>(10_000);
  const loading = ref<boolean>(false);
  const error = ref<null | string>(null);

  function setChartData(data: ChartDataResponse) {
    chartData.value = {
      series: data.series,
      timeRange: data.timeRange,
      totalPoints: data.totalPoints,
      downsampled: data.downsampled,
    };
  }

  function setSelectedMeasurements(measurements: string[]) {
    selectedMeasurements.value = measurements;
  }

  function setAggregation(type: AggregationType | null, window: null | number) {
    aggregationType.value = type;
    windowSize.value = window;
  }

  function setMaxPoints(points: number) {
    maxPoints.value = points;
  }

  function setLoading(isLoading: boolean) {
    loading.value = isLoading;
  }

  function setError(errorMessage: null | string) {
    error.value = errorMessage;
  }

  function buildEChartsOption() {
    if (!chartData.value) return null;

    return {
      title: {
        text: "TsFile Data Visualization",
        left: "center",
      },
      tooltip: {
        trigger: "axis",
        axisPointer: {
          type: "cross",
        },
      },
      legend: {
        data: chartData.value.series.map((s) => s.name),
        top: 30,
        type: "scroll",
      },
      grid: {
        left: "3%",
        right: "4%",
        bottom: "15%",
        top: "15%",
        containLabel: true,
      },
      toolbox: {
        feature: {
          dataZoom: {
            yAxisIndex: "none",
          },
          restore: {},
          saveAsImage: {},
        },
      },
      xAxis: {
        type: "time",
      },
      yAxis: {
        type: "value",
      },
      dataZoom: [
        {
          type: "inside",
          start: 0,
          end: 100,
        },
        {
          start: 0,
          end: 100,
        },
      ],
      series: chartData.value.series.map((s) => ({
        name: s.name,
        type: "line",
        data: s.data,
        smooth: true,
        emphasis: {
          focus: "series",
        },
        showSymbol: false,
      })),
    };
  }

  function reset() {
    chartData.value = null;
    selectedMeasurements.value = [];
    aggregationType.value = null;
    windowSize.value = null;
    maxPoints.value = 10_000;
    loading.value = false;
    error.value = null;
  }

  return {
    chartData,
    selectedMeasurements,
    aggregationType,
    windowSize,
    maxPoints,
    loading,
    error,
    setChartData,
    setSelectedMeasurements,
    setAggregation,
    setMaxPoints,
    setLoading,
    setError,
    buildEChartsOption,
    reset,
  };
});
