import redis
import json

r = redis.Redis(host='localhost', port=6379, decode_responses=True)
status_map = r.hgetall("game:rider:status")
targets_map = r.hgetall("game:rider:targets")
orders_map = r.hgetall("game:rider:orders")

print(f"status 数量: {len(status_map)}")
print("部分 status 样本:")
for k in list(status_map.keys())[:10]:
    print(f"  {k}: status={status_map[k]}, order={orders_map.get(k)}, target={targets_map.get(k)}")

riders_json = r.get("game:state:riders")
if riders_json:
    riders = json.loads(riders_json)
    print(f"\nRedis 中的 riders 数量: {len(riders)}")
    print("部分 rider 物理坐标样本:")
    for rdr in riders[:5]:
        print(f"  {rdr['id']}: pos=({rdr['currentPosition']['x']:.2f}, {rdr['currentPosition']['y']:.2f}), target={rdr.get('targetPosition')}, speed={rdr.get('speed')}, status={rdr.get('status')}")
