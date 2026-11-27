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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.tsfile.viewer.config.TsFileProperties;
import org.apache.tsfile.viewer.service.PathValidationService.PathNotAllowedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests for {@link PathValidationService}.
 *
 * <p>Validates: Requirement 7.6 (Directory whitelist security)
 */
@ExtendWith(MockitoExtension.class)
class PathValidationServiceTest {

  @Mock private TsFileProperties tsFileProperties;

  private PathValidationService pathValidationService;

  @BeforeEach
  void setUp() {
    pathValidationService = new PathValidationService(tsFileProperties);
  }

  @Nested
  @DisplayName("isPathAllowed tests")
  class IsPathAllowedTests {

    @Test
    @DisplayName("Should allow path within allowed directory")
    void shouldAllowPathWithinAllowedDirectory() {
      when(tsFileProperties.getAllowedDirectories())
          .thenReturn(Arrays.asList("/data/tsfiles", "/uploads/tsfiles"));

      boolean result = pathValidationService.isPathAllowed("/data/tsfiles/test.tsfile");

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should allow path in subdirectory of allowed directory")
    void shouldAllowPathInSubdirectory() {
      when(tsFileProperties.getAllowedDirectories())
          .thenReturn(Arrays.asList("/data/tsfiles", "/uploads/tsfiles"));

      boolean result = pathValidationService.isPathAllowed("/data/tsfiles/subdir/test.tsfile");

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should reject path outside allowed directories")
    void shouldRejectPathOutsideAllowedDirectories() {
      when(tsFileProperties.getAllowedDirectories())
          .thenReturn(Arrays.asList("/data/tsfiles", "/uploads/tsfiles"));

      boolean result = pathValidationService.isPathAllowed("/etc/passwd");

      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should reject null path")
    void shouldRejectNullPath() {
      boolean result = pathValidationService.isPathAllowed(null);

      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should reject empty path")
    void shouldRejectEmptyPath() {
      boolean result = pathValidationService.isPathAllowed("");

      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should reject blank path")
    void shouldRejectBlankPath() {
      boolean result = pathValidationService.isPathAllowed("   ");

      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should reject path with parent directory traversal")
    void shouldRejectPathWithParentTraversal() {
      // Path traversal is detected before checking allowed directories
      boolean result = pathValidationService.isPathAllowed("/data/tsfiles/../../../etc/passwd");

      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should reject path with double dot in middle")
    void shouldRejectPathWithDoubleDotInMiddle() {
      // Path traversal is detected before checking allowed directories
      boolean result =
          pathValidationService.isPathAllowed("/data/tsfiles/subdir/../../../etc/passwd");

      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should reject path with URL-encoded traversal")
    void shouldRejectPathWithUrlEncodedTraversal() {
      // URL-encoded path traversal is detected before checking allowed directories
      boolean result = pathValidationService.isPathAllowed("/data/tsfiles/%2e%2e/etc/passwd");

      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should reject when no allowed directories configured")
    void shouldRejectWhenNoAllowedDirectoriesConfigured() {
      when(tsFileProperties.getAllowedDirectories()).thenReturn(Collections.emptyList());

      boolean result = pathValidationService.isPathAllowed("/data/tsfiles/test.tsfile");

      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should reject when allowed directories is null")
    void shouldRejectWhenAllowedDirectoriesIsNull() {
      when(tsFileProperties.getAllowedDirectories()).thenReturn(null);

      boolean result = pathValidationService.isPathAllowed("/data/tsfiles/test.tsfile");

      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should allow exact match of allowed directory")
    void shouldAllowExactMatchOfAllowedDirectory() {
      when(tsFileProperties.getAllowedDirectories())
          .thenReturn(Arrays.asList("/data/tsfiles", "/uploads/tsfiles"));

      boolean result = pathValidationService.isPathAllowed("/data/tsfiles");

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should reject path that starts with allowed directory name but is different")
    void shouldRejectPathThatStartsWithAllowedDirectoryNameButIsDifferent() {
      when(tsFileProperties.getAllowedDirectories()).thenReturn(List.of("/data/tsfiles"));

      // /data/tsfiles-other is NOT a subdirectory of /data/tsfiles
      boolean result = pathValidationService.isPathAllowed("/data/tsfiles-other/test.tsfile");

      assertThat(result).isFalse();
    }
  }

  @Nested
  @DisplayName("validatePath tests")
  class ValidatePathTests {

    @Test
    @DisplayName("Should not throw for valid path")
    void shouldNotThrowForValidPath() throws PathNotAllowedException {
      when(tsFileProperties.getAllowedDirectories())
          .thenReturn(Arrays.asList("/data/tsfiles", "/uploads/tsfiles"));

      // Should not throw
      pathValidationService.validatePath("/data/tsfiles/test.tsfile");
    }

    @Test
    @DisplayName("Should throw PathNotAllowedException for invalid path")
    void shouldThrowForInvalidPath() {
      when(tsFileProperties.getAllowedDirectories())
          .thenReturn(Arrays.asList("/data/tsfiles", "/uploads/tsfiles"));

      assertThatThrownBy(() -> pathValidationService.validatePath("/etc/passwd"))
          .isInstanceOf(PathNotAllowedException.class)
          .hasMessageContaining("Access denied")
          .hasMessageContaining("/etc/passwd");
    }

    @Test
    @DisplayName("Should throw PathNotAllowedException for path traversal")
    void shouldThrowForPathTraversal() {
      // Path traversal is detected before checking allowed directories
      assertThatThrownBy(
              () -> pathValidationService.validatePath("/data/tsfiles/../../../etc/passwd"))
          .isInstanceOf(PathNotAllowedException.class)
          .hasMessageContaining("Access denied");
    }
  }

  @Nested
  @DisplayName("getAllowedDirectories tests")
  class GetAllowedDirectoriesTests {

    @Test
    @DisplayName("Should return allowed directories from properties")
    void shouldReturnAllowedDirectoriesFromProperties() {
      List<String> directories = Arrays.asList("/data/tsfiles", "/uploads/tsfiles");
      when(tsFileProperties.getAllowedDirectories()).thenReturn(directories);

      List<String> result = pathValidationService.getAllowedDirectories();

      assertThat(result).containsExactly("/data/tsfiles", "/uploads/tsfiles");
    }
  }

  @Nested
  @DisplayName("Upload directory tests")
  class UploadDirectoryTests {

    @Test
    @DisplayName("Should return true when upload directory is configured")
    void shouldReturnTrueWhenUploadDirectoryConfigured() {
      when(tsFileProperties.getUploadDirectory()).thenReturn("/uploads/tsfiles");

      boolean result = pathValidationService.isUploadDirectoryConfigured();

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should return false when upload directory is null")
    void shouldReturnFalseWhenUploadDirectoryIsNull() {
      when(tsFileProperties.getUploadDirectory()).thenReturn(null);

      boolean result = pathValidationService.isUploadDirectoryConfigured();

      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should return false when upload directory is blank")
    void shouldReturnFalseWhenUploadDirectoryIsBlank() {
      when(tsFileProperties.getUploadDirectory()).thenReturn("   ");

      boolean result = pathValidationService.isUploadDirectoryConfigured();

      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should return upload directory from properties")
    void shouldReturnUploadDirectoryFromProperties() {
      when(tsFileProperties.getUploadDirectory()).thenReturn("/uploads/tsfiles");

      String result = pathValidationService.getUploadDirectory();

      assertThat(result).isEqualTo("/uploads/tsfiles");
    }

    @Test
    @DisplayName("Should return true when upload directory is within allowed directories")
    void shouldReturnTrueWhenUploadDirectoryIsAllowed() {
      when(tsFileProperties.getUploadDirectory()).thenReturn("/uploads/tsfiles");
      when(tsFileProperties.getAllowedDirectories())
          .thenReturn(Arrays.asList("/data/tsfiles", "/uploads/tsfiles"));

      boolean result = pathValidationService.isUploadDirectoryAllowed();

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should return false when upload directory is not within allowed directories")
    void shouldReturnFalseWhenUploadDirectoryIsNotAllowed() {
      when(tsFileProperties.getUploadDirectory()).thenReturn("/other/uploads");
      when(tsFileProperties.getAllowedDirectories())
          .thenReturn(Arrays.asList("/data/tsfiles", "/uploads/tsfiles"));

      boolean result = pathValidationService.isUploadDirectoryAllowed();

      assertThat(result).isFalse();
    }
  }
}
