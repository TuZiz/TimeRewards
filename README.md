# TimeRewards

TimeRewards 是一个基于 Kotlin 编写的 Bukkit / Paper 奖励插件，用于统计玩家在线时长，并按 `today`、`week`、`month`、`year`、`total` 五个周期发放奖励。

插件内置箱子 GUI，支持：

- 在线时长面板展示
- 周期奖励领取
- 命令查看奖励列表
- 可选 PlaceholderAPI 占位符
- YAML / MySQL 两种数据存储

## 特性

- 统计玩家当前会话与累计在线时长
- 按周期自动分桶：今天、本周、本月、本年、总计
- 奖励支持：
  - 服务器控制台命令
  - 物品奖励
- GUI 采用配置驱动：
  - 标题可配置
  - 布局可配置
  - 图标与分页按钮可配置
  - 每个周期一个配置文件，GUI 和奖励写在同一个 `Rewards/*.yml`
  - 支持用反引号奖励名把指定奖励放入 GUI
- 支持 `/timerewards reload` 热重载配置

## 命令

主命令：`/timerewards`

别名：

- `/tr`
- `/onlinerewards`

可用子命令：

- `/tr` 或 `/tr open`：打开今日奖励 GUI
- `/tr today`：打开今日奖励 GUI
- `/tr week`：打开本周奖励 GUI
- `/tr month`：打开本月奖励 GUI
- `/tr year`：打开本年奖励 GUI
- `/tr total`：打开累计奖励 GUI
- `/tr claim <scope> <id|序号>`：领取指定奖励
- `/tr reload`：重载配置

## 权限

- `timerewards.use`
  - 默认值：`true`
  - 允许使用插件主功能
- `timerewards.reload`
  - 默认值：`op`
  - 允许重载配置

## 配置

主要配置文件：

- `src/main/resources/config.yml`
- `src/main/resources/messages.yml`
- `src/main/resources/Rewards/day.yml`
- `src/main/resources/Rewards/week.yml`
- `src/main/resources/Rewards/month.yml`
- `src/main/resources/Rewards/year.yml`
- `src/main/resources/Rewards/total.yml`

### `config.yml`

主要包含：

- `settings.default-scope`
  - 默认打开的周期，当前为 `today`
- `database.enabled`
  - 是否启用 MySQL
- `database.type`
  - 当前实现支持 `mysql`
- `database.migrate-existing-yaml`
  - 启用后会把 `playerdata.yml` 的旧数据迁移进 MySQL
- 周期 GUI 与奖励内容已拆分到 `Rewards` 目录，不再放在 `config.yml` 顶层

### `Rewards/*.yml`

每个周期一个配置文件，GUI 和奖励写在同一个文件里：

- `Rewards/day.yml`：今日在线奖励
- `Rewards/week.yml`：本周在线奖励
- `Rewards/month.yml`：本月在线奖励
- `Rewards/year.yml`：本年在线奖励
- `Rewards/total.yml`：累计在线奖励

文件顶层结构：

- `enabled`：当前周期总开关
- `gui`：当前周期 GUI 配置
- `rewards`：当前周期奖励配置

`gui` 使用 `GuiPlain` 和 `GuiKey`。普通单字符仍按 `GuiKey` 渲染；反引号包裹的内容会按奖励名、奖励 ID 或 `display-name` 匹配同文件 `rewards` 中的奖励。

从旧版本升级时，如果服务器目录里还保留 `gui/day.yml` 等旧文件，插件会临时读取旧 GUI 并输出迁移提醒；建议把旧 GUI 内容移动到对应 `Rewards/*.yml` 的 `gui` 段后删除旧 `gui` 目录。

