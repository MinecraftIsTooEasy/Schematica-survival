# Schematica-survival（中文说明）

## 项目简介
`Schematica-survival` 是一个面向生存模式的 Schematica 扩展模组（MITE/FishModLoader 环境）。
它提供了蓝图加载、移动、旋转、镜像、粘贴、撤回、区域保存/创建，以及可交互的“蓝图打印机”方块。

当前项目信息：
- 模组名：`Schematica-survival`
- 模组 ID：`schematica_survival`
- 当前版本：`0.1.2`
- 入口类：`com.github.lunatrius.schematica.SchematicaSurvival`

## 核心功能
- 生存模式命令集：`/schematica ...`
- 蓝图打印机方块（含合成配方）
- 打印机 GUI：
  - 扫描蓝图文件
  - 确认并加载投影（Confirm & Load）
  - 打印 / 撤回
  - 旋转与镜像
  - 投影透明度调节（虚影 + 线框）
  - 材料供给面板（每种材料单独按钮）
  - 滚动条拖动（支持滚轮/上下键）
- 投影渲染行为：
  - 方块虚影保持自遮挡关系（被覆盖面不透出）
  - 线框仅绘制暴露面
- 打印机库存供料：
  - 打印优先消耗打印机自身库存
  - 不再从玩家背包直接扣除（打印机路径）
  - “已存”按服务端打印机库存快照同步
  - 支持分批投料（`printer provide`）
- 打印成本配置（可开关）：
  - 默认每 `32` 个待放置方块需要 `1` 颗绿宝石（向上取整）
  - 可通过 `config/schematica_survival.properties` 调整：
    - `printer.requireEmerald`
    - `printer.blocksPerEmerald`
- 安全打印规则：
  - 若目标位置是“非空气且与目标方块不同”，直接阻止打印并回报坐标
  - 若目标位置方块与投影方块相同，则跳过该位置（不打印、且不计入材料需求）

## 快速开始
1. 构建/编译：`./gradlew.bat compileJava`
2. 将 `.schematic` 文件放入 `run/schematics/`
3. 进入游戏后合成打印机并放置
4. 右键打开打印机 GUI：
   - 选择蓝图后点击“确认并加载”
   - 在下方面板分批提供材料
   - 点击打印

打印机配方：
- `S S S`
- `S R S`
- `S S S`
- `S = 铁锭`，`R = 红石`

## 常用命令
- `/schematica help`
- `/schematica list`
- `/schematica load <name>`
- `/schematica unload`
- `/schematica status`
- `/schematica origin here`
- `/schematica move <x> <y> <z>`
- `/schematica nudge <dx> <dy> <dz>`
- `/schematica rotate <90|180|270>`
- `/schematica mirror <x|z>`
- `/schematica paste [replace|solid|nonair]`
- `/schematica undo`
- `/schematica save <x1> <y1> <z1> <x2> <y2> <z2> <name>`
- `/schematica create <name>`
- `/schematica sel status`
- `/schematica sel clear`
- `/schematica printer print <x> <y> <z> [replace|solid|nonair]`
- `/schematica printer provide <x> <y> <z> <itemId> <subtype> [count]`

## 本地化与文本
- 已支持中英文语言键：
  - `src/main/resources/assets/minecraft/lang/zh_CN.lang`
  - `src/main/resources/assets/minecraft/lang/en_US.lang`
- GUI 与命令提示文本已尽量改为语言键管理。

## 备注
- 生存粘贴会消耗材料。
- 目前打印机默认按 `replace` 路径发起打印（可通过命令参数扩展模式）。
- 若需查看变更细节，请参考 `CHANGELOG.md`。
