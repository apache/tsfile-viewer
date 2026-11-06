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

package org.apache.tsfile.viewer.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Boundary tests for the pagination envelope and its parameter rules.
 *
 * <p>Validates: offset default/negative/overflow, limit default/cap, hasMore correctness.
 */
class PageDTOTest {

  @Test
  @DisplayName("offset is 0 when missing or negative")
  void shouldNormalizeOffset() {
    assertThat(PageDTO.normalizeOffset(null)).isZero();
    assertThat(PageDTO.normalizeOffset(0)).isZero();
    assertThat(PageDTO.normalizeOffset(-1)).isZero();
    assertThat(PageDTO.normalizeOffset(-1000)).isZero();
    assertThat(PageDTO.normalizeOffset(7)).isEqualTo(7);
  }

  @Test
  @DisplayName("limit defaults to 50 and is capped at 200")
  void shouldNormalizeLimit() {
    assertThat(PageDTO.normalizeLimit(null)).isEqualTo(PageDTO.DEFAULT_LIMIT).isEqualTo(50);
    assertThat(PageDTO.normalizeLimit(0)).isEqualTo(50);
    assertThat(PageDTO.normalizeLimit(-5)).isEqualTo(50);
    assertThat(PageDTO.normalizeLimit(1)).isEqualTo(1);
    assertThat(PageDTO.normalizeLimit(50)).isEqualTo(50);
    assertThat(PageDTO.normalizeLimit(200)).isEqualTo(200);
    assertThat(PageDTO.normalizeLimit(201)).isEqualTo(PageDTO.MAX_LIMIT).isEqualTo(200);
    assertThat(PageDTO.normalizeLimit(Integer.MAX_VALUE)).isEqualTo(200);
  }

  @Test
  @DisplayName("first page reports hasMore when elements remain")
  void shouldReportHasMoreOnFirstPage() {
    PageDTO<String> page = PageDTO.of(items(50), 100, 0, 50);

    assertThat(page.getOffset()).isZero();
    assertThat(page.getLimit()).isEqualTo(50);
    assertThat(page.getTotal()).isEqualTo(100);
    assertThat(page.getItems()).hasSize(50);
    assertThat(page.isHasMore()).isTrue();
  }

  @Test
  @DisplayName("last page reports no more elements when it ends exactly at total")
  void shouldReportNoMoreAtExactBoundary() {
    PageDTO<String> page = PageDTO.of(items(50), 100, 50, 50);

    assertThat(page.isHasMore()).isFalse();
  }

  @Test
  @DisplayName("partial page before the end still reports hasMore")
  void shouldReportHasMoreOnPartialPage() {
    PageDTO<String> page = PageDTO.of(items(3), 10, 7, 50);

    assertThat(page.getItems()).hasSize(3);
    assertThat(page.isHasMore()).isFalse();

    PageDTO<String> shorterPage = PageDTO.of(items(2), 10, 6, 2);
    assertThat(shorterPage.isHasMore()).isTrue();
  }

  @Test
  @DisplayName("offset beyond total yields an empty page with the real total")
  void shouldReturnEmptyPageBeyondTotal() {
    PageDTO<String> page = PageDTO.of(List.of(), 87_488, 87_488, 50);

    assertThat(page.getItems()).isEmpty();
    assertThat(page.getTotal()).isEqualTo(87_488);
    assertThat(page.getOffset()).isEqualTo(87_488);
    assertThat(page.getLimit()).isEqualTo(50);
    assertThat(page.isHasMore()).isFalse();
  }

  @Test
  @DisplayName("far offset never overflows into hasMore=true")
  void shouldNotOverflowOnHugeOffset() {
    PageDTO<String> page = PageDTO.of(List.of(), 10, Integer.MAX_VALUE, 200);

    assertThat(page.isHasMore()).isFalse();
  }

  @Test
  @DisplayName("null items are treated as an empty page")
  void shouldTreatNullItemsAsEmpty() {
    PageDTO<String> page = PageDTO.of(null, 0, 0, 50);

    assertThat(page.getItems()).isEmpty();
    assertThat(page.getTotal()).isZero();
    assertThat(page.isHasMore()).isFalse();
  }

  private static List<String> items(int count) {
    return java.util.stream.IntStream.range(0, count).mapToObj(i -> "item-" + i).toList();
  }
}
