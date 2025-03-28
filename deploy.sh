#!/bin/bash
# 传感器应用部署脚本

set -e  # 如果有命令失败则退出

# 显示帮助信息
function show_help {
  echo "传感器应用部署脚本"
  echo "用法: ./deploy.sh [选项]"
  echo ""
  echo "选项:"
  echo "  --build       构建应用"
  echo "  --deploy      部署应用"
  echo "  --restart     重启服务"
  echo "  --logs        查看日志"
  echo "  --help        显示帮助信息"
  echo ""
  echo "示例: ./deploy.sh --build --deploy"
}

# 解析参数
build=0
deploy=0
restart=0
logs=0

for arg in "$@"; do
  case $arg in
    --build)
      build=1
      shift
      ;;
    --deploy)
      deploy=1
      shift
      ;;
    --restart)
      restart=1
      shift
      ;;
    --logs)
      logs=1
      shift
      ;;
    --help)
      show_help
      exit 0
      ;;
    *)
      echo "未知参数: $arg"
      show_help
      exit 1
      ;;
  esac
done

# 如果没有参数，显示帮助
if [ $# -eq 0 ] && [ $build -eq 0 ] && [ $deploy -eq 0 ] && [ $restart -eq 0 ] && [ $logs -eq 0 ]; then
  show_help
  exit 0
fi

# 创建必要的目录
echo "确保必要的目录存在..."
mkdir -p nginx/logs
mkdir -p nginx/ssl
mkdir -p logs

# 构建应用
if [ $build -eq 1 ]; then
  echo "构建Java应用..."
  mvn clean package -DskipTests
fi

# 检查SSL证书
if [ $deploy -eq 1 ]; then
  if [ ! -f nginx/ssl/cert.pem ] || [ ! -f nginx/ssl/key.pem ]; then
    echo "警告: SSL证书文件不存在!"
    read -p "是否要生成自签名证书用于测试? (y/n) " -n 1 -r
    echo ""
    if [[ $REPLY =~ ^[Yy]$ ]]; then
      echo "生成自签名SSL证书..."
      openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
        -keyout nginx/ssl/key.pem \
        -out nginx/ssl/cert.pem
    else
      echo "请在继续前将SSL证书放置在 nginx/ssl/ 目录下"
      exit 1
    fi
  fi
fi

# 部署应用
if [ $deploy -eq 1 ]; then
  echo "部署应用..."
  docker-compose up -d --build
  
  # 等待服务启动
  echo "等待服务启动..."
  sleep 5
  
  # 检查服务状态
  docker-compose ps
fi

# 重启服务
if [ $restart -eq 1 ]; then
  echo "重启服务..."
  docker-compose restart
fi

# 查看日志
if [ $logs -eq 1 ]; then
  echo "查看应用日志..."
  docker-compose logs -f app
fi

echo "完成!"
exit 0 