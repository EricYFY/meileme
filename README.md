# 🏔️ 山间民宿度假村 · 挂机经营模拟系统（Meileme Resort）

> **系统版本**：v2.1（数据驱动双层配置 & 全动态等级与后管联动版）  
> **更新时间**：2026-10-26  
> **适用终端**：Java 后端（Spring Boot 3.2.4）+ Vue3 前台交互（Vite + Pinia）+ 管理员后台（13大模块可视化配置）

---

## 📌 一、系统定位与玩法机制

纯**挂机经营**沙盘：开局后引擎连续实时仿真（现实 **1 分钟 = 游戏 1 天**，可 0.5~16x 倍速切换），玩家随时下单，系统根据数学模型进行全自动流转。

### 1. 核心操作生效机制
- **① 即时类操作**（招聘 / 培训启动 / 解雇 / 技能研发 / 地块解锁）：进入「待生效指令队列」，于**次日 0 点**完成二次校验并正式生效；
- **② 工程类操作**（新建施工 / 形态升星 / 分支进化 / 复合合并 / 内装升级）：点击即**立即扣费**并进入「进行中工程」状态，按各自**工期（游戏天，自次日起累计扣减）**推进，**工期结束当日 0 点正式竣工**；
- **③ 连续经营原则**：施工与培训期间实体**不脱产、不打烊**，建筑与员工均按**开工前原等级**继续正常营业并输出能力与收益。

### 2. 纯粹的长线成长数值
- 无价格战、无随机事件、无四季衰减、无广告买量；
- 玩家的核心策略聚焦于：**母本规划 → 形态升星与进化 → 内装承载挂载 → 岗位能力调配 → 科技树研发 → 地块开拓**。

---

## 🏗️ 二、配置系统与全链路架构

系统采用**双层配置驱动架构**与**热重载机制**，彻底告别硬编码：

```mermaid
flowchart TB
    subgraph 配置持久化层
        DEF[出厂默认配置 default-config.json<br/>只读随打包]
        ADM[自定义配置 admin-config.json<br/>热重载优先]
    end

    subgraph 后端中枢 (Spring Boot)
        REG[ConfigRegistry 配置注册表]
        ENG[GameEngine 主循环 1s Tick]
        SETTLE[SettlementService 每日结算]
        STAFF[StaffService 人力供需]
        BUILD[BuildingService 建筑施工]
    end

    subgraph 前端交互 (Vue3 + WebSocket)
        GAME[游戏前台界面 5173<br/>总览/地图/建筑/员工/科技/成就/日志]
        ADMIN[后管配置弹窗 AdminConfigModal<br/>13 大模块可视化编辑 & 热生效]
    end

    DEF -->|兜底载入| REG
    ADM -->|优先加载 / 保存覆盖| REG
    REG --> ENG
    ENG --> SETTLE & STAFF & BUILD
    ADMIN <-->|GET / POST / RESET| REG
    ENG <-->|WebSocket: /game| GAME
```

1. **配置文件关系**：
   - `backendjava/src/main/resources/default-config.json`：随源码打包的出厂默认备份；
   - `backendjava/admin-config.json`：管理员在后管保存后的自定义覆盖文件；引擎启动与热更新时优先读取此文件。
2. **可视化管理后台**（点击游戏顶部状态栏「⚙️ 管理员模式」呼出）：
   - 支持 **13 大模块**（全局参数、客群、区域、母本、形态、进化线、内装、合并配方、地块、能力、岗位、技能树、成就）的在线可视化编辑与即时校验；
   - 支持配置导出为本地 JSON、导入本地 JSON 文件；
   - 支持一键删除 `admin-config.json` 并重置为出厂默认值。

---

## 🏛️ 三、建筑、形态与内装体系

### 3.1 母本建筑（`mothers`）与形态解耦
- **母本（Mother）**：定义地块初始可规划建造的建筑类型。统一由 `isOpen` 控制开放，其基础建造成本（`buildCost`）与建造工期（`buildDays`）统一在母本中维护；
- **解耦设计**：母本不再冗余配置容量。建筑竣工落格时，由其赋予的初始形态（Form）自主承载人数与属性。

