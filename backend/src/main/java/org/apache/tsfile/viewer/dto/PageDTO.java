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

import java.util.List;

/**
 * Generic page envelope used by the paginated metadata endpoints.
 *
 * <p>Serializes as {@code {"items": [...], "total": n, "offset": n, "limit": n, "hasMore": bool}}.
 *
 * @param <T> element type of one page
 */
public class PageDTO<T> {

  /** Page size applied when the client does not ask for one. */
  public static final int DEFAULT_LIMIT = 50;

  /** Hard cap of a single request, keeping one page's memory footprint bounded. */
  public static final int MAX_LIMIT = 200;

  private List<T> items;
  private int total;
  private int offset;
  private int limit;
  private boolean hasMore;

  /** Default constructor for JSON deserialization. */
  public PageDTO() {}

  /**
   * Creates a page envelope.
   *
   * @param items page elements
   * @param total total number of elements across all pages
   * @param offset offset of the first element of this page
   * @param limit page size requested for this page
   * @param hasMore whether elements beyond this page exist
   */
  public PageDTO(List<T> items, int total, int offset, int limit, boolean hasMore) {
    this.items = items;
    this.total = total;
    this.offset = offset;
    this.limit = limit;
    this.hasMore = hasMore;
  }

  /**
   * Builds a page envelope, deriving {@code hasMore} from the payload.
   *
   * @param items page elements (null is treated as an empty page)
   * @param total total number of elements across all pages
   * @param offset offset of the first element of this page
   * @param limit page size requested for this page
   * @param <T> element type
   * @return page envelope
   */
  public static <T> PageDTO<T> of(List<T> items, int total, int offset, int limit) {
    List<T> safeItems = items == null ? List.of() : items;
    int safeTotal = Math.max(total, 0);
    boolean hasMore = (long) offset + safeItems.size() < safeTotal;
    return new PageDTO<>(safeItems, safeTotal, offset, limit, hasMore);
  }

  /**
   * Normalizes a requested offset: missing or negative values become 0.
   *
   * @param offset raw request value
   * @return non-negative offset
   */
  public static int normalizeOffset(Integer offset) {
    return offset == null || offset < 0 ? 0 : offset;
  }

  /**
   * Normalizes a requested limit: missing or non-positive values fall back to {@link
   * #DEFAULT_LIMIT}, values above {@link #MAX_LIMIT} are capped to it.
   *
   * @param limit raw request value
   * @return effective page size in {@code [1, MAX_LIMIT]}
   */
  public static int normalizeLimit(Integer limit) {
    if (limit == null || limit <= 0) {
      return DEFAULT_LIMIT;
    }
    return Math.min(limit, MAX_LIMIT);
  }

  public List<T> getItems() {
    return items;
  }

  public void setItems(List<T> items) {
    this.items = items;
  }

  public int getTotal() {
    return total;
  }

  public void setTotal(int total) {
    this.total = total;
  }

  public int getOffset() {
    return offset;
  }

  public void setOffset(int offset) {
    this.offset = offset;
  }

  public int getLimit() {
    return limit;
  }

  public void setLimit(int limit) {
    this.limit = limit;
  }

  public boolean isHasMore() {
    return hasMore;
  }

  public void setHasMore(boolean hasMore) {
    this.hasMore = hasMore;
  }
}
