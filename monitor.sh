#!/bin/bash
# 传感器应用监控脚本

# 设置变量
LOG_FILE="./logs/monitor.log"
HEALTH_CHECK_URL="http://localhost:8080/actuator/health"
NOTIFY_EMAIL=""
CHECK_INTERVAL=60 # 秒

# 显示帮助信息
function show_help {
  echo "传感器应用监控脚本"
  echo "用法: ./monitor.sh [选项]"
  echo ""
  echo "选项:"
  echo "  --daemon       作为守护进程运行"
  echo "  --email=EMAIL  设置通知邮箱"
  echo "  --interval=N   设置检查间隔（秒）"
  echo "  --status       检查当前状态"
  echo "  --help         显示帮助信息"
  echo ""
  echo "示例: ./monitor.sh --daemon --email=admin@example.com --interval=300"
}

# 初始化参数
RUN_AS_DAEMON=0
CHECK_STATUS=0

# 解析参数
for arg in "$@"; do
  case $arg in
    --daemon)
      RUN_AS_DAEMON=1
      shift
      ;;
    --email=*)
      NOTIFY_EMAIL="${arg#*=}"
      shift
      ;;
    --interval=*)
      CHECK_INTERVAL="${arg#*=}"
      shift
      ;;
    --status)
      CHECK_STATUS=1
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

# 创建日志目录
mkdir -p ./logs

# 日志函数
log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a $LOG_FILE
}

# 检查依赖
check_dependencies() {
  if ! command -v curl &> /dev/null; then
    log "错误: 找不到curl命令"
    exit 1
  fi
  
  if [ ! -z "$NOTIFY_EMAIL" ] && ! command -v mail &> /dev/null; then
    log "警告: 找不到mail命令，邮件通知将不可用"
  fi
}

# 发送通知
send_notification() {
  local subject="$1"
  local message="$2"
  
  log "$subject: $message"
  
  if [ ! -z "$NOTIFY_EMAIL" ] && command -v mail &> /dev/null; then
    echo "$message" | mail -s "$subject" "$NOTIFY_EMAIL"
    log "通知已发送至 $NOTIFY_EMAIL"
  fi
}

# 检查应用状态
check_app_status() {
  log "检查应用状态..."
  
  # 检查容器状态
  if ! docker-compose ps | grep -q "Up"; then
    send_notification "应用容器异常" "容器不在运行状态"
    return 1
  fi
  
  # 健康检查
  local health_status=$(curl -s -o /dev/null -w "%{http_code}" $HEALTH_CHECK_URL)
  if [ "$health_status" != "200" ]; then
    send_notification "应用健康检查失败" "HTTP状态码: $health_status"
    return 1
  fi
  
  # 检查资源使用情况
  local cpu_usage=$(docker stats --no-stream --format "{{.CPUPerc}}" $(docker-compose ps -q app))
  local mem_usage=$(docker stats --no-stream --format "{{.MemUsage}}" $(docker-compose ps -q app))
  
  log "CPU使用率: $cpu_usage"
  log "内存使用情况: $mem_usage"
  
  # 判断资源使用是否过高
  local cpu_value=${cpu_usage//%/}
  if (( $(echo "$cpu_value > 80" | bc -l) )); then
    send_notification "CPU使用率过高" "当前使用率: $cpu_usage"
  fi
  
  return 0
}

# 主监控循环
monitor_loop() {
  log "开始监控 (间隔: ${CHECK_INTERVAL}秒)"
  
  while true; do
    check_app_status
    sleep $CHECK_INTERVAL
  done
}

# 检查依赖
check_dependencies

# 如果仅检查状态
if [ $CHECK_STATUS -eq 1 ]; then
  check_app_status
  exit $?
fi

# 以守护进程方式运行
if [ $RUN_AS_DAEMON -eq 1 ]; then
  log "以守护进程方式启动监控..."
  nohup $0 > /dev/null 2>&1 &
  echo "监控进程已启动 (PID: $!)"
  exit 0
fi

# 正常运行监控循环
monitor_loop 