### 3.2 建筑形态（`buildingForms`）
- **大类自主**：形态自带 `category`（`ACCOMMODATION` 住宿 / `SUPPORT` 配套）；
- **动态等级**：支持任意多级升星（Lv1 ➔ Lv2 ➔ Lv3...），各星级配置独立的升级花费（`formLevelUpCost`）、工期（`formLevelUpDays`）、房费/客单价、以及该星级允许的内装上限（`maxInteriorLevel`）；
- **岗位能力需求（`abilityDemands`）**：每个形态显式指定运转所需的各项能力基准点数（如客房清洁、餐饮烹饪等）；
- **客群相性（`affinities`）**：定义形态对各客群的基础契合度（默认 0.5）。

### 3.3 承载能力与内装（`interiors`）强关联
- **内装分类（`category`）**：分为 `CARRY`（承载型）、`NORMAL`（普通型）、`COMFORT`（舒适型）、`ENTERTAINMENT`（娱乐型）；
- **唯一承载内装（`capacityInterior`）生效法则**：
  - 每个建筑形态在配置中通过 `capacityInterior` 绑定唯一的一个内装设施（如标准客房对应大床、餐厅对应餐位桌椅、温泉对应泡池）；
  - **只有当建筑安装了该指定内装，且该内装为 `CARRY` 类别时，其每级提供的承载能力（`capacityPerLevel`）才会生效为该建筑增加实际接待人数**；
  - 非承载内装或未绑定的内装，仅贡献舒适度加成、营收加成与客群吸引相性，不扩充接待容量。

### 3.4 进化与合并网络
- **分支进化（`evolutionLines`）**：单建筑达到要求星级后，沿预设进化路线晋升为高阶形态（如标准客房 ➔ 家庭亲子房 ➔ 亲子套房）。进化规则完全数据驱动化配置；
- **合并配方（`mergeRecipes`）**：采用结构化原料清单 `sources`（支持 2合1 及多合一复合合并）。两栋源建筑在施工期间各自正常营业，竣工当日 0 点融合成终极高阶合院。

---

## 👥 四、客流转化与经济模型

### 1. 每日客流转化公式
$$意向到访转化率 = \min\Big(\text{maxConversionRate},\, \max\big(\text{minConversionRate},\, \frac{\text{客群综合吸引力}}{\text{conversionDenominator}}\big)\Big)$$

- **客群综合吸引力**：累加该区域全部营业建筑对该客群的吸引力；
  $$\text{单建筑吸引力} = \text{建筑实际舒适度} \times \big(\text{形态基础相性} + \sum \text{内装客群相性加成}\big)$$
- **实际接待与流失**：
  $$\text{意向顾客} = \text{区域基准客流} \times \text{区域客群占比} \times \text{转化率}$$
  $$\text{实际接待} = \min\big(\text{意向顾客},\, \text{有效总承载容量}\big)$$
  $$\text{流失客流} = \text{意向顾客} - \text{实际接待} \quad (\text{超出容量直接流失，不二次分流})$$

### 2. 收益结算规则
- **住宿类营收**：按「间」结算。分配人数 $> 0$ 即视为整间售出，即便未住满也按整间房价计收：
  $$\text{住宿收入} = \sum \Big(\text{售出间数} \times \text{形态当前等级房价} \times (1 + \sum \text{内装营收加成}) \times \text{科技倍率}\Big)$$
- **配套类营收**：按「人次」结算。当日服务人次不超过配套服务容量上限：
  $$\text{配套收入} = \sum \Big(\min(\text{区域入住总人数}, \text{配套总容量}) \times \text{配套客单价} \times (1 + \sum \text{内装营收加成}) \times \text{科技倍率}\Big)$$
- **日常零固定成本**：游戏内无固定日常维护费与日常底薪，当日净利润等同于当日营业总额。

---

## 🧑‍💼 五、雇佣与岗位能力供需体系

