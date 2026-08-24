#!/bin/bash
# ResumeFlow ECS 干净重启脚本（在服务器上执行：bash restart_clean.sh）
# 目的：停止当前实例，带 DEMO_INIT_ENABLED=true 与独立时间戳日志重启，
#       验证初始化标志与服务健康；不再覆盖 app.log。
# 说明：初始化器幂等——demo 数据已完整时仅打印"已初始化，跳过"，不会覆盖线上维护的数据。
set -x

# ---------- 1. 停旧进程 ----------
OLD_PID=$(ps -ef | grep 'resume-flow-backend' | grep -v grep | awk '{print $2}')
if [ -n "$OLD_PID" ]; then
  kill $OLD_PID
  sleep 5
  ps -p $OLD_PID > /dev/null 2>&1 && kill -9 $OLD_PID
fi
# 确认端口已释放
ss -tlnp | grep 8081 && { echo "端口 8081 仍被占用，中止"; exit 1; }

# ---------- 2. 带 DEMO_INIT_ENABLED=true 启动，使用独立时间戳日志 ----------
cd /root/workspace
LOG_FILE=app-$(date +%Y%m%d-%H%M%S).log
MYSQL_HOST=rm-2ze95444hz8egty00ro.mysql.cn-beijing.rds.aliyuncs.com \
MYSQL_PORT=3306 \
MYSQL_DATABASE=resume_flow \
MYSQL_USERNAME=root \
MYSQL_PASSWORD='HMhyx2000628!' \
DEMO_INIT_ENABLED=true \
nohup java -jar target/resume-flow-backend-1.0.0.jar --spring.profiles.active=prod --server.port=8081 > "$LOG_FILE" 2>&1 &
NEW_PID=$!
echo "新进程 PID=$NEW_PID，日志文件=$LOG_FILE"

# ---------- 3. 等待启动完成 ----------
for i in $(seq 1 60); do
  sleep 2
  grep -q 'Started ResumeFlowApplication' "$LOG_FILE" && break
done

echo "===== 启动与初始化标志 ====="
grep -E 'Started ResumeFlowApplication|demo 用户数据已初始化|开始初始化 demo|跳过 demo 数据初始化' "$LOG_FILE"

echo "===== 端口检查 ====="
ss -tlnp | grep 8081

# ---------- 4. 接口验证 ----------
TOKEN=$(curl -s -X POST http://127.0.0.1/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"123456"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])" 2>/dev/null)
if [ -z "$TOKEN" ]; then
  echo "登录失败，请检查 $LOG_FILE"
  exit 1
fi
echo "===== sync/status ====="
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/sync/status
echo
echo "重启完成。后续排查请使用独立日志：/root/workspace/$LOG_FILE"
