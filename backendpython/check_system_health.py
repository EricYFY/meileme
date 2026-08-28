import sys
import os
import urllib.request
import urllib.error
import json
import redis

def check_redis():
    print("【1. Redis 服务检查 (Port 6379)】")
    try:
        r = redis.Redis(host='localhost', port=6379, socket_timeout=2)
        r.ping()
        keys_count = len(r.keys("*"))
        print(f"  ✅ Redis 运行正常，当前 Key 数量: {keys_count}")
        return True
    except Exception as e:
        print(f"  ❌ Redis 连接失败: {e}")
        return False

def check_python():
    print("\n【2. Python 物理引擎检查 (Port 8081)】")
    try:
        req = urllib.request.Request("http://localhost:8081/api/maps", headers={"User-Agent": "HealthChecker"})
        with urllib.request.urlopen(req, timeout=3) as resp:
            data = json.loads(resp.read().decode('utf-8'))
            print(f"  ✅ Python FastAPI 服务运行正常 (HTTP {resp.status})，已加载地图数: {len(data)}")
            return True
    except Exception as e:
        print(f"  ❌ Python 服务不可达: {e}")
        return False

def check_java():
    print("\n【3. Java 调度与网关检查 (Port 8080)】")
    try:
        # Java 暴露在 8080 端口
        req = urllib.request.Request("http://localhost:8080/", headers={"User-Agent": "HealthChecker"})
        with urllib.request.urlopen(req, timeout=3) as resp:
            print(f"  ✅ Java 服务响应正常 (HTTP {resp.status})")
            return True
    except urllib.error.HTTPError as e:
        # Spring Boot 默认根路径返回 404 或 Whitelabel Error Page 也表明端口存活
        print(f"  ✅ Java Spring Boot 端口存活 (HTTP {e.code})")
        return True
    except Exception as e:
        print(f"  ❌ Java 服务连接失败: {e}")
        return False

def check_frontend():
    print("\n【4. 前端 Vite Web 服务检查 (Port 5173)】")
    try:
        req = urllib.request.Request("http://localhost:5173/", headers={"User-Agent": "HealthChecker"})
        with urllib.request.urlopen(req, timeout=3) as resp:
            print(f"  ✅ 前端 Vite 开发服务器运行正常 (HTTP {resp.status})")
            return True
    except Exception as e:
        print(f"  ❌ 前端服务连接失败: {e}")
        return False

if __name__ == "__main__":
    print("========== 🚀 美了么系统全链路健康检查 ==========\n")
    r_ok = check_redis()
    p_ok = check_python()
    j_ok = check_java()
    f_ok = check_frontend()
    
    print("\n================================================")
    if r_ok and p_ok and j_ok and f_ok:
        print("🎉 全系统 4 大服务全部健康运行中，随时可以开始游戏与模拟！")
    else:
        print("⚠️ 存在未就绪服务，请根据上方提示进行检查。")
