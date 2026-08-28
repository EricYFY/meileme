import redis
import json

r = redis.Redis(host='localhost', port=6379, decode_responses=True)

riders_json = r.get("game:state:riders")
if riders_json:
    riders = json.loads(riders_json)
    print(f"当前模拟中骑手数量: {len(riders)}")
    idle_count = sum(1 for x in riders if x.get("status") == 0)
    pickup_count = sum(1 for x in riders if x.get("status") == 1)
    delivering_count = sum(1 for x in riders if x.get("status") == 2)
    print(f"  - 空闲骑手: {idle_count}")
    print(f"  - 取餐中: {pickup_count}")
    print(f"  - 配送中: {delivering_count}")
else:
    print("当前未启动模拟或处于就绪待命状态。")
