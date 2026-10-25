# Docker 一键部署说明

把项目的 **5 个中间件 + 前后端应用**全部容器化，一条命令拉起。

---

## 一、5 个中间件

| 中间件 | 镜像 | 端口 | 作用 |
|---|---|---|---|
| **MySQL** | `mysql:8.0` | 3306 | 业务数据（唯一权威数据源） |
| **Redis** | `redis:7-alpine` | 6379 | 缓存 / 限流 / 验证码 / 令牌黑名单 |
| **RabbitMQ** | `rabbitmq:4-management-alpine` | 5672（管理台 15672） | 异步邮件 + 死信队列 |
| **Elasticsearch** | `elasticsearch:9.5.3` | 9200 | 全文检索 + kNN 向量检索 |
| **RustFS** | `rustfs/rustfs:latest` | 9000（控制台 9001） | 图片对象存储（**替代 MinIO**） |

> ⚠️ **两个版本坑，都是踩过的**：
>
> **① ES 必须 9.x，不能用 8.x**
> Spring Data ES 6.0.5 带的客户端是 `elasticsearch-java 9.2.8`，连 8.x 服务端会返回 400：
> ```
> [es/indices.exists] Expecting a response body, but none was sent
> ```
> 注意：Docker Hub 上的 `elasticsearch` 镜像**只到 8.x**，9.x 只在 `docker.elastic.co`（国内加速器不管）。
> 本项目用的 `elasticsearch:9.5.3` 是从 Docker Hub 拉到并打了标准标签的。
>
> **② 对象存储已从 MinIO 换成 RustFS**
> MinIO 官方**停止发布社区版**（AGPL），`minio/minio` 的 `latest` 标签被撤，公共加速器也不镜像这个命名空间。
> RustFS 是 Rust 写的 S3 兼容实现，**端口和接口都与 MinIO 一致**，所以后端只是把配置项从 `minio.*` 改名为 `storage.*`，代码逻辑没动。

---

## 二、前置条件

1. **安装 Docker Desktop**（Windows 版）
   - 官网：https://www.docker.com/products/docker-desktop
   - 装完重启一次，`docker --version` 和 `docker compose version` 能跑即可
2. **给 Docker 分配至少 4GB 内存**
   - Docker Desktop → Settings → Resources → Memory
   - ES 要 512MB，加 MySQL / RabbitMQ / RustFS，2GB 会很吃力
3. **端口别被占用**：`3306 / 6379 / 5672 / 15672 / 9200 / 9000 / 9001 / 8081 / 80`
   - 如果本机装了 MySQL / RabbitMQ 服务，先停掉：
     ```powershell
     Stop-Service MySQL80; Stop-Service RabbitMQ     # 需要管理员
     ```

---

## 三、使用步骤

### 1. 准备环境变量

```powershell
cd D:\code\Java\Jwt
Copy-Item .env.example .env
# 编辑 .env，填数据库密码、邮箱授权码、JWT 密钥、AI key、RustFS 凭据
```

`.env` 在 `.gitignore` 中，**不会进版本库**；`.env.example` 是模板，可提交。

### 2. 启动

**方式一：只起中间件**（推荐日常开发 —— 后端仍用 IDEA 跑，方便断点调试）

```powershell
docker compose up -d mysql redis rabbitmq elasticsearch rustfs
```

**方式二：全都要**（含前后端应用，模拟真实部署）

```powershell
docker compose up -d
```

首次运行会**构建镜像 + 下载依赖**，约 5-15 分钟（取决于网速）。

### 3. 确认状态

```powershell
docker compose ps
```

期望看到 5 个容器，其中 4 个有 healthy 标记（Redis 没配 healthcheck，`Up` 即可）：

```
forum-mysql     Up (healthy)    3306
forum-redis     Up              6379
forum-rabbitmq  Up (healthy)    5672, 15672
forum-es        Up (healthy)    9200
forum-rustfs    Up (healthy)    9000, 9001
```

### 4. 访问地址

| 服务 | 地址 | 凭据 |
|---|---|---|
| 前端（方式二） | http://localhost | — |
| 后端接口 | http://localhost:8081 | — |
| Swagger 文档 | http://localhost:8081/swagger-ui/index.html | — |
| RabbitMQ 管理台 | http://localhost:15672 | `admin` / `admin`（见 `.env`） |
| **RustFS 控制台** | http://localhost:9001 | `rustfsadmin` / `rustfspassword`（见 `.env`） |
| Elasticsearch | http://localhost:9200 | — |

---

## 四、常用命令

```powershell
docker compose ps                       # 看状态
docker compose logs -f backend          # 跟后端日志
docker compose logs -f elasticsearch    # 跟 ES 日志（启动慢，排查看这个）
docker compose logs -f rustfs           # 跟 RustFS 日志
docker compose restart backend          # 重启后端
docker compose stop                     # 停止（容器保留）
docker compose down                     # 停止并删除容器（数据卷保留）
docker compose down -v                  # ⚠️ 连数据卷一起删 —— 数据全没
docker compose build --no-cache backend # 改了代码后重建后端镜像
```

---

## 五、实现要点

### 1. 数据库首启自动建表（**只有一个 `schema.sql`**）

`docker-compose.yml` 把 `Jwt-backend/sql` 挂到 MySQL 的初始化目录：

```yaml
volumes:
  - ./Jwt-backend/sql:/docker-entrypoint-initdb.d:ro
```

