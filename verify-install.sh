#!/bin/bash
# 传感器应用安装验证脚本

# 设置文本颜色
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # 重置颜色

echo "==============================================="
echo "    传感器应用安装环境验证脚本"
echo "==============================================="

# 检查必要的命令
check_command() {
  if command -v $1 &> /dev/null; then
    echo -e "${GREEN}✓${NC} $1 已安装"
    return 0
  else
    echo -e "${RED}✗${NC} $1 未安装"
    return 1
  fi
}

# 检查Java版本
check_java() {
  if command -v java &> /dev/null; then
    java_version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
    echo -e "${GREEN}✓${NC} Java 已安装, 版本: $java_version"
    
    # 检查Java版本是否至少为11
    java_major=$(echo $java_version | awk -F '.' '{print $1}')
    if [[ $java_version == 1.* ]]; then
      # Java 1.x版本
      java_major=$(echo $java_version | awk -F '.' '{print $2}')
    fi
    
    if [[ $java_major -lt 11 ]]; then
      echo -e "${YELLOW}⚠${NC} Java版本低于推荐版本 (11+)"
    fi
  else
    echo -e "${RED}✗${NC} Java 未安装"
  fi
}

# 检查Maven版本
check_maven() {
  if command -v mvn &> /dev/null; then
    mvn_version=$(mvn --version 2>&1 | awk '/Apache Maven/ {print $3}')
    echo -e "${GREEN}✓${NC} Maven 已安装, 版本: $mvn_version"
  else
    echo -e "${RED}✗${NC} Maven 未安装"
  fi
}

# 检查Docker环境
check_docker() {
  if command -v docker &> /dev/null; then
    docker_version=$(docker --version | awk '{print $3}' | tr -d ',')
    echo -e "${GREEN}✓${NC} Docker 已安装, 版本: $docker_version"
    
    # 验证docker是否正常工作
    if docker info &>/dev/null; then
      echo -e "${GREEN}✓${NC} Docker 守护进程运行正常"
    else
      echo -e "${RED}✗${NC} Docker 守护进程未运行"
    fi
  else
    echo -e "${RED}✗${NC} Docker 未安装"
  fi
}

# 检查Docker Compose环境
check_docker_compose() {
  if command -v docker-compose &> /dev/null; then
    compose_version=$(docker-compose --version | awk '{print $3}' | tr -d ',')
    echo -e "${GREEN}✓${NC} Docker Compose 已安装, 版本: $compose_version"
  elif command -v docker &> /dev/null && docker compose version &>/dev/null; then
    compose_version=$(docker compose version --short)
    echo -e "${GREEN}✓${NC} Docker Compose (插件) 已安装, 版本: $compose_version"
  else
    echo -e "${RED}✗${NC} Docker Compose 未安装"
  fi
}

# 检查网络连接
check_network() {
  echo ""
  echo "检查网络连接："
  
  # 测试到Maven中央仓库的连接
  if curl -s --connect-timeout 5 https://repo1.maven.org/maven2/ &>/dev/null; then
    echo -e "${GREEN}✓${NC} Maven仓库连接正常"
  else
    echo -e "${RED}✗${NC} 无法连接到Maven仓库"
  fi
  
  # 测试到Docker Hub的连接
  if curl -s --connect-timeout 5 https://registry-1.docker.io/v2/ &>/dev/null; then
    echo -e "${GREEN}✓${NC} Docker Hub连接正常"
  else
    echo -e "${RED}✗${NC} 无法连接到Docker Hub"
  fi
}

# 检查文件系统权限
check_permissions() {
  echo ""
  echo "检查文件系统权限："
  
  # 测试当前目录写入权限
  if touch test_permission &>/dev/null && rm test_permission &>/dev/null; then
    echo -e "${GREEN}✓${NC} 当前目录写入权限正常"
  else
    echo -e "${RED}✗${NC} 无法在当前目录写入文件"
  fi
  
  # 创建并检查必要的目录
  for dir in "nginx/logs" "nginx/ssl" "logs" "backups"; do
    if mkdir -p "$dir" &>/dev/null; then
      echo -e "${GREEN}✓${NC} 目录 $dir 创建/访问正常"
    else
      echo -e "${RED}✗${NC} 无法创建/访问目录 $dir"
    fi
  done
}

# 检查脚本权限
check_script_permissions() {
  echo ""
  echo "检查脚本执行权限："
  
  for script in "deploy.sh" "backup.sh" "monitor.sh" "verify-install.sh"; do
    if [[ -f "$script" ]]; then
      if [[ -x "$script" ]]; then
        echo -e "${GREEN}✓${NC} $script 具有执行权限"
      else
        echo -e "${YELLOW}⚠${NC} $script 不具有执行权限"
        echo "    运行以下命令授予执行权限: chmod +x $script"
      fi
    else
      echo -e "${YELLOW}⚠${NC} $script 文件不存在"
    fi
  done
}

# 检查端口占用情况
check_ports() {
  echo ""
  echo "检查端口占用情况："
  
  for port in 8080 8443 80 443; do
    if command -v netstat &>/dev/null; then
      if netstat -tuln | grep ":$port " &>/dev/null; then
        echo -e "${YELLOW}⚠${NC} 端口 $port 已被占用"
      else
        echo -e "${GREEN}✓${NC} 端口 $port 可用"
      fi
    elif command -v ss &>/dev/null; then
      if ss -tuln | grep ":$port " &>/dev/null; then
        echo -e "${YELLOW}⚠${NC} 端口 $port 已被占用"
      else
        echo -e "${GREEN}✓${NC} 端口 $port 可用"
      fi
    else
      echo -e "${YELLOW}⚠${NC} 无法检查端口 $port (netstat/ss 命令不可用)"
    fi
  done
}

# 运行所有检查
echo ""
echo "检查必要组件："
check_java
check_maven
check_command curl
check_command openssl
check_docker
check_docker_compose
check_command tar
check_command bc

# 检查其他方面
check_network
check_permissions
check_script_permissions
check_ports

# 提供总结和建议
echo ""
echo "==============================================="
echo "              环境检查完成"
echo "==============================================="
echo ""
echo "如果发现任何标记为 ${RED}✗${NC} 的项目，请先解决这些问题后再继续安装。"
echo "标记为 ${YELLOW}⚠${NC} 的项目是警告，可以考虑解决但不是必须的。"
echo ""
echo "下一步建议："
echo "1. 确保所有必要的组件已安装"
echo "2. 使用 ./deploy.sh --build --deploy 部署应用"
echo "3. 使用 ./monitor.sh --status 验证应用状态"
echo ""
echo "详细的部署指南请参考 DEPLOYMENT.md"
echo "自动化脚本文档请参考 AUTOMATION.md"
echo "==============================================="

exit 0 