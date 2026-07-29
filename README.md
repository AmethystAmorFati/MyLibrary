MyLibrary

MyLibrary 是一款本地优先（Local-first）的 Android 个人文化记录应用，用于记录和管理书籍、电影、阅读/观看过程、摘录、标签以及个人统计。

它希望成为一个长期保存个人文化轨迹的数字档案库，而不是简单的书影音清单。

## ✨ 特性

### 📚 作品管理

- 支持书籍、电影记录
- 新增、编辑、查看、搜索作品
- 支持封面管理（原图 + 缩略图）
- 支持软删除与回收站恢复
- 支持多条阅读/观看记录

### 📝 阅读与观看记录

- 每次阅读/观看作为独立 Record 保存
- 记录日期、评分、评价、状态和时长
- 支持历史补录
- 支持连续阅读日期展示
- 时间轴展示真实记录过程

### 🗓 时间轴与日历

- 首页时间轴展示个人记录轨迹
- 月历展示作品封面投影
- 年历查看全年记录分布
- 同一作品多次记录支持稳定排序展示

### 🏷 标签与字段

- 两级标签系统
- 自定义字段系统
  - 文本
  - 数字
  - 日期
  - 单选
  - 多选
  - 评分

用于满足不同用户的个人记录需求。

### ✂️ 摘录管理

- 支持保存作品摘录
- 支持章节、页码信息
- 支持摘录搜索
- 支持最近摘录展示

### 📊 数据统计

- 分别统计阅读与观看记录
- 展示作品数量、记录次数、累计时长
- 支持阅读/观看行为分析
- 支持自定义字段统计

### 🎨 主题系统

- 基于语义颜色的主题架构
- 支持动态主题基础能力
- 统一控制：
  - 页面背景
  - 卡片
  - 导航
  - 弹窗
  - 胶囊组件

未来支持完整主题包系统。

### 💾 数据备份

- ZIP 格式完整备份
- 包含：
  - 数据库
  - 封面图片
  - 应用配置

支持数据恢复和版本迁移。

---

# 🏗 技术架构

```text
Jetpack Compose UI
        ↓
ViewModel
        ↓
Use Case
        ↓
Repository
        ↓
Room Database

主要技术：

Kotlin
Jetpack Compose
Material 3
Room
DataStore
Kotlin Serialization
Navigation Compose

项目采用：

单向数据流
Repository 数据隔离
Room Migration 管理数据库升级
Schema 导出验证
🗄 数据版本

当前稳定版本：

Room Schema: 12
Backup Schema: v4

数据库包含：

Item（作品）
Record（阅读/观看记录）
Quote（摘录）
Tag（标签）
Field（自定义字段）
Status（状态）

所有数据库升级均使用 Migration，不使用 destructive migration。

🔒 本地优先

MyLibrary：

不需要账号
不依赖服务器
数据保存在本地设备
不上传个人阅读记录

你的阅读历史、摘录和个人档案属于你自己。

📱 当前状态

已完成：

✅ 作品管理
✅ 时间轴
✅ 月历 / 年历基础功能
✅ 资料库筛选与搜索
✅ 标签系统
✅ 自定义字段
✅ 摘录系统
✅ 阅读/观看记录
✅ 数据统计
✅ 数据备份恢复
✅ 主题系统基础架构

开发中：

月历导出
年度海报
数据报告导出
完整主题包系统
🚀 构建

环境：

Android Studio
JDK 21
Gradle Wrapper

运行：

./gradlew assembleDebug

测试：

./gradlew testDebugUnitTest
📂 项目结构
app/
 ├── data/          # Room 数据库、Repository
 ├── domain/        # 领域模型
 ├── ui/            # Compose 页面
 ├── backup/        # 数据备份恢复
 ├── export/        # 导出功能
 └── theme/         # 主题系统
🛣 Roadmap

计划：

 完整主题包导入与管理
 月历图片导出
 年度封面海报
 PNG/PDF 阅读报告
 性能优化
 发布版本完善
License

License will be added before the first stable release.
