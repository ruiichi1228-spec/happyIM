#!/bin/bash
# HappyIM 压力测试脚本
# 用法: bash stress_test.sh

GATEWAY="http://localhost:8080"
EMAIL="test@test.com"
PASSWORD="12345678"

echo "=== HappyIM 压力测试 ==="
echo ""

# 1. 登录获取 token
echo "1. 登录..."
LOGIN=$(curl -s -X POST "$GATEWAY/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"code\":\"000000\"}")
TOKEN=$(echo $LOGIN | python3 -c "import sys,json;print(json.load(sys.stdin).get('data',{}).get('accessToken',''))" 2>/dev/null)

if [ -z "$TOKEN" ]; then
  echo "  登录失败，请先注册账号并获取正确验证码"
  echo "  返回: $LOGIN"
  exit 1
fi
echo "  Token: ${TOKEN:0:20}..."

# 2. 并发测试函数
run_concurrent() {
  local name=$1
  local url=$2
  local method=$3
  local body=$4
  local concurrency=$5
  local count=$6

  echo ""
  echo "=== $name ==="
  echo "  并发: $concurrency, 总请求: $count"

  start=$(date +%s%N)

  success=0
  fail=0
  total_time=0

  for i in $(seq 1 $count); do
    (
      if [ "$method" = "POST" ]; then
        resp=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$url" \
          -H "Content-Type: application/json" \
          -H "Authorization: Bearer $TOKEN" \
          -d "$body" 2>/dev/null)
      else
        resp=$(curl -s -o /dev/null -w "%{http_code}" "$url" \
          -H "Authorization: Bearer $TOKEN" 2>/dev/null)
      fi
      if [ "$resp" = "200" ]; then
        echo -n "."
      else
        echo -n "x"
      fi
    ) &

    # 控制并发数
    if [ $((i % concurrency)) -eq 0 ]; then
      wait
    fi
  done
  wait

  end=$(date +%s%N)
  elapsed=$(( (end - start) / 1000000 ))

  echo ""
  echo "  耗时: ${elapsed}ms"
  echo "  QPS: $(( count * 1000 / (elapsed + 1) ))"
}

# 3. 跑测试场景
run_concurrent "会话列表" "$GATEWAY/api/conversations" "GET" "" 10 100

run_concurrent "用户资料" "$GATEWAY/api/users/me" "GET" "" 20 200

echo ""
echo "=== 压测完成 ==="
echo ""
echo "安装 JMeter GUI 版出详细报告: https://jmeter.apache.org/download_jmeter.cgi"
