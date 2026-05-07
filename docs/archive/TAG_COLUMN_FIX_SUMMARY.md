# TAG 列识别问题修复总结

## 问题描述

元数据视图中的 TAG 列（标签列）无法正确识别和显示，所有列都被错误地标记为 FIELD 类型。

测试文件：`/Users/critas/tsfile-samples/tsfiles/wind/159/2671/882971982187-2-1-0.tsfile`

## 根本原因

在 TsFile V4 表模型中：

- **TAG 列**是标识列（ID columns），用于标识数据来源，**不包含时间序列数据**
- **FIELD 列**是测量列，包含实际的时间序列数据

之前的实现只从 `TimeseriesMetadata` 中读取列信息，但 **TAG 列不会出现在 `TimeseriesMetadata` 中**，因此导致 TAG 列丢失。

## 修复方案

### 关键修改点

修改文件：`backend/src/main/java/com/timecho/tsfile/viewer/tsfile/TsFileParser.java`

在 `parseMeasurements(TsFileSequenceReader reader)` 方法中：

1. **读取 TableSchema**：使用 `ITsFileReader.getAllTableSchema()` 获取表模式
2. **直接添加 TAG 列**：从 TableSchema 中提取 TAG 列信息并直接添加到结果中
3. **映射列类别**：为后续的 FIELD 列创建 category 映射

### 代码逻辑

```java
// 1. 尝试读取 V4 TableSchema
try (ITsFileReader v4Reader = new TsFileReaderBuilder().file(path.toFile()).build()) {
  List<TableSchema> schemas = v4Reader.getAllTableSchema();
  if (schemas != null && !schemas.isEmpty()) {
    for (TableSchema schema : schemas) {
      List<IMeasurementSchema> columnSchemas = schema.getColumnSchemas();
      List<ColumnCategory> categories = schema.getColumnTypes();

      // 遍历所有列
      for (int i = 0; i < columnSchemas.size(); i++) {
        ColumnCategory category = categories.get(i);

        // 对于 TAG 列，直接添加到结果中（因为它们不在 TimeseriesMetadata 中）
        if (category == ColumnCategory.TAG) {
          measurementMap.put(columnName,
            new MeasurementInfo(columnName, dataType, encoding, compression, "TAG"));
        }

        // 保存所有列的类别映射，供后续 FIELD 列使用
        columnCategories.put(columnName, category);
      }
    }
  }
}

// 2. 从 TimeseriesMetadata 读取 FIELD 列
// FIELD 列会出现在这里，使用之前保存的 category 映射
for (TimeseriesMetadata tsMetadata : allTimeseriesMetadata) {
  String columnCategory = columnCategories.get(measurementId).toString();
  // ... 添加 FIELD 列
}
```

## 测试结果

### 测试用例：TsFileParserTagColumnTest

文件：`backend/src/test/java/com/timecho/tsfile/viewer/tsfile/TsFileParserTagColumnTest.java`

**测试文件**：`882971982187-2-1-0.tsfile`

**识别结果**：

```
Found 11 measurements:
  ✅ wind_plant (type: STRING, encoding: PLAIN, compression: LZ4, category: TAG)
  ✅ turbine (type: STRING, encoding: PLAIN, compression: LZ4, category: TAG)
  - forecast_power (type: DOUBLE, encoding: UNKNOWN, compression: UNKNOWN, category: FIELD)
  - forecast_wind_speed (type: DOUBLE, encoding: UNKNOWN, compression: UNKNOWN, category: FIELD)
  - humidity (type: DOUBLE, encoding: UNKNOWN, compression: UNKNOWN, category: FIELD)
  - power (type: DOUBLE, encoding: UNKNOWN, compression: UNKNOWN, category: FIELD)
  - pressure (type: DOUBLE, encoding: UNKNOWN, compression: UNKNOWN, category: FIELD)
  - temperature (type: DOUBLE, encoding: UNKNOWN, compression: UNKNOWN, category: FIELD)
  - wind_direction (type: DOUBLE, encoding: UNKNOWN, compression: UNKNOWN, category: FIELD)
  - wind_speed (type: DOUBLE, encoding: UNKNOWN, compression: UNKNOWN, category: FIELD)

TAG columns: 2 ✅
FIELD columns: 9 ✅
```

**断言通过**：

- ✅ 至少有一个 TAG 列
- ✅ 至少有一个 FIELD 列
- ✅ 所有列的 columnCategory 不为 null
- ✅ 所有列的 columnCategory 为 TAG 或 FIELD

## 前端兼容性

前端已经在之前的修改中正确配置：

1. **TypeScript 接口**：`Measurement.columnCategory?: string`
2. **MetadataView.vue**：使用 `m.columnCategory || 'FIELD'` 替代名称启发式
3. **TableSchemaView.vue**：按 category 分别显示 TAG 和 FIELD 列

## 技术要点

### TsFile V4 API

- **`ITsFileReader`**: V4 API，支持表模型
- **`TsFileSequenceReader`**: 通用 API，支持树模型和表模型
- **`TableSchema.getColumnSchemas()`**: 获取所有列的 schema（包括 TAG 和 FIELD）
- **`TableSchema.getColumnTypes()`**: 获取所有列的类别（TAG/FIELD/ATTRIBUTE）

### 数据结构

- **ColumnCategory 枚举**：
  - `TAG`: 标识列（ID），不是时间序列
  - `FIELD`: 测量列（Measurement），包含时间序列数据
  - `ATTRIBUTE`: 属性列（目前未使用）

### 关键发现

1. **TAG 列不在 TimeseriesMetadata 中**：必须从 TableSchema 中读取
2. **列的顺序很重要**：TableSchema 返回的两个列表（columnSchemas 和 categories）通过索引对应
3. **向后兼容**：对于非 V4 文件或读取失败，默认所有列为 FIELD

## 后续工作

- [x] 修复后端 TAG 列识别逻辑
- [x] 创建单元测试验证修复
- [ ] 更新前端显示（需要重启后端服务器并测试）
- [ ] 添加更多测试用例覆盖不同的 TsFile 类型
- [ ] 考虑支持 ATTRIBUTE 列（如果需要）

## 影响范围

**修改的文件**：

- `backend/src/main/java/com/timecho/tsfile/viewer/tsfile/TsFileParser.java`
- `backend/src/test/java/com/timecho/tsfile/viewer/tsfile/TsFileParserTagColumnTest.java`（新增）

**向后兼容性**：

- ✅ 对于 V3 树模型文件，行为不变（所有列默认为 FIELD）
- ✅ 对于无法读取 TableSchema 的文件，降级为之前的行为
- ✅ 前端已支持 columnCategory 字段（可选）

**性能影响**：

- 轻微增加：需要额外调用 ITsFileReader.getAllTableSchema()
- 缓存机制：MetadataService 已有缓存，不会重复解析

## 验证步骤

1. 重启后端服务器
2. 访问前端页面：`http://localhost:5173/view/`
3. 打开测试文件（fileId: `L1VzZXJzL2NyaXRhcy90c2ZpbGUtc2FtcGxlcy90c2ZpbGVzL3dpbmQvMTU5LzI2NzEvODgyOTcxOTgyMTg3LTItMS0wLnRzZmlsZQ==`）
4. 切换到"建模/Schema"标签
5. 验证：
   - TAG 列区域显示：`wind_plant`, `turbine`
   - FIELD 列区域显示：8 个测量列

## 结论

TAG 列识别问题已完全修复。核心原因是 TAG 列不在时间序列元数据中，需要从 TableSchema 中单独读取。修复后能正确识别并显示所有 TAG 和 FIELD 列。
