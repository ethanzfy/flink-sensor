# 传感器应用自动化脚本

本文档详细介绍了传感器数据应用提供的自动化脚本，这些脚本用于简化部署、监控和维护过程。

## 脚本概述

项目提供了四个主要的自动化脚本：

1. **deploy.sh** - 部署脚本，用于构建和部署应用
2. **backup.sh** - 备份脚本，用于创建应用配置和数据的备份
3. **monitor.sh** - 监控脚本，用于监控应用状态和资源使用情况
4. **verify-install.sh** - 验证脚本，用于检查安装环境和依赖项

## 部署脚本 (deploy.sh)

### 功能

- 构建Java应用
- 部署容器服务
- 重启服务
- 查看应用日志
- 自动生成SSL证书（用于测试）

### 用法

```bash
# 显示帮助信息
./deploy.sh --help

# 构建并部署应用
./deploy.sh --build --deploy

# 仅构建应用（不部署）
./deploy.sh --build

# 仅部署应用（不重新构建）
./deploy.sh --deploy

# 重启所有服务
./deploy.sh --restart

# 查看应用日志
./deploy.sh --logs
```

### 工作流程

1. **构建阶段** (`--build`):
   - 执行 `mvn clean package -DskipTests` 构建Java应用
   - 生成目标JAR文件

2. **部署阶段** (`--deploy`):
   - 检查SSL证书是否存在，如不存在可生成自签名证书
   - 使用docker-compose构建和启动容器
   - 显示服务状态

3. **日志查看** (`--logs`):
   - 使用docker-compose显示应用容器的实时日志

## 备份脚本 (backup.sh)

### 功能

- 创建应用配置文件的备份
- 可选择备份完整应用数据
- 可选择包含日志文件
- 管理备份历史记录

### 用法

```bash
# 显示帮助信息
./backup.sh --help

# 默认备份（包含应用数据，不包含日志）
./backup.sh

# 仅备份配置文件
./backup.sh --config-only

# 备份所有内容，包括日志文件
./backup.sh --with-logs
```

### 备份内容

1. **配置文件** (始终包含):
   - Dockerfile
   - docker-compose.yml
   - Nginx配置文件
   - SSL证书

2. **应用数据** (默认包含，使用 `--config-only` 可排除):
   - 源代码文件
   - Maven配置
   - 文档文件
   - 部署脚本

3. **日志文件** (使用 `--with-logs` 包含):
   - 应用日志
   - Nginx日志
   - 监控日志

### 备份存储

- 所有备份存储在 `backups/` 目录下
- 备份文件格式：`sensor_backup_YYYYMMDD_HHMMSS.tar.gz`
- 脚本执行时会显示当前可用的所有备份

## 监控脚本 (monitor.sh)

### 功能

- 检查应用容器状态
- 健康检查API调用
- 监控资源使用情况（CPU、内存）
- 阈值告警（高CPU使用率）
- 可配置的邮件通知

### 用法

```bash
# 显示帮助信息
./monitor.sh --help

# 检查当前应用状态
./monitor.sh --status

# 以守护进程模式启动持续监控
./monitor.sh --daemon

# 自定义监控间隔
./monitor.sh --interval=300  # 每5分钟检查一次

# 设置告警邮箱
./monitor.sh --email=admin@example.com

# 组合使用
./monitor.sh --daemon --interval=300 --email=admin@example.com
```

### 监控内容

1. **容器状态检查**:
   - 验证Docker容器是否正常运行

2. **API健康检查**:
   - 调用Spring Boot Actuator健康检查端点
   - 检查HTTP状态码是否为200

3. **资源监控**:
   - 监控CPU使用率
   - 监控内存使用情况
   - 当CPU使用率超过80%时触发告警

### 日志和通知

- 监控日志保存在 `logs/monitor.log`
- 配置邮箱后，异常情况将发送邮件通知
- 日志包含时间戳、事件类型和详细信息

## 环境验证脚本 (verify-install.sh)

### 功能

- 检查系统中必要的软件依赖
- 验证Java和Maven版本
- 检查Docker和Docker Compose环境
- 测试网络连接状态
- 验证文件系统权限
- 检查端口占用情况
- 提供详细的环境检查报告

### 用法

```bash
# 运行环境检查
./verify-install.sh
```

### 检查内容

1. **软件依赖检查**:
   - Java (版本11+)
   - Maven
   - Docker
   - Docker Compose
   - curl, openssl, tar, bc等工具

2. **网络连接**:
   - Maven中央仓库连接测试
   - Docker Hub连接测试

3. **文件系统权限**:
   - 当前目录写入权限
   - 必要目录的创建和访问权限

4. **脚本权限**:
   - 验证所有脚本的执行权限

5. **端口检查**:
   - 验证应用所需端口(8080, 8443, 80, 443)是否可用

### 输出说明

脚本使用颜色编码输出检查结果：
- **绿色✓**: 通过检查，无需操作
- **红色✗**: 检查失败，需要解决才能继续
- **黄色⚠**: 警告，可能需要注意但不阻碍安装

脚本最后会提供总结和后续步骤建议。

## 自动化运维建议

### 定时任务配置

建议使用crontab配置以下定时任务：

```bash
# 每5分钟检查应用状态
*/5 * * * * /path/to/monitor.sh --status

# 每天凌晨2点创建配置备份
0 2 * * * /path/to/backup.sh --config-only

# 每周日凌晨3点创建完整备份
0 3 * * 0 /path/to/backup.sh --with-logs
```

### 监控系统集成

监控脚本设计为可与现有监控系统集成：

- 使用 `--status` 选项可以提供退出代码（0=正常, 非0=异常）
- 可以将输出通过管道传递给其他监控工具
- 日志文件可以被日志聚合工具收集

## 脚本依赖

这些脚本依赖以下工具：

1. **deploy.sh**:
   - docker, docker-compose
   - maven
   - openssl (用于生成SSL证书)

2. **backup.sh**:
   - tar
   - bash 4.0+

3. **monitor.sh**:
   - curl
   - docker, docker-compose
   - mail (可选，用于发送通知)
   - bc (用于浮点数比较)

## 故障排除

### 部署脚本问题

- **构建失败**: 检查Maven配置和依赖
- **SSL证书问题**: 确保nginx/ssl目录存在且有写入权限
- **容器启动失败**: 检查端口冲突和环境变量

### 备份脚本问题

- **权限错误**: 确保有读取所有文件的权限
- **空间不足**: 检查磁盘空间，特别是包含日志时
- **备份文件过大**: 考虑使用 `--config-only` 仅备份配置

### 监控脚本问题

- **假阳性告警**: 调整 `--interval` 参数避免临时资源峰值
- **邮件未发送**: 确认系统mail命令可用
- **监控进程异常终止**: 检查系统资源限制

### 验证脚本问题

- **颜色显示异常**: 某些终端可能不支持ANSI颜色代码，请尝试在不同终端运行
- **版本检测不准确**: 某些系统下版本提取可能不精确，请手动验证版本要求
- **端口检查失败**: 如果netstat和ss都不可用，手动检查端口占用情况 