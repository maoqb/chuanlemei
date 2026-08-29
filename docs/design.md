# 方案设计

## 总体方案

首版改为 Android 原生 app，使用单 Activity + 原生 View 直接构建界面，不引入 Compose、CameraX 或后端服务。这样能在当前机器快速构建出可安装 APK，并把相机拍照、日期校验、衣物计次和本地识别链路完整跑通。

## 模块划分

| 模块 | 文件 | 职责 |
| --- | --- | --- |
| UI | `MainActivity.java` | 四个页签、相机/图库 Intent、表单、统计展示 |
| 数据模型 | `domain/Garment.java`、`domain/WearRecord.java` | 衣物和穿着记录 |
| 日期与周期 | `domain/DateTools.java`、`domain/PeriodRange.java` | 当天校验、周期范围、日期展示 |
| 统计 | `domain/StatsCalculator.java` | 单件计次、周期统计、组合统计 |
| 存储 | `data/WardrobeDatabase.java` | SQLite 表结构和 CRUD |
| 图片文件 | `data/ImageStore.java` | 图片解码、压缩、内部文件保存 |
| 识别 | `vision/ImageSignature.java`、`vision/GarmentRecognizer.java` | 图片签名、候选排序、置信度 |

## 数据结构

### garments

| 字段 | 说明 |
| --- | --- |
| `id` | 衣物 ID |
| `name` | 衣物名称 |
| `category` | `top` / `bottom` / `shoes` |
| `color` | 主色 |
| `brand` | 品牌 |
| `note` | 备注 |
| `photo_path` | App 内部保存的衣物图片路径 |
| `signature` | 图片识别签名 |
| `created_at` / `updated_at` / `archived_at` | 生命周期字段 |

### wear_records

| 字段 | 说明 |
| --- | --- |
| `id` | 记录 ID |
| `worn_at` | 穿着日期，格式 `YYYY-MM-DD` |
| `captured_at` | 拍照保存时间 |
| `photo_path` | App 内部保存的相机照片路径 |
| `top_id` / `bottom_id` / `shoes_id` | 关联衣物 |
| `recognition_summary` | 识别结果摘要 |
| `note` | 备注 |

## 拍照记录流程

1. 用户进入“记录”页。
2. 点击“调用相机拍照并识别”。
3. App 通过 `MediaStore.ACTION_IMAGE_CAPTURE` 调起系统相机。
4. 相机把照片写入 `MediaStore` URI。
5. App 读取照片，压缩保存到内部 `files/photos`。
6. App 生成照片 `ImageSignature`。
7. `GarmentRecognizer` 按上衣、裤子、鞋分别匹配已导入衣物。
8. 用户确认识别结果。
9. 保存前校验 `recordDate` 必须等于本地当天。
10. 写入 SQLite。

## 识别算法

当前算法是本地 MVP：

1. 把图片缩放到 72x72。
2. 对像素计算 64 桶 RGB 颜色直方图。
3. 同时计算平均 RGB。
4. 用直方图交集和平均颜色距离组合成 0 到 1 的置信度。
5. 每个分类保留 Top 3 候选。
6. 置信度大于 0.42 时自动选中，否则只展示候选。

这个接口后续可以保持不变，把实现替换为 TensorFlow Lite 或云端视觉模型。

## 构建与自测

已验证：

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

单元测试覆盖：

- 日期格式和日期范围
- 图片签名相似度
- 自动识别候选
- 周期统计、单件计次、组合统计
