#!/bin/bash

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置
APP_NAME="Flink传感器模拟器"
JAR_FILE="target/flink-sensor-simulator-1.0-SNAPSHOT.jar"
DEFAULT_PORT=8080
PID_FILE=".app.pid"

# 显示帮助信息
show_help() {
    echo -e "${BLUE}${APP_NAME} - 帮助信息${NC}"
    echo ""
    echo "用法: $0 [选项]"
    echo ""
    echo "选项:"
    echo "  --build          构建应用程序"
    echo "  --run            运行应用程序 (默认)"
    echo "  --stop           停止应用程序"
    echo "  --status         查看应用程序状态"
    echo "  --open           在浏览器中打开应用程序"
    echo "  --help           显示帮助信息"
    echo ""
    echo "示例:"
    echo "  $0 --build --run   构建并运行应用程序"
    echo "  $0 --status        查看应用程序运行状态"
    echo ""
}

# 参数解析
BUILD=false
RUN=false
STOP=false
STATUS=false
OPEN=false

# 如果没有提供参数，默认显示帮助信息
if [ $# -eq 0 ]; then
    RUN=true
fi

# 解析命令行参数
while [[ $# -gt 0 ]]; do
    case "$1" in
        --build)
            BUILD=true
            shift
            ;;
        --run)
            RUN=true
            shift
            ;;
        --stop)
            STOP=true
            shift
            ;;
        --status)
            STATUS=true
            shift
            ;;
        --open)
            OPEN=true
            shift
            ;;
        --help)
            show_help
            exit 0
            ;;
        *)
            echo -e "${RED}未知选项: $1${NC}"
            show_help
            exit 1
            ;;
    esac
done

# 检查Java环境
check_java() {
    if ! command -v java &> /dev/null; then
        echo -e "${RED}错误: 未找到Java${NC}"
        echo "请安装Java 11或更高版本"
        exit 1
    fi
    
    JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
    echo -e "${GREEN}Java版本: $JAVA_VERSION${NC}"
}

# 检查Maven环境
check_maven() {
    if ! command -v mvn &> /dev/null; then
        echo -e "${RED}错误: 未找到Maven${NC}"
        echo "请安装Maven 3.6或更高版本"
        exit 1
    fi
    
    MVN_VERSION=$(mvn --version | awk '/Apache Maven/ {print $3}')
    echo -e "${GREEN}Maven版本: $MVN_VERSION${NC}"
}

# 构建应用程序
build_app() {
    echo -e "${BLUE}开始构建${APP_NAME}...${NC}"
    check_maven
    
    mvn clean package -DskipTests
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}构建成功!${NC}"
    else
        echo -e "${RED}构建失败!${NC}"
        exit 1
    fi
}

# 检查应用是否正在运行
is_app_running() {
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        if ps -p $PID > /dev/null; then
            return 0  # 正在运行
        fi
    fi
    return 1  # 未运行
}

# 获取应用程序的PID
get_app_pid() {
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        if ps -p $PID > /dev/null; then
            echo $PID
            return 0
        fi
    fi
    
    # 尝试通过进程查找
    PID=$(ps aux | grep java | grep "$JAR_FILE" | grep -v grep | awk '{print $2}')
    if [ -n "$PID" ]; then
        echo $PID
        return 0
    fi
    
    return 1
}

