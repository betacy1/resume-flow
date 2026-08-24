#!/bin/bash
# ResumeFlow ECS 一键部署脚本（在服务器上执行：bash deploy_ecs.sh）
# 前提：dist.zip 已传到 /opt/resumeflow/，jar 已传到 /root/workspace/target/
set -x

# ---------- 1. 管理后台：替换静态文件（nginx 直接生效） ----------
cd /opt/resumeflow
rm -rf dist
unzip -o dist.zip

# ---------- 2. 后端：停旧进程 ----------
OLD_PID=$(ps -ef | grep 'resume-flow-backend' | grep -v grep | awk '{print $2}')
if [ -n "$OLD_PID" ]; then
  kill $OLD_PID
  sleep 5
  # 若仍存活则强杀
  ps -p $OLD_PID > /dev/null 2>&1 && kill -9 $OLD_PID
fi

# ---------- 3. 后端：启动新版本 ----------
# 线上 RDS 已初始化过完整数据：带 DEMO_INIT_ENABLED=true 只会命中"已初始化，跳过"分支，
# 不会清空您在管理端维护的数据（如需重建初始数据才保留该变量）。
cd /root/workspace
MYSQL_HOST=rm-2ze95444hz8egty00ro.mysql.cn-beijing.rds.aliyuncs.com \
MYSQL_PORT=3306 \
MYSQL_DATABASE=resume_flow \
MYSQL_USERNAME=root \
MYSQL_PASSWORD='HMhyx2000628!' \
DEMO_INIT_ENABLED=true \
nohup java -jar target/resume-flow-backend-1.0.0.jar --spring.profiles.active=prod --server.port=8081 > app.log 2>&1 &

# ---------- 4. 等待启动 ----------
for i in $(seq 1 60); do
  sleep 2
  grep -q 'Started ResumeFlowApplication' app.log && break
done
tail -n 20 app.log
echo "===== 端口检查 ====="
ss -tlnp | grep 8081

# ---------- 5. 接口验证 ----------
TOKEN=$(curl -s -X POST http://127.0.0.1/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"123456"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])" 2>/dev/null)
echo "===== sync/status ====="
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/sync/status
echo
echo "===== preferences ====="
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/preferences | head -c 200
echo
echo "===== data/export 字段统计 ====="
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/data/export \
  | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print('version:',d['profileVersion'],'fields:',len(d['customFields']),'materials:',len(d['materials']))"
echo "===== skills ====="
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/skills | head -c 200
echo
echo "部署完成"