### 1. 全局人力供需池
- 度假村运转需要各项专业能力点数（客房清洁、餐饮服务、烹饪、温泉运营、客房维修等）；
- **能力总需求**：全度假村营业中建筑的 `abilityDemands` 累加和；
- **能力总供给**：在册全部员工（包含升级培训中的员工，按原等级计算）在 `abilityOutputs` 中提供的能力点数累加和。

### 2. 缺员舒适度折损
- 某项能力满足率 $< 100\%$ 时，相关建筑的实际舒适度按折损系数打折：
  $$\text{折损系数} = \max\Big(\text{staffShortageMinComfortRatio},\, \frac{\text{总供给}}{\text{总需求}}\Big)$$
- 舒适度下降会直接传导导致客群吸引力减弱，进而降低到访转化率与实际营收。

### 3. 员工在岗成长与升级培训
- **在岗积累**：员工在册出勤天数达到岗位 `upgradeDaysByLevel` 门槛后，解锁「培训晋升」按钮；
- **在岗培训（不脱产）**：支付对应等级培训费用（`trainCostByLevel`）后启动培训工期（`trainDaysByLevel`）。**培训期间员工不脱产，继续按升级前等级全额提供能力**；工期结束后晋升为新等级；
- **解雇补偿**：
  $$\text{解雇补偿金} = \text{compensationBase} \times \big(1 + \text{在岗天数} \times \text{fireCompensationPerDayRatio}\big) \times \text{技能折扣}$$

---

## 🌳 六、科技技能树与成就体系

### 1. 经营技能树（`skills`）
- 技能树分为 10 大明确方向（内装升级减免、招聘减免、解雇减免、建筑进化减免、建筑升级减免、内装舒适度倍增、岗位能力倍增、建筑建造减免、住宿收益倍增、配套收益倍增）；
- 单局成长拓扑为无环 DAG，消耗由地块解锁等产出的技能点（`costPoints`）点亮；
- 界面内嵌基于 SVG 的实时交互拓扑全景图谱，直观展示依赖关系与加成效果。

### 2. 成就系统（`achievements`）
- 覆盖 6 大标准维度（建筑总数、累计接待顾客数、总资产规模、员工总数、特定等级岗位人数、特定等级形态建筑数）；
- 达成目标自动派发技能点奖励，并作为高级地块解锁的前置必备条件。

---

## 📖 七、图鉴收集系统（Codex System）

度假村引入了全岛典藏收集系统，记录玩家经营发展中的每一处里程碑与创新印记：

### 1. 四大图鉴类别与解锁时机
- **🏰 建筑形态图鉴（`unlockedForms`）**：
  - 玩家**首次完成**某个基础建筑形态的建造竣工时解锁；
  - 玩家**首次完成**高阶分支进化竣工（如家庭亲子房、行政套房）或多建筑合并融合竣工（如温泉木屋）时解锁该目标形态图鉴；
- **🛋️ 内装设施图鉴（`unlockedInteriors`）**：
  - 玩家在任意建筑中**首次安装/建造**某项内装设施（如大床、餐位桌椅、露天温泉泡池）竣工时解锁；
- **🗺️ 度假村地块图鉴（`unlockedPlots`）**：
  - 开局初始化自动解锁的默认领地地块直接收录至图鉴；
  - 玩家后续通过消耗资金达成成就**首次解锁**新地块时收录至图鉴；
- **👔 员工岗位图鉴（`unlockedPositions`）**：
  - 玩家在人才市场**首次成功招聘**某个岗位员工（如客房保洁员、主厨、安全员、运营经理等）时解锁。

### 2. 前后端数据下发与典藏交互
- **数据结构**：后端在 `GameState` 维护 `CodexState`，并在 `RESORT_STATE` 全量下发；
- **即时通知**：首次解锁时，后端精准广播 `CODEX_UNLOCKED` 事件，前台触发金色庆贺动画 Toast；
- **典藏大厅（`CodexPanel.vue`）**：
  - **总览成就仪表盘**：显示总收集数、总收集率（%）与进度条，根据达成率自动授予称号（*初出茅庐 ➔ 度假村筑梦新秀 ➔ 卓越庄园开拓者 ➔ 度假王国传奇大师 ➔ 全知全能度假王国典藏家*）；
  - **四大分项卡片**：快捷切换分类查看子收集率与进度；
  - **精美卡片网格**：已解锁图鉴带有发光边框与真实参数，未收录图鉴呈现神秘剪影、编号问号与解锁指引；
  - **档案详情浮窗**：点击已收录卡片可查阅各形态的客群偏好、能力需求、容纳/单价与典藏印章。

