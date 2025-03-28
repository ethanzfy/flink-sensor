#!/bin/bash
# 传感器应用备份脚本

# 设置变量
BACKUP_DIR="./backups"
DATE=$(date +"%Y%m%d_%H%M%S")
BACKUP_NAME="sensor_backup_$DATE"

# 显示帮助信息
function show_help {
  echo "传感器应用备份脚本"
  echo "用法: ./backup.sh [选项]"
  echo ""
  echo "选项:"
  echo "  --config-only  仅备份配置文件"
  echo "  --with-logs    包含日志文件"
  echo "  --help         显示帮助信息"
  echo ""
  echo "示例: ./backup.sh --with-logs"
}

# 初始化参数
CONFIG_ONLY=0
WITH_LOGS=0

# 解析参数
for arg in "$@"; do
  case $arg in
    --config-only)
      CONFIG_ONLY=1
      shift
      ;;
    --with-logs)
      WITH_LOGS=1
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

# 创建备份目录
mkdir -p $BACKUP_DIR

echo "开始备份..."
echo "备份名称: $BACKUP_NAME"

# 创建临时目录
TEMP_DIR="/tmp/$BACKUP_NAME"
mkdir -p $TEMP_DIR

# 备份配置文件
echo "备份配置文件..."
mkdir -p $TEMP_DIR/config
cp docker-compose.yml $TEMP_DIR/config/
cp Dockerfile $TEMP_DIR/config/
cp -r nginx/conf.d $TEMP_DIR/config/
cp nginx/nginx.conf $TEMP_DIR/config/
cp -r nginx/ssl $TEMP_DIR/config/

# 备份应用数据（如果不是仅配置模式）
if [ $CONFIG_ONLY -eq 0 ]; then
  echo "备份应用数据..."
  # 备份应用相关文件，按需修改
  mkdir -p $TEMP_DIR/app
  cp -r src $TEMP_DIR/app/
  cp pom.xml $TEMP_DIR/app/
  cp *.md $TEMP_DIR/app/
  cp *.sh $TEMP_DIR/app/
fi

# 备份日志（如果需要）
if [ $WITH_LOGS -eq 1 ]; then
  echo "备份日志文件..."
  mkdir -p $TEMP_DIR/logs
  cp -r logs/* $TEMP_DIR/logs/ 2>/dev/null || true
  cp -r nginx/logs/* $TEMP_DIR/logs/nginx/ 2>/dev/null || true
fi

# 创建压缩文件
echo "创建压缩文件..."
tar -czf "$BACKUP_DIR/$BACKUP_NAME.tar.gz" -C /tmp $BACKUP_NAME

# 清理临时文件
rm -rf $TEMP_DIR

echo "备份完成: $BACKUP_DIR/$BACKUP_NAME.tar.gz"

# 列出可用备份
echo ""
echo "可用备份:"
ls -lh $BACKUP_DIR/

exit 0 