#!/bin/bash
# Flink传感器模拟器启动脚本

set -e

# 显示帮助信息
function show_help {
  echo "Flink传感器模拟器启动脚本"
  echo "用法: ./run-sensor-app.sh [选项]"
  echo ""
  echo "选项:"
  echo "  --build       重新构建应用"
  echo "  --run         运行应用 (默认)"
  echo "  --dashboard   显示仪表板URL"
  echo "  --help        显示帮助信息"
  echo ""
  echo "示例: ./run-sensor-app.sh --build --run"
}

# 解析参数
BUILD=0
RUN=1
DASHBOARD=0

for arg in "$@"; do
  case $arg in
    --build)
      BUILD=1
      shift
      ;;
    --run)
      RUN=1
      shift
      ;;
    --dashboard)
      DASHBOARD=1
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

# 设置端口
APP_PORT=8080
APP_URL="http://localhost:${APP_PORT}"
DASHBOARD_URL="${APP_URL}/actuator"
WEBSOCKET_URL="ws://localhost:${APP_PORT}/wx-socket"

# Maven 构建
if [ $BUILD -eq 1 ]; then
  echo "正在构建应用..."
  mvn clean package -DskipTests
  
  if [ $? -ne 0 ]; then
    echo "构建失败！"
    exit 1
  fi
  
  echo "构建成功！"
fi

# 运行应用
if [ $RUN -eq 1 ]; then
  echo "正在启动Flink传感器模拟器应用..."
  echo "应用将在端口 ${APP_PORT} 上运行"
  echo "WebSocket端点: ${WEBSOCKET_URL}"
  
  # 检查Java环境
  if ! [ -x "$(command -v java)" ]; then
    echo "错误: Java未安装或不在PATH中" >&2
    exit 1
  fi
  
  # 选择JAR文件
  JAR_FILE="target/flink-sensor-simulator-1.0-SNAPSHOT.jar"
  if [ ! -f "$JAR_FILE" ]; then
    echo "错误: JAR文件不存在，请先使用 --build 选项构建应用" >&2
    exit 1
  fi
  
  # 设置JVM参数
  JAVA_OPTS="-Xmx256m"
  
  # 运行应用
  java $JAVA_OPTS -jar "$JAR_FILE" &
  PID=$!
  
  echo "应用已启动，PID: $PID"
  echo "正在等待应用启动..."
  sleep 5
  
  # 检查应用是否成功启动
  if ps -p $PID > /dev/null; then
    echo "应用已成功启动！"
    echo "Web访问地址: ${APP_URL}"
    echo "监控面板地址: ${DASHBOARD_URL}"
    
    # 打开浏览器预览页面
    if command -v xdg-open &> /dev/null; then
      xdg-open "${APP_URL}" &
    elif command -v open &> /dev/null; then
      open "${APP_URL}" &
    elif command -v start &> /dev/null; then
      start "${APP_URL}" &
    else
      echo "无法自动打开浏览器，请手动访问: ${APP_URL}"
    fi
    
    # 监听Ctrl+C以优雅地关闭应用
    trap "echo '正在关闭应用...'; kill $PID; exit 0" SIGINT SIGTERM
    
    # 在前台等待，以便可以使用Ctrl+C关闭
    wait $PID
  else
    echo "应用启动失败！"
    exit 1
  fi
fi

# 显示仪表板URL
if [ $DASHBOARD -eq 1 ]; then
  echo "监控面板地址:"
  echo "- 主页: ${DASHBOARD_URL}"
  echo "- WebSocket统计: ${DASHBOARD_URL}/websocket-stats"
  echo "- 数据缓存状态: ${DASHBOARD_URL}/data-cache"
  echo "- 消息队列状态: ${DASHBOARD_URL}/message-queue"
  echo "- 异常统计: ${DASHBOARD_URL}/exception-stats"
  echo "- Prometheus指标: ${DASHBOARD_URL}/prometheus"
  echo ""
  
  # 打开浏览器预览监控面板
  if command -v xdg-open &> /dev/null; then
    xdg-open "${DASHBOARD_URL}" &
  elif command -v open &> /dev/null; then
    open "${DASHBOARD_URL}" &
  elif command -v start &> /dev/null; then
    start "${DASHBOARD_URL}" &
  else
    echo "无法自动打开浏览器，请手动访问: ${DASHBOARD_URL}"
  fi
fi

exit 0 