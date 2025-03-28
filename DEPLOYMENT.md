# 传感器数据应用部署指南

本文档提供了使用Docker和Nginx部署传感器数据应用的详细说明。

## 部署架构

系统采用以下架构部署：

1. **Java 应用容器**：运行Spring Boot应用程序，处理传感器数据和WebSocket通信
2. **Nginx代理**：处理SSL终止、WebSocket代理和HTTP请求路由

## 前提条件

- Docker和Docker Compose已安装
- Maven已安装（用于构建应用）
- 已准备好SSL证书（用于HTTPS）

## 目录结构

部署所需文件应按以下结构组织：

```
/project-root
├── Dockerfile                # Java应用容器配置
├── docker-compose.yml        # 容器编排配置
├── deploy.sh                 # 部署脚本
├── backup.sh                 # 备份脚本
├── monitor.sh                # 监控脚本
├── pom.xml                   # Maven构建配置
├── src/                      # 源代码
├── target/                   # 构建产物
├── logs/                     # 日志目录
├── backups/                  # 备份目录
└── nginx/                    # Nginx配置
    ├── nginx.conf            # 主配置文件
    ├── conf.d/               # 站点配置
    │   └── default.conf      # 默认站点配置
    ├── logs/                 # Nginx日志
    └── ssl/                  # SSL证书
        ├── cert.pem          # 证书文件
        └── key.pem           # 私钥文件
```

## 部署步骤

### 1. 构建Java应用

```bash
mvn clean package
```

### 2. 准备SSL证书

将您的SSL证书和私钥文件放在 `nginx/ssl/` 目录下：
- 证书文件：`nginx/ssl/cert.pem`
- 私钥文件：`nginx/ssl/key.pem`

如果使用自签名证书进行测试，可以使用以下命令生成：

```bash
mkdir -p nginx/ssl
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout nginx/ssl/key.pem \
  -out nginx/ssl/cert.pem
```

### 3. 创建必要的目录

```bash
mkdir -p nginx/logs
mkdir -p logs
mkdir -p backups
```

### 4. 修改配置（如需要）

- 更新 `nginx/conf.d/default.conf` 中的 `server_name` 为您的域名
- 根据需要调整Docker容器的资源限制
- 自定义其他Nginx设置

### 5. 启动容器

手动启动：
```bash
docker-compose up -d
```

或使用部署脚本：
```bash
./deploy.sh --build --deploy
```

### 6. 检查服务状态

```bash
docker-compose ps
```

或使用监控脚本：
```bash
./monitor.sh --status
```

## 自动化脚本

项目提供了三个自动化脚本，简化部署和维护过程：

### 部署脚本 (deploy.sh)

部署脚本用于自动化构建和部署过程。

```bash
# 显示帮助信息
./deploy.sh --help

# 构建并部署应用
./deploy.sh --build --deploy

# 仅重启服务
./deploy.sh --restart

# 查看应用日志
./deploy.sh --logs
```

### 备份脚本 (backup.sh)

备份脚本用于定期备份项目配置和数据。

```bash
# 显示帮助信息
./backup.sh --help

# 全量备份（包含应用数据）
./backup.sh

# 仅备份配置文件
./backup.sh --config-only

# 包含日志文件的备份
./backup.sh --with-logs
```

备份文件将保存在 `backups/` 目录下，格式为 `sensor_backup_YYYYMMDD_HHMMSS.tar.gz`。

### 监控脚本 (monitor.sh)

监控脚本用于检查应用健康状态和资源使用情况。

```bash
# 显示帮助信息
./monitor.sh --help

# 检查当前应用状态
./monitor.sh --status

# 以守护进程模式启动监控（每60秒检查一次）
./monitor.sh --daemon

# 自定义监控间隔和通知邮箱
./monitor.sh --daemon --interval=300 --email=admin@example.com
```

监控日志保存在 `logs/monitor.log`。

## 配置说明

### Dockerfile

- 基础镜像：eclipse-temurin:17-jdk-jammy
- JVM参数：-Xmx256m -XX:+UseZGC
- 暴露端口：8080, 8443
- 健康检查：通过Spring Boot Actuator的/actuator/health端点

### Nginx配置

Nginx配置为WebSocket通信和API访问提供了以下功能：

1. **WebSocket代理**
   - 路径：/wx-socket
   - 超时设置：300秒
   - 保持长连接

2. **SSL终止**
   - 支持TLSv1.2和TLSv1.3
   - 配置了安全的密码套件
   - 启用HSTS

3. **压缩与性能**
   - 启用gzip压缩（对静态资源和API响应）
   - WebSocket通信不进行压缩（已有自己的压缩机制）
   - 静态资源缓存

4. **安全设置**
   - HTTP请求自动重定向到HTTPS
   - 添加了安全相关的HTTP头
   - 限制特定路径的请求频率

## 维护与故障排除

### 查看日志

- 应用日志：`docker-compose logs app` 或 `./deploy.sh --logs`
- Nginx访问日志：`tail -f nginx/logs/access.log`
- Nginx错误日志：`tail -f nginx/logs/error.log`
- 监控日志：`cat logs/monitor.log`

### 重启服务

```bash
# 重启所有服务
docker-compose restart
# 或使用脚本
./deploy.sh --restart

# 仅重启应用程序
docker-compose restart app

# 仅重启Nginx
docker-compose restart nginx
```

### 更新应用

1. 重新构建应用：`mvn clean package`
2. 重新构建并启动容器：`docker-compose up -d --build app`

或使用部署脚本一键完成：
```bash
./deploy.sh --build --deploy
```

### 备份与恢复

创建备份：
```bash
./backup.sh
```

恢复备份：
```bash
tar -xzf backups/sensor_backup_YYYYMMDD_HHMMSS.tar.gz -C /tmp
# 根据需要复制文件回原位置
```

## 性能与调优

- JVM内存设置可在docker-compose.yml中的JAVA_OPTS环境变量调整
- Nginx的worker_processes配置为auto，会自动适应CPU核心数
- 对于高负载场景，可能需要增加app服务的实例数并配置负载均衡

## 安全考虑

- 容器以非root用户运行，增强安全性
- Nginx配置了安全的SSL设置和HTTP头
- 已限制对敏感路径的访问

## 生产环境建议

1. **监控**：使用内置监控脚本或添加Prometheus监控和Grafana仪表板
2. **日志管理**：使用ELK或其他日志集中管理工具
3. **自动化部署**：使用内置部署脚本或集成CI/CD流程
4. **负载均衡**：对于高负载场景，考虑添加负载均衡器
5. **数据备份**：使用内置备份脚本实施定期数据备份策略
6. **自动化运维**：
   - 配置定时任务执行监控：`crontab -e` 添加 `*/5 * * * * /path/to/monitor.sh --status`
   - 配置定时备份：`crontab -e` 添加 `0 2 * * * /path/to/backup.sh --config-only` 