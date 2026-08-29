# 穿了没 Android

`穿了没` 是一个 Android 原生 app，用来记录每件衣服穿了多少次。当前版本采用固定底部四 Tab 的主流移动端结构，已经实现衣物导入、调用系统相机拍照记录、当天日期校验、本地图片特征识别、图表化历史统计、单件衣物数据和上衣/裤子/鞋组合预览。

## 功能

- 衣橱导入：支持上衣、裤子、鞋，录入名称、主色、品牌、备注，并选择衣物照片。
- 拍照记录：首页只保留一个明确的拍照主按钮；记录穿着次数必须从相机进入，拍照后才允许保存。
- 日期校验：拍照后自动写入本地当天，保存时再次校验；跨日未保存时必须重新拍照。
- 自动识别：导入衣物时生成本地图片签名，拍照后按上衣、裤子、鞋输出候选和置信度。
- 历史周期：支持 7 天、30 天、90 天、今年、全部、自定义周期，并通过柱状图、环形图和排行进度条展示。
- 单件数据：展示单件衣物总次数、最近穿着历史和关联穿搭。
- 组合效果：可选择上衣、裤子、鞋生成组合预览，并统计周期内常穿组合。
- 主流导航：固定底部首页、衣橱、搭配、统计四个 Tab，整块 Tab 均可点击并垂直居中。

## 技术栈

- Java 17
- Android Gradle Plugin 8.7.3
- compileSdk 36 / minSdk 29
- SQLiteOpenHelper
- 原生 Android View UI
- 原生 Canvas 图表与矢量图标
- 系统相机 Intent + MediaStore
- 本地 Canvas/Bitmap 颜色直方图识别

## 构建

```bash
./gradlew assembleDebug
```

Debug APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

安装到已连接设备：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

运行单元测试：

```bash
./gradlew testDebugUnitTest
```

## 文档

- [需求矩阵](docs/requirements-matrix.md)
- [方案设计](docs/design.md)

## 首版边界

当前识别算法是纯本地 MVP：用衣物导入图和相机图的颜色直方图、平均颜色做相似度匹配。它能完成首版自动候选，但遇到相近颜色、复杂背景、遮挡或全身照光线变化时会误判。后续可以在 `GarmentRecognizer` 这一层替换为 CameraX + ML Kit / TensorFlow Lite / 服务端视觉模型。