```yml
enabled: true

gui:
  Title: "&#FFD700每日在线奖励"
  GuiPlain:
    - "    S    "
    - " `30分钟在线奖励` "
  GuiKey:
    "S":
      Material: "PLAYER_HEAD"
      Name: "&f我的在线统计"
      IconFunction: "stats"

rewards:
  30分钟在线奖励:
    time: 30
    display-name: "&e30分钟在线奖励"
    lore:
      - "&7在线满 30 分钟即可领取"
      - "&e点击领取奖励"
    commands:
      - "money give {player} 100"
```

只有写进 `gui.GuiPlain` 的奖励才会在 GUI 中显示。若 GUI 引用了不存在的奖励，或奖励缺少有效 `time`、`commands`、`items`，插件会在加载配置时输出可定位的日志错误。

简单 GUI 布局示例：

```yml
GuiPlain:
  - "    S    "
  - " `30分钟在线奖励` "
```

周期总开关：

- `enabled`
  - `day.yml` 默认 `true`
  - `week.yml` / `month.yml` / `year.yml` / `total.yml` 默认 `false`

每个奖励可配置：
- `time`
- `icon`
- `icon-amount`
- `display-name`
- `display-rewards`
- `lore`
- `commands`
- `items`

`lore` 是奖励在 GUI 中显示的完整描述文本。你可以完全手写，不需要放 `{reward_lines}`、`{hint}`、`{progress_text}` 等变量；配置里写什么就显示什么。若某个奖励没有写 `lore`，才会使用同文件 `gui.GuiKey` 中 `IconFunction: reward` 的兜底模板。

启用周期后，奖励必须至少配置一条 `commands` 或一项有效 `items`，否则会被跳过并记录错误。

默认 `Rewards/day.yml` 使用 20 到 200 分钟的简洁在线奖励，主要发放 EMC：

```yml
commands:
  - "uemc give {player} 50"
```

可以按服务器经济节奏直接调整每档 EMC 数值。

### `messages.yml`

用于：

- 无权限提示
- 仅玩家可用提示
- 重载提示
- 奖励未达成提示
- 奖励已领取提示
- 奖励领取成功提示
- 命令帮助与列表文本

## 数据存储

默认使用 YAML 存储：

- `playerdata.yml`

开启数据库后使用 MySQL：

- 自动创建表
- 维护玩家信息、周期进度、已领取奖励记录
- 支持从 YAML 迁移旧数据

MySQL 连接配置位于：

```yml
database:
  enabled: true
  mysql:
    host: localhost
    port: 3306
    database: timerewards
    username: root
    password: ""
    table-prefix: timerewards_
```

## 占位符

如果服务器安装了 PlaceholderAPI，插件会注册 `timerewards` 占位符扩展。

可用变量包括：

- `%timerewards_today_minutes%`
- `%timerewards_week_minutes%`
- `%timerewards_month_minutes%`
- `%timerewards_year_minutes%`
- `%timerewards_total_minutes%`
- `%timerewards_today_formatted%`
- `%timerewards_week_formatted%`
- `%timerewards_month_formatted%`
- `%timerewards_year_formatted%`
- `%timerewards_total_formatted%`
- `%math_1-200%`
  - 运行时随机整数，占位范围包含两端

该随机占位符同样可用在 `config.yml`、`messages.yml` 和 `Rewards/*.yml` 的任意字符串中。

## 构建

环境要求：

- JDK 21
- Gradle
- Minecraft / Spigot API 1.21 相关依赖

构建命令：

```bash
./gradlew build
```

Windows 下可使用：

```powershell
gradlew.bat build
```

## 安装

1. 执行构建
2. 将生成的插件 Jar 放入服务器 `plugins` 目录
3. 启动服务器后按需修改 `config.yml`、`messages.yml` 和 `Rewards/*.yml`

## 依赖说明

项目当前还引用了本地依赖：

- `depands/easygui-bundle-1.0-SNAPSHOT.jar`

如果本地没有该 Jar，构建会失败，需要先补齐对应文件。

## 说明

- 插件主类：`ym.timeRewards.TimeRewards`
- API 版本：`1.21`
- Folia 支持：未声明
- 软依赖：`PlaceholderAPI`