---

## 📁 八、工程目录结构

```
meileme/
├── backendjava/                          # ☕ Java 游戏核心后端 (端口 8080)
│   ├── admin-config.json                 # 管理员自定义配置文件 (热重载首选)
│   ├── pom.xml                           # Maven 构建配置 (Spring Boot 3.2.4)
│   └── src/
│       ├── main/
│       │   ├── java/com/meileme/resort/
│       │   │   ├── ResortApplication.java    # 启动入口类
│       │   │   ├── config/                   # 数据驱动配置实体类 (BuildingForm, Plot, etc.)
│       │   │   ├── model/                    # 运行时内存实体 (GameState, CodexState, BuildingInstance, etc.)
│       │   │   ├── repository/               # 内存态仓储管理
│       │   │   ├── service/                  # 核心服务 (GameEngine, Settlement, Comfort, etc.)
│       │   │   └── websocket/                # WebSocket 握手与消息路由网关
│       │   └── resources/
│       │       ├── application.properties    # 服务端口与系统参数配置
│       │       └── default-config.json       # 出厂只读备份全量配置
│       └── test/java/com/meileme/resort/     # 自动化业务回归测试集 (含 CodexTest, CategoryDecoupleTest)
│
├── frontendweb/                          # 🎨 Vue3 前台交互界面与管理员后台 (端口 5173)
│   ├── package.json
│   ├── vite.config.js
│   └── src/
│       ├── App.vue                           # 前端主框架
│       ├── services/                         # WebSocket 客户端与全局状态 store
│       └── components/
│           ├── admin/                        # ⚙️ AdminConfigModal.vue (13大模块可视化后管)
│           ├── codex/                        # 📖 CodexPanel.vue (图鉴收集系统大厅与卡片详情)
│           ├── overview/                     # 状态栏、今日预计、客流构成、能力供需面板
│           ├── building/                     # 地块网格地图、新建弹窗、建筑详情演化面板
│           ├── staff/                        # 招聘中心、在册员工列表、培训晋升操作
│           ├── skill/                        # SVG 可视化科技树拓扑面板
│           ├── region/                       # 大区地块切换与解锁进度
│           └── log/                          # 经营日报与历史区间报表
│
├── CONFIG_DICTIONARY.md                  # 📖 全系统配置全字段详查字典手册 (v2.0)
└── README.md                            # 📘 系统整体架构、业务规则与快速启动说明书
```

---

## 🚀 九、快速启动与调试指引

### 1. 启动后端
```bash
cd backendjava
# 运行单元测试
mvn clean test

# 启动 Spring Boot 服务 (8080 端口)
mvn spring-boot:run
```

### 2. 启动前端
```bash
cd frontendweb
npm install
npm run dev
# 浏览器访问: http://localhost:5173
```

### 3. 推荐调试链路
1. 打开浏览器进入游戏主界面，点击顶部「▶ 开始一局」；
2. 点击「规划建造」，在当前地块可建造格位选择建造「标准客房」；
3. 建造工程开始，倒计时推进，次日 0 点结算生成首批客流与房费营收；
4. 点击建筑进入详情页，可体验：**内装升级**、**建筑同形态升星**、**分支进化路线选择**；
5. 点击顶部「⚙️ 管理员模式」，可在弹窗中自由调节 13 大模块的各项数值与规则，点击「保存当前配置」即时热重载生效！

---

> ⚠️ **开发维护规范**：
> 1. 本项目的业务规则、配置字段与架构设计均以此文档及 [CONFIG_DICTIONARY.md](file:///Users/ericyang/Desktop/yfyProject/meileme/CONFIG_DICTIONARY.md) 为准；
> 2. **在以后的每一次功能迭代、逻辑重构或字段增删改动中，必须同步检查并更新本 `README.md` 文件**。
