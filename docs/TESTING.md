# TsFile Viewer Testing Guide

This document describes the testing strategy and how to run tests for the TsFile Viewer application.

## Overview

The project includes comprehensive tests for both backend and frontend:

- **Backend**: 131 JUnit 5 tests covering utilities, services, and controllers
- **Frontend**: Vitest tests for components, stores, and API client

## Backend Testing

### Test Coverage

The backend has 131 passing tests covering:

1. **TsFile Utilities** (`tsfile/` package)
   - TsFileParser: Metadata extraction, measurement parsing, RowGroup/Chunk parsing
   - TsFileDataReader: Data reading with filters, pagination, time range queries
   - TsFileReaderCache: Cache behavior, LRU eviction, concurrent access

2. **Service Layer** (`service/` package)
   - FileService: File tree browsing, upload validation, path validation
   - MetadataService: Metadata caching, parsing, retrieval
   - DataService: Data filtering, aggregation, downsampling (LTTB algorithm)

3. **Controller Layer** (`controller/` package)
   - FileController: File tree endpoint, upload endpoint
   - MetadataController: Metadata retrieval endpoint
   - DataController: Data preview and chart query endpoints

4. **Error Handling**
   - GlobalExceptionHandler: Error response formatting
   - Validation errors, file not found, timeout handling
   - HTTP status code correctness

### Running Backend Tests

```bash
cd backend

# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=TsFileReaderCacheTest

# Run tests with coverage
mvn test jacoco:report

# Skip tests during build
mvn clean package -DskipTests
```

### Test Configuration

Tests use:
- **JUnit 5**: Test framework
- **Mockito**: Mocking framework
- **AssertJ**: Fluent assertions
- **Spring Boot Test**: Integration testing support
- **MockMvc**: Controller testing

### Example Test

```java
@SpringBootTest
@AutoConfigureMockMvc
class MetadataControllerTest {
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void getMetadata_returnsMetadata_whenFileExists() throws Exception {
        mockMvc.perform(get("/api/meta/test-123"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.fileId").value("test-123"))
               .andExpect(jsonPath("$.version").exists());
    }
}
```

## Frontend Testing

### Test Coverage

The frontend includes tests for:

1. **Components** (`components/__tests__/`)
   - FilterPanel: Filter controls, event emissions, validation
   - DataTable: Data rendering, loading states, error states, pagination
   - ChartPanel: Chart rendering, loading states, point-click events

2. **Stores** (`stores/__tests__/`)
   - useFileStore: Current file state, recent files management
   - useMetaStore: Metadata caching, loading states, error handling

3. **API Client** (`api/__tests__/`)
   - Request configuration, retry logic, error handling

### Running Frontend Tests

```bash
cd frontend

# Install dependencies first
pnpm install

# Run all tests
pnpm test:unit

# Run tests in watch mode
pnpm test:unit --watch

# Run tests with coverage
pnpm test:unit --coverage

# Run specific test file
pnpm test:unit FilterPanel.spec.ts
```

### Test Configuration

Tests use:
- **Vitest**: Fast unit test framework
- **Vue Test Utils**: Vue component testing utilities
- **@testing-library/vue**: Additional testing utilities

### Example Test

```typescript
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import FilterPanel from '../FilterPanel.vue'

describe('FilterPanel', () => {
  it('emits filter-change event when filters are updated', async () => {
    const wrapper = mount(FilterPanel, {
      props: { fileId: 'test-123' }
    })

    await wrapper.vm.$emit('change', {
      startTime: 1000000,
      endTime: 2000000,
      limit: 100,
      offset: 0,
    })

    expect(wrapper.emitted('change')).toBeTruthy()
  })
})
```

## Integration Testing

### Backend Integration Tests

Integration tests verify end-to-end functionality:

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class FileUploadIntegrationTest {
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Test
    void uploadFile_returnsFileId() {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource("test.tsfile"));
        
        ResponseEntity<UploadResponse> response = restTemplate.postForEntity(
            "/api/files/upload",
            body,
            UploadResponse.class
        );
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getFileId()).isNotNull();
    }
}
```

### Frontend Integration Tests

Component integration tests verify interactions:

```typescript
describe('Data Preview Workflow', () => {
  it('loads and displays data with filters', async () => {
    const wrapper = mount(DataPreviewView, {
      props: { fileId: 'test-123' }
    })

    // Apply filters
    await wrapper.findComponent(FilterPanel).vm.$emit('change', {
      startTime: 1000000,
      endTime: 2000000,
    })

    // Wait for data to load
    await wrapper.vm.$nextTick()

    // Verify data is displayed
    expect(wrapper.findComponent(DataTable).props('data')).toHaveLength(100)
  })
})
```

## Test Data

### Backend Test Data

Test TsFiles are located in `backend/src/test/resources/`:
- `test-tree-model.tsfile`: Sample Tree Model TsFile
- `test-table-model.tsfile`: Sample Table Model TsFile

### Frontend Test Data

Mock data is defined in test files:

```typescript
const mockData: DataRow[] = [
  {
    timestamp: 1000000,
    device: 'device1',
    measurements: {
      temperature: 25.5,
      humidity: 60,
    },
  },
]
```

## Continuous Integration

### GitHub Actions (Example)

```yaml
name: Tests