MySQL 容器**首次启动**会执行该目录下**所有 `.sql`**，建出全部 **11 张表** + 4 条分类数据。

> ⚠️ **两个必须知道的事**：
>
> **① 只在数据卷为空时执行**。改了 SQL 想重跑，得删卷：
> ```powershell
> docker compose stop mysql
> docker rm forum-mysql
> docker volume rm jwt_mysql-data
> docker compose up -d mysql
> ```
>
> **② `schema.sql` 开头必须有 `SET NAMES utf8mb4;`**
> MySQL 官方镜像的 entrypoint 执行初始化脚本时，**连接字符集默认是 `latin1`**。
> 文件是 UTF-8，于是「日常闲聊」的字节被当 latin1 读入、再按 utf8mb4 存表 —— **双重编码**，查出来是 `æ—¥å¸¸é—²èŠ‚`。
> 加这一行解决。**这是"本地看不出、一部署就暴露"的典型问题**（用 Navicat 手工执行不会有）。

### 2. 容器之间用「服务名」互访

`application-dev.yml` 里写的是 `127.0.0.1` —— 那是"所有服务都在宿主机"的场景。
**容器里 `127.0.0.1` 指向容器自己**，所以 `docker-compose.yml` 用环境变量覆盖：

```yaml
environment:
  SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/template...
  SPRING_DATA_REDIS_HOST: redis
  SPRING_RABBITMQ_HOST: rabbitmq
  SPRING_ELASTICSEARCH_URIS: http://elasticsearch:9200
  STORAGE_ENDPOINT: http://rustfs:9000          # ← 注意是 STORAGE_，不是 MINIO_
```

`mysql` / `redis` / `elasticsearch` / `rustfs` 就是 compose 里的**服务名**，Docker 内置 DNS 直接解析。

### 3. Nginx 必须关 SSE 缓冲

`jwt-frontend/nginx.conf` 反代 `/api/` 时加了：

```nginx
proxy_buffering off;
proxy_cache     off;
proxy_read_timeout 300s;
```

**原因**：AI 问答用的是 **SSE 流式输出**，Nginx 默认会把响应攒进缓冲区再一起发 ——
那样前端看不到"逐字出现"的效果，而是一起蹦出来或者卡住。

开发环境下 `vite.config.js` 的代理也做了同样处理（`configure` 里改 `cache-control` 和 `x-accel-buffering`）。

### 4. 多阶段构建让镜像更小

后端 `Dockerfile` 用 Maven 镜像编译，再把 jar 拷进精简的 JRE 镜像：

```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build     # 构建阶段（几百 MB，不进最终镜像）
...
FROM eclipse-temurin:17-jre-alpine             # 运行阶段（只留 jar）
```

并且**先拷 `pom.xml` 下载依赖、再拷 `src`** —— 只要依赖没变，改 Java 代码能命中 Docker 层缓存，不用重新下载几百 MB。

### 5. `.dockerignore` 别省

前端 `node_modules` 几百 MB，后端 `target` 也不小。没有 `.dockerignore` 的话，
`COPY . .` 会把这些全塞进构建上下文，构建慢得离谱，镜像也白白变大。

### 6. RustFS 用**命名卷**，不用宿主机目录

RustFS 容器以**非 root 用户（ID 10001）**运行。如果挂宿主机目录，需要先 `chown 10001`，否则权限拒绝。
所以 compose 里用的是**命名卷** `rustfs-data`，Docker 自动处理权限。

---

## 六、为什么全用 Docker

项目**早期**有一套本机方案：手动装 5 个中间件，用一个 PowerShell 脚本（`start-services.ps1`）拉起 3 个前台进程。

**那套方案已经废弃并删除**，原因：

| | 本机方案（已弃用） | **Docker（现在）** |
|---|---|---|
| 首次准备 | 手动装 5 个软件（各自要配环境） | **只装 Docker** |
| 换电脑 | 重新装 5 个软件 | **`docker compose up -d`** |
| 版本一致性 | 各自装的可能不同 | **镜像锁死版本** |
| 启动方式 | 开 3 个窗口 + 2 个 Windows 服务 | **一条命令** |
| 清理 | 手动卸载，注册表残留 | **`down -v` 删卷** |

**唯一的代价**：容器里调试后端不方便 —— 所以日常开发**后端仍在 IDEA 里跑**，只用 Docker 起中间件（方式一）。

---

## 七、常见问题排查

| 现象 | 原因 | 解决 |
|---|---|---|
| `port is already allocated` | 端口被本机服务占用 | `Stop-Service MySQL80` / `Stop-Service RabbitMQ`，或查 `netstat -ano \| findstr :3306` |
| 后端报 `Table doesn't exist` | 建表脚本没执行（卷不是空的） | 删卷重建（见第五节） |
| 中文变 `æ—¥å¸¸` | `schema.sql` 缺 `SET NAMES utf8mb4;` | 加上后重建数据库 |
| 后端报 ES 400 | ES 服务端是 8.x | 换成 `elasticsearch:9.5.3` |
| 图片上传失败 | RustFS 没起来或凭据不对 | `docker compose logs -f rustfs`，核对 `.env` 里的 `RUSTFS_ACCESS_KEY` |
| AI 报 404 | base-url 缺 `/v1` | 见 `application-dev.yml` 注释，Spring AI 2.x 不会自动补 |
| `down -v` 后数据没了 | 数据卷被删 | 这是预期行为；重要数据先 `mysqldump` |
