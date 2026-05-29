#!/bin/bash
# SkyWalking 链路追踪启动脚本
# 用法: bash scripts/start_with_skywalking.sh

set -e
cd "$(dirname "$0")/.."

AGENT_DIR="./skywalking-agent"
AGENT_VERSION="9.4.0"
AGENT_JAR="$AGENT_DIR/skywalking-agent.jar"

# 1. 下载 Java Agent（如果还没有）
if [ ! -f "$AGENT_JAR" ]; then
  echo "📥 下载 SkyWalking Java Agent $AGENT_VERSION ..."
  mkdir -p "$AGENT_DIR"
  curl -L "https://dlcdn.apache.org/skywalking/java-agent/$AGENT_VERSION/apache-skywalking-java-agent-$AGENT_VERSION.tgz" \
    | tar -xz -C "$AGENT_DIR" --strip-components=1
  echo "✅ Agent 下载完成"
fi

# 2. 启动 SkyWalking
echo "🚀 启动 SkyWalking OAP + UI..."
docker-compose up -d skywalking-oap skywalking-ui
echo "⏳ 等待 SkyWalking 启动..."
sleep 15

# 3. 编译项目
echo "🔨 编译项目..."
cd backend && mvn clean package -DskipTests -q && cd ..

# 4. 启动微服务（带 SkyWalking Agent）
AGENT_OPTS="-javaagent:$AGENT_JAR -Dskywalking.agent.service_name=happyim -Dskywalking.collector.backend_service=localhost:11800"

echo ""
echo "=== 启动服务（每个开一个新终端）==="
echo ""
echo "java $AGENT_OPTS -jar backend/services/user-service/target/user-service-1.0.0.jar"
echo "java $AGENT_OPTS -jar backend/services/chat-service/target/chat-service-1.0.0.jar"
echo "java $AGENT_OPTS -jar backend/services/content-service/target/content-service-1.0.0.jar"
echo "java $AGENT_OPTS -jar backend/services/chat-ws/target/chat-ws-1.0.0.jar"
echo "java $AGENT_OPTS -jar backend/gateway/target/happyim-gateway-1.0.0.jar"
echo ""
echo "🌐 SkyWalking UI: http://localhost:8089"
echo "📊 Grafana:       http://localhost:3000"
echo "🔧 Nacos:         http://localhost:8848/nacos"
