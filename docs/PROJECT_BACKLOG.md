# MyLibrary 待办

## 当前稳定基线

- Room Schema 12。
- Backup schema v4。
- 正式作品类型仅开放书籍、电影。
- Item、Record、Quote、状态、标签、自定义字段和统计已有正式数据链路。
- Record 固定保存状态文字快照和 `durationMinutes`。
- Quote 固定保存 `chapter`、`page`，并保留旧 `source` 语义。
- Round 4V 完成后，用户已手动验证 `compileDebugKotlin`、`testDebugUnitTest`、`assembleDebug`、`assembleDebugAndroidTest` 和 `lintDebug`；Round 4V-Fix 3 修改后的构建与真机验证仍待执行。

## 已完成但待真机验证

- 编辑器在 Room 事务提交后立即发布保存完成；旧封面清理失败不再改变保存结果。
- 回收站永久删除提交后，普通封面清理失败只记录警告。
- Backup v4 导入具有显式阶段、恢复报告、staging 清理和 `CancellationException` 传播。
- 固定阅读／观看统计使用分离的 Record、Quote 聚合，避免两个一对多表的 JOIN 放大。
- 封面输入限制为 32 MiB、宽高各 8192、总像素 1600 万，并检查真实格式与图片 bounds。
- 封面海报在创建 Bitmap 前限制宽、高、总像素和估算 ARGB 内存。
- Round 4V 月历图片导出已接通年月配置、首页同源日期／封面规则、主题快照、1080×1440 PNG Renderer，以及 MediaStore／SAF 直接保存。
- Round 4V 年度封面海报已接通年份与全部／书籍／电影配置、真实 Record 日期归属、Item 去重、稳定排序、有效封面过滤和直接保存。
- Round 4V-Fix 已按产品决定移除图片预览页面、preview route、预览 ViewModel 和视觉导出分享入口；设置页稳定 ViewModel 负责生成、保存、SAF 事件和临时文件清理。
- Round 4V-Fix 2 已移除年度配置的“类别”Label，减弱月历空日期格并压缩标题、星期与网格的内部空白；年度海报在布局前过滤无效封面。
- Round 4V-Fix 3 将首页和月历导出统一到同一个封面 placement 函数；年度海报使用验证阶段冻结的真实封面比例，按稳定顺序生成宽度固定 1080、高度随内容变化的无间距等高行布局。
- Round 4V 原实现的 Kotlin 编译、JVM 测试、Debug 构建、AndroidTest APK 编译和 lint 已由用户手动验证；Round 4V-Fix 3 修改后的构建与真机直接导出仍待手动验证。
- 详情／编辑器当前采用 IO 查询、Default 映射、一次发布、destination `RESUMED` gate、无固定 delay、无空白 Card skeleton、Quote 单上游。
- 真实 11→12、1→12、手工填充 v9→12、Backup v4 数据库往返和固定统计 Room 测试源码均已存在；尚未执行 instrumentation test。

## P1 数据与发布风险

- Schema 9 与 Schema 10 的 `identityHash` 均为 `396338b323f6d481a4543ef08af9b1dc`，但 Schema 10 比 Schema 9 多 `field_definitions.options`。未找到可证明历史 hash 的版本库、旧 APK 或原始数据库，因此不得伪造 hash；当前通过手工填充的真实 v9 SQLite fixture 验证 9→10→11→12，并继续保留历史资产可信度风险。
- Schema 12 没有不可变的 Type semantic key。当前 BOOK／MOVIE 仍以 ID 1／2 为稳定契约；非标准 ID 即使显示名为 Book／Movie 也不得自动提升为正式类型。
- 后续若要彻底脱离固定 ID，应在明确的 Schema 13 设计中增加 `item_types.semantic_key`，同时提供 Item、字段定义、备份和统计的迁移策略。
- Backup 导入仍不是 Room、DataStore 和文件系统的跨存储原子事务。恢复属于有报告的 best-effort recovery，不能承诺任意失败点都完全未修改。
- Android 云备份包含数据库、封面和 DataStore，需要在发布前确定隐私策略。
- 手动 Backup ZIP 为明文，发布前必须明确提示。
- Gradle wrapper 当前使用本机 `file:///D:/...`，干净 clone 无法构建。
- 正式 applicationId、签名、版本和发布许可材料尚未确定。
- 处理 App 进程在 DataStore、数据库和封面提交步骤之间被终止时的导入恢复机制。

## P2 性能与维护

- `records.start_date` 索引；需要 Schema 13 或后续 Migration，不能在 Schema 12 上静默增加。
- 详情／编辑器 LazyColumn 评估。
- 全部摘录 Paging 3。
- Record／Quote Sheet 临时草稿 saveable。
- 孤儿封面和分享缓存扫描、清理。
- WorkManager 定期清理任务。
- 报告大量 `IN` 查询分块。
- Macrobenchmark 和大数据真机 jank 测试。
- 固定统计和自定义统计后台 dispatcher 的真机验证。
- 资料库大数据分组和首页全范围时间轴查询优化。
- 废弃的类型管理、ADD_RECORD／EDIT_RECORD、独立标签编辑 route 和旧 use case 清理。
- 已取消的标签统计 Flow、投影和状态字段清理。

## 用户可见未完成功能


### Round 4W：报告输出（源码已完成，待手动验证）

- 月度／年度报告统一使用 ALL／BOOK／MOVIE 全局范围和同一入选 Item 集合。
- 基础统计复用正式阅读／观看统计模型；Record 只服务周期归属和固定统计。
- 标签、字段统计、当前作品状态、作品档案／作品字段和摘录按固定模块顺序自动分页；作品档案本身只显示封面、标题和作者／导演。
- 自定义字段在报告中只按 Item 处理，不生成 Record 字段或 Record 状态统计。
- PNG／PDF 共用分页后的页面模型、Layout engine、Renderer、MediaStore／SAF 保存和临时文件清理。
- 大量 Item ID 查询已分块；源码修改后的构建、测试和真机导出待用户手动验证。

### Round 4X：主题包管理

- 主题包容器。
- 导入和校验。
- 已安装列表。
- 点击应用。
- 当前主题持久化。
- 删除。
- 恢复默认。
- Backup 集成。
- Web 制作器最后处理。

## GitHub

- 仓库根目录仅为 `MyLibrary`。
- 将 Gradle wrapper 改为官方 HTTPS distribution。
- 完善 `.gitignore`：`.kotlin/`、日志、APK／AAB、`*.jks`、`*.keystore`。
- README 更新到 Schema 12／Backup v4 和真实功能状态。
- 增加 LICENSE。
- 增加第三方声明。
- 增加隐私说明。
- 清理 APK、日志、缓存和本机构建输出。
- 防止提交 keystore 和父目录文件。
- 使用干净 clone 完成 debug 构建。

## Release

- 正式 `applicationId`／`namespace`。
- `versionCode`／`versionName` 策略。
- release signing。
- R8／资源压缩。
- 签名 APK／AAB。
- 升级 Migration 和完整链验证。
- Backup v4 导出、清空、恢复验证。
- Android 云备份策略。
- 明文备份说明。
- 隐私说明、第三方许可和发布说明。

## 已取消／不开放

- 自定义作品类型 UI；Type 表和旧备份兼容层仅为历史数据兼容。
- 自定义 `FieldType.DURATION`。
- 标签数量／标签分布固定统计。
- 把 Record 时长实现为自定义字段的旧方案。
- 独立 Record 即时保存入口。
- 已取消内容不得重新列为待开发功能。