on: [push, pull_request]

jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '21'
      - run: cd backend && mvn test

  frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: pnpm/action-setup@v2
      - uses: actions/setup-node@v3
        with:
          node-version: '22'
      - run: cd frontend && pnpm install && pnpm test:unit
```

## Test Best Practices

### Backend

1. **Use Descriptive Test Names**: `getMetadata_returnsMetadata_whenFileExists`
2. **Follow AAA Pattern**: Arrange, Act, Assert
3. **Mock External Dependencies**: Use Mockito for file system, network calls
4. **Test Edge Cases**: Empty data, null values, boundary conditions
5. **Verify Error Handling**: Test exception scenarios

### Frontend

1. **Test User Interactions**: Clicks, inputs, form submissions
2. **Test Component Props**: Verify prop validation and defaults
3. **Test Event Emissions**: Verify components emit correct events
4. **Test Loading States**: Verify loading indicators appear/disappear
5. **Test Error States**: Verify error messages display correctly

## Code Coverage

### Backend Coverage Goals

- **Line Coverage**: > 80%
- **Branch Coverage**: > 70%
- **Method Coverage**: > 85%

Generate coverage report:
```bash
mvn test jacoco:report
# Report: backend/target/site/jacoco/index.html
```

### Frontend Coverage Goals

- **Statements**: > 75%
- **Branches**: > 70%
- **Functions**: > 75%
- **Lines**: > 75%

Generate coverage report:
```bash
pnpm test:unit --coverage
# Report: frontend/coverage/index.html
```

## Performance Testing

### Backend Performance Tests

Test query performance with large datasets:

```java
@Test
void queryLargeDataset_completesWithinTimeout() {
    long startTime = System.currentTimeMillis();
    
    DataPreviewRequest request = new DataPreviewRequest();
    request.setFileId("large-file");
    request.setLimit(1000);
    
    dataService.previewData(request);
    
    long duration = System.currentTimeMillis() - startTime;
    assertThat(duration).isLessThan(5000); // 5 seconds
}
```

### Frontend Performance Tests

Test rendering performance:

```typescript
it('renders large dataset efficiently', async () => {
  const largeDataset = Array.from({ length: 10000 }, (_, i) => ({
    timestamp: i * 1000,
    device: `device${i}`,
    measurements: { value: Math.random() * 100 },
  }))

  const startTime = performance.now()
  
  const wrapper = mount(DataTable, {
    props: { data: largeDataset }
  })

  const renderTime = performance.now() - startTime
  expect(renderTime).toBeLessThan(1000) // 1 second
})
```

## Debugging Tests

### Backend

```bash
# Run tests in debug mode
mvn test -Dmaven.surefire.debug

# Connect debugger to port 5005
```

### Frontend

```bash
# Run tests with debugger
pnpm test:unit --inspect-brk

# Open chrome://inspect in Chrome
```

## Troubleshooting

### Backend Tests Fail

1. **Check Java Version**: Ensure JDK 17 or 21 is installed
2. **Clean Build**: Run `mvn clean` before testing
3. **Check Dependencies**: Run `mvn dependency:tree`
4. **Review Logs**: Check `target/surefire-reports/`

### Frontend Tests Fail

1. **Reinstall Dependencies**: `rm -rf node_modules && pnpm install`
2. **Clear Cache**: `pnpm store prune`
3. **Check Node Version**: Ensure Node.js 20.19+ or 22.12+
4. **Review Console**: Check browser console for errors

## Test Maintenance

### Adding New Tests

1. **Backend**: Create test class in `src/test/java/` matching package structure
2. **Frontend**: Create `.spec.ts` file in `__tests__/` directory next to component

### Updating Tests

When modifying code:
1. Update corresponding tests
2. Add tests for new functionality
3. Remove tests for deleted functionality
4. Verify all tests pass before committing

### Test Review Checklist

- [ ] Tests are descriptive and clear
- [ ] Tests cover happy path and edge cases
- [ ] Tests are independent and isolated
- [ ] Tests run quickly (< 1 second each)
- [ ] Tests are deterministic (no flaky tests)
- [ ] Tests follow project conventions

## Resources

- [JUnit 5 Documentation](https://junit.org/junit5/docs/current/user-guide/)
- [Mockito Documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [Vitest Documentation](https://vitest.dev/)
- [Vue Test Utils](https://test-utils.vuejs.org/)
- [Spring Boot Testing](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)

## Summary

The TsFile Viewer project has comprehensive test coverage:
- **Backend**: 131 passing tests covering all layers
- **Frontend**: Component, store, and API client tests
- **Integration**: End-to-end workflow tests

Run tests regularly during development to catch issues early and maintain code quality.