# 运行应用程序
run_app() {
    echo -e "${BLUE}正在启动${APP_NAME}...${NC}"
    check_java
    
    if is_app_running; then
        echo -e "${YELLOW}警告: 应用程序已经在运行!${NC}"
        show_app_status
        return
    fi
    
    if [ ! -f "$JAR_FILE" ]; then
        echo -e "${YELLOW}JAR文件不存在，需要先构建应用程序${NC}"
        build_app
    fi
    
    echo -e "使用以下JVM参数启动:"
    echo -e "  -Xms256m -Xmx512m"
    
    # 启动应用并将PID保存到文件
    nohup java -Xms256m -Xmx512m -jar "$JAR_FILE" > app.log 2>&1 &
    APP_PID=$!
    echo $APP_PID > "$PID_FILE"
    
    echo -e "${GREEN}应用程序已启动 (PID: $APP_PID)${NC}"
    
    # 等待应用启动
    echo -ne "${YELLOW}等待应用程序启动"
    for i in {1..20}; do
        echo -ne "."
        sleep 0.5
        # 检查端口是否已开放
        if command -v nc &> /dev/null; then
            if nc -z localhost $DEFAULT_PORT; then
                break
            fi
        fi
    done
    echo -e "${NC}"
    
    # 检查应用是否成功启动
    if is_app_running; then
        echo -e "${GREEN}应用程序已成功启动!${NC}"
        echo -e "访问: ${BLUE}http://localhost:$DEFAULT_PORT${NC}"
    else
        echo -e "${RED}应用程序启动失败!${NC}"
        echo -e "请检查 app.log 文件获取详细信息"
    fi
}

# 停止应用程序
stop_app() {
    echo -e "${BLUE}正在停止${APP_NAME}...${NC}"
    
    APP_PID=$(get_app_pid)
    if [ $? -eq 0 ]; then
        echo -e "正在停止PID为 $APP_PID 的应用程序..."
        kill $APP_PID
        
        # 等待进程结束
        for i in {1..10}; do
            if ! ps -p $APP_PID > /dev/null; then
                break
            fi
            echo -ne "."
            sleep 1
        done
        echo ""
        
        # 如果进程仍在运行，强制终止
        if ps -p $APP_PID > /dev/null; then
            echo -e "${YELLOW}应用程序没有响应，正在强制终止...${NC}"
            kill -9 $APP_PID
        fi
        
        # 删除PID文件
        rm -f "$PID_FILE"
        
        echo -e "${GREEN}应用程序已停止${NC}"
    else
        echo -e "${YELLOW}应用程序未运行${NC}"
    fi
}

# 显示应用程序状态
show_app_status() {
    echo -e "${BLUE}${APP_NAME}状态:${NC}"
    
    if is_app_running; then
        APP_PID=$(get_app_pid)
        UPTIME=$(ps -o etime= -p $APP_PID)
        
        echo -e "${GREEN}状态: 运行中${NC}"
        echo -e "PID: $APP_PID"
        echo -e "运行时间: $UPTIME"
        echo -e "端口: $DEFAULT_PORT"
        echo -e "访问: ${BLUE}http://localhost:$DEFAULT_PORT${NC}"
        
        # 检查内存使用情况
        if command -v ps &> /dev/null; then
            MEM_USAGE=$(ps -o rss= -p $APP_PID | awk '{print $1/1024 " MB"}')
            echo -e "内存使用: $MEM_USAGE"
        fi
    else
        echo -e "${RED}状态: 未运行${NC}"
    fi
}

# 在浏览器中打开应用
open_in_browser() {
    URL="http://localhost:$DEFAULT_PORT"
    echo -e "${BLUE}正在打开 $URL${NC}"
    
    # 根据操作系统打开浏览器
    case "$(uname)" in
        "Darwin")  # macOS
            open "$URL"
            ;;
        "MINGW"*)  # Windows Git Bash
            start "$URL"
            ;;
        "Linux")   # Linux
            if command -v xdg-open &> /dev/null; then
                xdg-open "$URL"
            elif command -v gnome-open &> /dev/null; then
                gnome-open "$URL"
            else
                echo -e "${YELLOW}无法自动打开浏览器，请手动访问: $URL${NC}"
            fi
            ;;
        *)
            echo -e "${YELLOW}无法自动打开浏览器，请手动访问: $URL${NC}"
            ;;
    esac
}

# 执行命令
if [ "$BUILD" = true ]; then
    build_app
fi

if [ "$RUN" = true ]; then
    run_app
fi

if [ "$STOP" = true ]; then
    stop_app
fi

if [ "$STATUS" = true ]; then
    show_app_status
fi

if [ "$OPEN" = true ]; then
    open_in_browser
fi

# 如果应用程序正在运行且执行了--run，则自动打开浏览器
if [ "$RUN" = true ] && is_app_running; then
    sleep 2  # 给应用程序一点时间初始化
    open_in_browser
fi

exit 0 