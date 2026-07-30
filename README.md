# MyLibrary

MyLibrary 是一款本地优先（Local-first）的 Android 个人文化记录应用，用于记录和管理书籍、电影、阅读／观看过程、摘录、标签、自定义字段和个人统计。

它希望成为一个长期保存个人文化轨迹的数字档案库，而不只是简单的书影音清单。

## ✨ 主要功能

### 📚 作品管理

- 支持书籍和电影
- 新增、编辑、查看、搜索和软删除作品
- 支持封面原图和缩略图
- 支持书架、列表和纯图三种资料库视图
- 支持资料库状态与多标签组合筛选
- 支持回收站恢复

### 📝 阅读与观看记录

- 每次阅读或观看作为独立 Record 保存
- 支持日期、评分、评价、时长和历史状态文字快照
- 支持历史补录与多条记录统一编辑
- 时间轴按记录创建时间展示，每条记录只出现一次
- 作品当前状态与历史 Record 相互独立

### 🗓 月历、年历与时间轴

- 首页使用月历浮层与时间轴展示记录轨迹
- 月历按记录起止日期生成封面投影
- 支持周／月视图、展开收起和独立年历页面
- 月历点击可定位到时间轴中的对应日期
- 多封面按稳定顺序进行层叠展示

### 🏷 标签与自定义字段

- 两级标签系统
- 支持多标签交集筛选
- 支持以下自定义字段：
  - 文本
  - 数字
  - 日期
  - 单选
  - 多选
  - 评分
  - 布尔值

### ✂️ 摘录管理

- 支持多条作品摘录
- 支持章节和页码
- 支持摘录搜索
- 支持最近摘录展示

### 📊 统计与导出

- 阅读与观看数量统计
- 记录次数与累计时长统计
- 标签和自定义字段统计
- 月历图片导出
- 年度无缝封面海报
- 当前资料库高清封面网格
- 月度／年度 PNG 或 PDF 报告

### 🎨 动态主题系统

MyLibrary 支持完整的本地主题包：

- 页面背景、卡片和弹窗颜色或图片
- 主文字、次要文字、边框和强调色
- 两套 TTF 字体及四种字体角色
- 自定义底部导航图标
- 多主题导入、替换、应用和删除
- SHA-256 完整性校验
- 标准 ZIP 格式 `.mylibrarytheme`

主题制作器：

https://peanutpersimmon.github.io/MyLibrary/

主题包协议详见：

- `THEME_MANIFEST_V1.md`
- `THEME_PACKAGE_V1.md`
- `THEME_MANAGEMENT.md`

### 💾 数据备份

- ZIP 格式完整备份
- 包含数据库、封面和应用配置
- 支持数据恢复和备份版本迁移

## 🔒 本地优先

MyLibrary：

- 不需要账号
- 不依赖服务器
- 数据保存在本地设备
- 不上传个人阅读记录、摘录或封面

你的阅读历史、观看记录和个人档案属于你自己。

## 🏗 技术架构

```text
Jetpack Compose UI
        ↓
ViewModel
        ↓
Repository
        ↓
Room Database
```

主要技术：

- Kotlin
- Jetpack Compose
- Material 3
- Room
- DataStore
- Kotlin Serialization
- Navigation Compose

当前数据版本：

- Room Schema：12
- Backup Schema：v4

数据库升级均通过显式 Migration 完成，不使用 destructive migration。

## 📂 项目结构

```text
app/
├── data/       # Room、DAO、Repository、Preferences
├── domain/     # 领域模型
├── ui/         # Compose 页面与组件
├── backup/     # 数据备份与恢复
└── export/     # 月历、海报、报告等导出

theme-maker/
├── src/        # 浏览器主题制作器源码
├── test/       # Vitest 测试
└── dist/       # 本地构建结果，不提交到仓库
```

## 🚀 本地构建

Android：

```powershell
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Theme Maker：

```powershell
cd theme-maker
npm.cmd install
npm.cmd test
npm.cmd run build
npm.cmd run dev
```

## 👤 Author

**PeanutPersimmon**

- GitHub: https://github.com/PeanutPersimmon
- Repository: https://github.com/PeanutPersimmon/MyLibrary

## Development

OpenAI Codex was used as a development assistant for code generation, refactoring suggestions, testing support and documentation assistance.

Product direction, interaction design, code review and final implementation responsibility remain with PeanutPersimmon.

## License

MyLibrary 源代码采用 **PolyForm Noncommercial License 1.0.0** 授权。

允许个人、教育、研究及其他非商业用途下使用、修改和分发本项目；未经作者另行书面许可，不得用于商业用途。

商业授权请联系 PeanutPersimmon。

完整条款见 [`LICENSE`](LICENSE)。

Copyright 2026 PeanutPersimmon.