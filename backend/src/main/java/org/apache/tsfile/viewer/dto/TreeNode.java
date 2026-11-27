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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO representing a node in the file system tree.
 *
 * <p>Used for lazy-loading directory tree navigation. Children are only populated when a directory
 * node is expanded.
 *
 * <p>Validates: Requirement 1.1, 1.2, 8.1 (File tree, lazy loading)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TreeNode {

  private String name;
  private String path;

  @JsonProperty("isDirectory")
  private boolean isDirectory;

  private List<TreeNode> children;

  @JsonProperty("isLoaded")
  private boolean isLoaded;

  /** File size in bytes. {@code null} for directories, which are omitted from the JSON. */
  private Long size;

  /** Default constructor for JSON deserialization. */
  public TreeNode() {}

  /**
   * Creates a new tree node with basic information.
   *
   * @param name the file or directory name
   * @param path the full path to the file or directory
   * @param isDirectory true if this node represents a directory
   */
  public TreeNode(String name, String path, boolean isDirectory) {
    this.name = name;
    this.path = path;
    this.isDirectory = isDirectory;
    this.isLoaded = !isDirectory; // Files are always "loaded", directories need expansion
  }

  /**
   * Creates a new tree node with children.
   *
   * @param name the file or directory name
   * @param path the full path to the file or directory
   * @param isDirectory true if this node represents a directory
   * @param children list of child nodes (for directories)
   */
  public TreeNode(String name, String path, boolean isDirectory, List<TreeNode> children) {
    this(name, path, isDirectory);
    this.children = children;
    this.isLoaded = children != null;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public boolean isDirectory() {
    return isDirectory;
  }

  public void setDirectory(boolean directory) {
    isDirectory = directory;
  }

  public List<TreeNode> getChildren() {
    return children;
  }

  public void setChildren(List<TreeNode> children) {
    this.children = children;
  }

  public boolean isLoaded() {
    return isLoaded;
  }

  public void setLoaded(boolean loaded) {
    isLoaded = loaded;
  }

  public Long getSize() {
    return size;
  }

  public void setSize(Long size) {
    this.size = size;
  }

  /** Builder class for creating TreeNode instances. */
  public static class Builder {
    private String name;
    private String path;
    private boolean isDirectory;
    private List<TreeNode> children;
    private boolean isLoaded;
    private Long size;

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder path(String path) {
      this.path = path;
      return this;
    }

    public Builder directory(boolean isDirectory) {
      this.isDirectory = isDirectory;
      return this;
    }

    public Builder children(List<TreeNode> children) {
      this.children = children;
      return this;
    }

    public Builder loaded(boolean isLoaded) {
      this.isLoaded = isLoaded;
      return this;
    }

    public Builder size(Long size) {
      this.size = size;
      return this;
    }

    public TreeNode build() {
      TreeNode node = new TreeNode();
      node.setName(name);
      node.setPath(path);
      node.setDirectory(isDirectory);
      node.setChildren(children);
      node.setLoaded(isLoaded);
      node.setSize(size);
      return node;
    }
  }

  public static Builder builder() {
    return new Builder();
  }
}
