# ============================================================
# 前端镜像：Node 构建 → Nginx 托管静态文件
# ============================================================

# ---------- 阶段一：构建 ----------
FROM node:22-alpine AS build
WORKDIR /app

# 同样先拷依赖清单：锁文件没变时能复用缓存层
COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---------- 阶段二：Nginx ----------
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
