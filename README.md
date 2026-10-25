# 校园论坛（JWT Forum）

> 一个基于 **Spring Boot 4 + Vue 3** 的前后端分离论坛系统，集成 **JWT 认证**、**Elasticsearch 全文检索**、**向量 RAG 智能问答**、**对象存储** 与 **Docker 化部署**。

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-brightgreen)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5-42b883)](https://vuejs.org/)
[![Java](https://img.shields.io/badge/Java-17-orange)](https://openjdk.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ed)](https://docs.docker.com/compose/)

---

## 功能特性

### 用户与认证

- **JWT 无状态认证** —— 令牌自带用户身份，服务端不存 session；配合 Redis 黑名单实现"退出登录 / 封禁即时生效"
- **邮箱注册** —— 验证码经 **RabbitMQ** 异步投递，发送失败进死信队列，状态记在 `db_email_record`
- **角色权限** —— `user` / `admin` 两级，管理后台独立入口
- **封禁 / 禁言** —— 封禁拦截登录并拉黑已签发令牌；禁言允许浏览但拒绝发帖/评论
- **隐私设置** —— 手机/邮箱/微信/QQ/性别 五项开关，**过滤发生在服务端**（绕过前端也拿不到）

### 论坛核心

- **发帖 / 编辑 / 删除** —— 富文本用 **Quill Delta JSON** 存储（不存 HTML，无 XSS 注入面）
- **四级互动** —— 分类浏览、评论（支持二级回复）、点赞、收藏
- **全文检索** —— Elasticsearch 建索引，标题权重加倍，**隐藏帖不进搜索结果**
- **违禁词过滤** —— 发帖/评论前校验，词表可热更新
- **站内通知** —— 点赞/评论/系统通知，点击跳转对应页面

### AI 能力

- **流式对话** —— SSE 打字机效果，前端边收边渲染
- **向量 RAG 检索** —— 百炼 `text-embedding-v3` 生成 1024 维向量 → ES kNN 检索 → 组装 prompt → 回答带**引用来源**
- **周 token 额度** —— 按真实 `usage` 记账，用 `week_start` 读时判断实现跨周重置（**不用定时任务**）

### 工程能力

- **三层限流** —— 全局 IP 限流 + 业务级频率限制 + 登录频率熔断
- **traceId 全链路追踪** —— 每个请求一个 id，日志可串起来
- **多环境配置** —— `dev` / `local` / `prod` 三套 profile，敏感配置不进版本库
- **Docker 化部署** —— 一条 `docker compose up -d` 起全套中间件 + 前后端
- **接口文档** —— springdoc 自动生成 Swagger UI

---

## 技术栈

### 后端

| 类别 | 技术 | 版本 | 用途 |
|---|---|---|---|
| 框架 | **Spring Boot** | 4.0.6 | 基础框架（Jackson 3 / Security 7 / Framework 7） |
| 语言 | **Java** | 17（本机 JDK 25 运行） | |
| ORM | **MyBatis-Plus** | 3.5.15 | 单表 CRUD + 条件构造器 |
| 数据库 | **MySQL** | 8.0 | 唯一权威数据源 |
| 缓存 | **Redis** | 7 | 令牌黑名单、限流计数、验证码、列表缓存 |
| 消息队列 | **RabbitMQ** | 4 | 异步发邮件 + 死信队列 |
| 搜索引擎 | **Elasticsearch** | 9.5.3 | 全文检索 + **kNN 向量检索** |
| 对象存储 | **RustFS**（S3 兼容） | 1.0.1 | 图片存储（原 MinIO，因官方停止社区版而替换） |
| AI | **Spring AI** | 2.0.1 | 对接阿里百炼（OpenAI 协议兼容） |
| 认证 | **java-jwt** | 4.3.0 | HMAC256 签发/校验 |
| 文档 | **springdoc** | 3.1.1 | Swagger UI |

### 前端

| 类别 | 技术 | 版本 |
|---|---|---|
| 框架 | **Vue** | 3.5.32 |
| 路由 | **vue-router** | 5.0.7 |
| 状态 | **pinia** | 3.0.4 |
| UI | **Element Plus** | 2.14.0 |
| 请求 | **axios** | 1.16.1 |
| 富文本 | **Quill** | 2.0.3 |
| 构建 | **Vite** | 8.0.8 |

---

## 项目结构

```
Jwt/
├── Jwt-backend/                    # 后端（Spring Boot）
│   ├── src/main/java/com/example/
│   │   ├── config/                 # 安全、Web、RabbitMQ 配置
│   │   ├── controller/             # 8 个控制器（含 admin 子包）
│   │   ├── entity/                 # 实体 / VO / DTO
│   │   ├── filter/                 # traceId / JWT / 限流 / CORS 过滤器链
│   │   ├── listener/               # MQ 消费者
│   │   ├── mapper/                 # MyBatis-Plus Mapper
│   │   ├── repository/             # ES Repository
│   │   ├── service/                # 业务层（接口 + impl）
│   │   └── utils/                  # JWT / 缓存 / 存储 / 违禁词等工具
│   ├── src/main/resources/
│   │   ├── application.yml         # 主配置（激活 profile）
│   │   ├── application-dev.yml     # 开发环境公共配置
│   │   ├── application-local.yml   # 本机密钥（**已 gitignore**）
│   │   ├── application-prod.yml    # 生产配置（全用环境变量）
│   │   └── prohibited.json         # 违禁词表
│   ├── sql/schema.sql              # **唯一建表入口**（11 张表）
│   └── Dockerfile                  # 多阶段构建
│
├── jwt-frontend/                   # 前端（Vue 3）
│   ├── src/
│   │   ├── components/             # 通用组件（含 forum / admin 子目录）
│   │   ├── net/                    # axios 封装 + 各模块 API
│   │   ├── router/                 # 路由 + 权限守卫
│   │   ├── store/                  # pinia 状态
│   │   ├── views/                  # 页面（forum / user / ai / admin / settings / welcome）
│   │   └── utils/                  # 工具函数
│   ├── nginx.conf                  # 生产 Nginx 配置（含 SSE 关缓冲）
│   └── Dockerfile                  # 多阶段构建
│
├── docker-compose.yml              # 7 个服务编排
├── .env.example                    # Docker 环境变量模板
│
└── 项目文档/                        # 详细文档
    ├── 项目讲解文档.md              # 全项目技术讲解（16 章）
    ├── 接口文档.md
    ├── 数据库设计文档.md
    ├── DOCKER部署说明.md
    └── 补全进度.md                  # 交接文档
```

**代码规模**：Java 106 个文件 · Vue 30 个组件 · JS 11 个模块 · 11 张表 · 54 个接口

---

## 快速开始

### 方式一：Docker（推荐）

```bash
# 1. 准备环境变量
cp .env.example .env
# 编辑 .env，填入数据库密码、邮箱授权码、JWT 密钥、AI key

# 2. 启动中间件（MySQL / Redis / RabbitMQ / ES / RustFS）
docker compose up -d mysql redis rabbitmq elasticsearch rustfs

# 3. 确认全部健康
docker compose ps

# 4. 启动后端（本机跑，方便调试；也可 docker compose up -d backend）
cd Jwt-backend && mvn spring-boot:run

# 5. 启动前端
cd jwt-frontend && npm install && npm run dev
```

打开 http://localhost:5173

> **首次启动会自动建库**：MySQL 容器会执行 `Jwt-backend/sql/schema.sql`，建出全部 11 张表并写入 4 个分类。

### 方式二：全部本机安装

需要自行安装 MySQL 8 / Redis / RabbitMQ / Elasticsearch 9.x / 一个 S3 兼容对象存储，然后：

```bash
# 1. 建库
mysql -uroot -p < Jwt-backend/sql/schema.sql

# 2. 配置本机密钥
cp Jwt-backend/src/main/resources/application-local.example.yml \
   Jwt-backend/src/main/resources/application-local.yml
# 编辑，填入 DB 密码、邮箱授权码、JWT 密钥、AI key

# 3. 启动
cd Jwt-backend && mvn spring-boot:run
cd jwt-frontend && npm install && npm run dev
```

---

## 配置说明

### 三个 profile 的分工

| 文件 | 提交到仓库 | 内容 |
|---|---|---|
| `application.yml` | ✅ | 只决定激活哪些 profile |
| `application-dev.yml` | ✅ | 所有**不含密钥**的默认值，用 `${VAR:默认值}` 引用 |
| `application-local.yml` | ❌ **已 gitignore** | 真实密码 / 密钥 |
| `application-prod.yml` | ✅ | 全部用环境变量，Swagger 关闭 |

### 必须配置的环境变量

| 变量 | 说明 |
|---|---|
| `DB_PASSWORD` | MySQL root 密码 |
| `MAIL_PASSWORD` | 163 邮箱的 **SMTP 授权码**（不是登录密码） |
| `JWT_KEY` | JWT 签名密钥（换一次 = 所有令牌失效） |
| `AI_API_KEY` | 阿里百炼 key（留空则 AI 功能提示"未配置"） |
| `RUSTFS_ACCESS_KEY` / `RUSTFS_SECRET_KEY` | 对象存储凭据 |

> ⚠️ **AI 的 base-url 必须带 `/v1`**：Spring AI 2.x 用的是新版 OpenAI SDK，**不会自动补 `/v1`**。
>
> ```yaml
> spring.ai.openai.base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
> ```

---

## 详细文档

| 文档 | 内容 |
|---|---|
| [**项目讲解文档.md**](项目文档/项目讲解文档.md) | **全项目技术讲解**（16 章）：认证设计 / 论坛核心 / MQ / 搜索 / AI / 数据库 / 前端 / 部署 / 技术决策 / **面试高频点** |
| [接口文档.md](项目文档/接口文档.md) | 54 个接口的请求/响应说明 |
| [数据库设计文档.md](项目文档/数据库设计文档.md) | 11 张表结构、索引设计、6 条设计决策 |
| [DOCKER部署说明.md](项目文档/DOCKER部署说明.md) | 镜像、编排、环境变量、排查 |
| [补全进度.md](项目文档/补全进度.md) | 各阶段完成情况（交接文档） |

---

## 技术亮点

### 1. JWT 无状态 + Redis 黑名单

令牌本身不查库（纯读 claims），但"退出登录 / 封禁"需要立即失效 —— 用 Redis 存 jwtId 黑名单（TTL = 令牌剩余有效期），兼顾性能与可控性。

### 2. MySQL 是唯一权威，其余都是派生数据

```
MySQL（权威）
  ├→ ES 索引    ：写时增量 + 启动全量重建（失败不阻塞业务，可自愈）
  ├→ Redis 缓存 ：写时删除 + 读时重建（Cache-Aside）
  └→ RustFS    ：只存 URL，文件不可变，无需同步
```

**一致性边界清晰**：只有 MySQL 需要强一致，其他随时可重建。

### 3. 周额度不用定时任务

`db_account_ai_usage.week_start` 记录统计所属周，**读取时判断**是否需要重置 —— 服务停机不影响正确性，多实例天然幂等。

### 4. 点赞靠数据库唯一索引保并发

```sql
INSERT IGNORE INTO db_topic_interact_like (tid, uid, time) VALUES (?, ?, NOW());
```

先用 `SELECT` 判断再插入有竞态（两个请求同时查到"没点过"）；唯一索引 + `INSERT IGNORE` 把正确性交给数据库。

### 5. 隐私字段在服务端过滤

看别人主页时，对方设为"不公开"的字段在**服务端就置成 null** —— 绕过前端直接调接口也拿不到。

### 6. RAG 检索链路完整

```
用户提问
  → EmbeddingModel 向量化（1024 维）
  → ES kNN（num_candidates=50 粗筛 → topK=3）
  → 回 MySQL 查标题摘要
  → 组装 prompt（3 篇上限 / 每篇 400 字 / 总 1500 字）
  → 大模型生成 + 引用来源返回前端
```

**降级**：ES 或 embedding 失败时自动退回普通问答，不影响 AI 整体可用。

---

## 已知限制

| 限制 | 说明 |
|---|---|
| **ES 版本必须 9.x** | Spring Data ES 6.0.5 的客户端是 `elasticsearch-java 9.2.8`，连 8.x 会返回 400 |
| **对象存储已换 RustFS** | MinIO 官方停止发布社区版，`latest` 标签被撤 |
| **单体应用** | 未引入 Spring Cloud；规模匹配，拆分成本 > 收益 |
| **无 Canal** | 数据量小（几千条），全量重建 < 1 秒；引入 CDC 属于过度设计 |
| **前端无 ESLint** | 暂未配置，靠 Vue 编译检查 + 人工 review |

---

## 开发约定

- **验证分层**：`@RequestBody + @Valid` → `MethodArgumentNotValidException`；`@RequestParam + @Validated` → `ConstraintViolationException`
- **响应统一**：所有接口返回 `RestBean<T>` / `PageRestBean<T>`
- **头部约定**：鉴权用 `Authorization`；追踪用响应头里的 traceId
- **实体转换**：`BaseData.asViewObject(Class<T>)` 泛型转换，不用 MapStruct
- **包结构**：`controller` / `service` / `service.impl` / `mapper` / `entity.dto` / `entity.vo` 分层清晰

---

## License

个人学习项目，仅供学习参考。
