# Docker 一键部署说明

把项目的 5 个中间件和前后端应用全部容器化，**一条命令拉起**。

---

## 一、为什么需要它

这个项目依赖 5 个中间件：

| 中间件 | 端口 | 作用 |
|---|---|---|
| MySQL 8.0 | 3306 | 业务数据 |
| Redis 7 | 6379 | 缓存 / 限流 / 验证码 / 令牌黑名单 |
| RabbitMQ 4 | 5672（管理台 15672） | 异步邮件 + 死信队列 |
| Elasticsearch 9.5.4 | 9200 | 帖子全文检索 |
| MinIO | 9000（控制台 9001） | 图片对象存储 |

以前要**手动装 5 个软件、开 3 个窗口**（`start-services.ps1` 只能管其中 3 个前台进程）。
用 Docker 之后：

~~~powershell
docker compose up -d      # 全部起来
docker compose down       # 全部停掉
~~~

**换电脑也不用重装** —— 只要装了 Docker。

---

## 二、前置条件

1. **安装 Docker Desktop**（Windows 版）
   - 官网：https://www.docker.com/products/docker-desktop
   - 装完重启一次，命令行能跑 `docker --version` 和 `docker compose version` 即可
2. **给 Docker 分配至少 4GB 内存**
   - Docker Desktop → Settings → Resources → Memory
   - 光 Elasticsearch 就要 512MB，再加 MySQL / RabbitMQ / MinIO，2GB 会很吃力
3. **端口别被占用**：3306 / 6379 / 5672 / 9200 / 9000 / 9001 / 8081 / 80
   - 如果本机已经装了 MySQL 或 Redis 并占着端口，先停掉它们

---

## 三、使用步骤

### 1. 准备环境变量

~~~powershell
cd D:\code\Java\Jwt
Copy-Item .env.example .env
# 然后编辑 .env，填上数据库密码、JWT 密钥、AI key 等
~~~

`.env` 已在 `.gitignore` 中，**不会进版本库**；`.env.example` 是模板，可以提交。

### 2. 启动

**方式一：只起中间件**（推荐日常开发用 —— 后端仍用 IDEA/Maven 跑，方便断点调试）

~~~powershell
docker compose up -d mysql redis rabbitmq elasticsearch minio
~~~

**方式二：全都要**（含前后端应用，模拟真实部署）

~~~powershell
docker compose up -d
~~~

首次运行会**构建镜像 + 下载依赖**，大约 5-15 分钟（取决于网速）。

### 3. 确认状态

~~~powershell
docker compose ps
~~~

所有服务应该是 `running`（healthy）状态。

### 4. 访问

| 服务 | 地址 |
|---|---|
| 前端（方式二） | http://localhost |
| 后端接口 | http://localhost:8081 |
| Swagger 文档 | http://localhost:8081/swagger-ui/index.html |
| RabbitMQ 管理台 | http://localhost:15672（admin / admin） |
| MinIO 控制台 | http://localhost:9001（minioadmin / minipassword） |
| Elasticsearch | http://localhost:9200 |

---

## 四、常用命令

~~~powershell
docker compose ps                      # 看状态
docker compose logs -f backend         # 跟后端日志
docker compose logs -f elasticsearch   # 跟 ES 日志（启动慢，看这个排查）
docker compose restart backend         # 重启后端
docker compose stop                    # 停止（容器保留）
docker compose down                    # 停止并删除容器（数据卷保留）
docker compose down -v                 # ⚠️ 连数据卷一起删 —— 数据全没
docker compose build --no-cache backend  # 改了代码后重建后端镜像
~~~

---

## 五、几个实现要点

### 1. 数据库首启自动建表

`docker-compose.yml` 里把 `Jwt-backend/sql` 挂到了 MySQL 的 `/docker-entrypoint-initdb.d`：

~~~yaml
volumes:
  - ./Jwt-backend/sql:/docker-entrypoint-initdb.d:ro
~~~

MySQL 容器**首次启动**时会按文件名顺序执行里面的 `.sql`，所以建表不用手动做。
⚠️ 只在**数据卷为空时**执行 —— 如果改了 SQL 想重跑，得 `docker compose down -v` 清掉数据。

### 2. 容器之间用「服务名」互相访问

`application-dev.yml` 里写的是 `127.0.0.1`，那是给"所有服务都跑在宿主机"的场景用的。
容器里 `127.0.0.1` 指向容器自己，所以 `docker-compose.yml` 里用环境变量覆盖：

~~~yaml
environment:
  SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/template...
  SPRING_DATA_REDIS_HOST: redis
  SPRING_RABBITMQ_HOST: rabbitmq
  SPRING_ELASTICSEARCH_URIS: http://elasticsearch:9200
  MINIO_ENDPOINT: http://minio:9000
~~~

`mysql` / `redis` / `elasticsearch` 这些就是 compose 里的服务名，Docker 内置 DNS 能直接解析。

### 3. Nginx 必须关 SSE 缓冲

`nginx.conf` 里反代 `/api/` 时加了：

~~~nginx
proxy_buffering off;
proxy_cache     off;
proxy_read_timeout 300s;
~~~

**原因**：AI 问答用的是 SSE 流式输出，Nginx 默认会把响应攒进缓冲区再一起发 ——
那样前端就看不到"逐字出现"的效果，而是一起蹦出来或者干脆卡住。
（`vite.config.js` 的 dev 代理里也做了同样的处理。）

### 4. 多阶段构建让镜像更小

后端 `Dockerfile` 用 Maven 镜像编译，再把 jar 拷进精简的 JRE 镜像：

~~~dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build     # 构建阶段（几百 MB，不进最终镜像）
...
FROM eclipse-temurin:17-jre-alpine              # 运行阶段（只留 jar）
~~~

并且**先拷 `pom.xml` 下载依赖、再拷 `src`** —— 这样只要依赖没变，改 Java 代码时
能命中 Docker 的层缓存，不用每次重新下载几百 MB。

### 5. `.dockerignore` 别省

前端 `node_modules` 有几百 MB，后端 `target` 也不小。没有 `.dockerignore` 的话，
`COPY . .` 会把这些全塞进构建上下文，构建会慢得离谱，镜像也会白白变大。

---

## 六、和「不用 Docker」的方案怎么选

| | Docker | start-services.ps1 |
|---|---|---|
| 首次准备 | 装 Docker Desktop（约 500MB） | 手动装 5 个中间件 |
| 换电脑 | **只要装 Docker** | 重新装 5 个软件 |
| 内存占用 | 略高（每个容器有开销） | 略低 |
| 后端调试 | 容器里不方便断点 | **IDEA 里直接调试** |
| 版本一致性 | **镜像锁定版本，团队一致** | 各自装的版本可能不同 |

**建议**：日常开发用 `docker compose up -d mysql redis rabbitmq elasticsearch minio`
只起中间件，后端仍在 IDEA 里跑（方便调试）；要演示/部署时再 `docker compose up -d` 全起。
