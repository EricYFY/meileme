import os
import json
import time
import uuid

MAPS_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "saved_maps")

class MapStorage:
    def __init__(self):
        os.makedirs(MAPS_DIR, exist_ok=True)
        self._ensure_default_maps()

    def _ensure_default_maps(self):
        """如果地图库为空，自动创建一张经典的预设示范地图"""
        if not os.listdir(MAPS_DIR):
            from map_generator import MapGenerator
            gen = MapGenerator(merchant_count=10)
            gen.generate()
            map_data = gen.get_map_data()
            
            default_map = {
                "id": "map_default_preset",
                "name": "经典示范城市 (Preset)",
                "createdAt": int(time.time() * 1000),
                "updatedAt": int(time.time() * 1000),
                "min": map_data["min"],
                "max": map_data["max"],
                "grid": map_data["grid"]
            }
            file_path = os.path.join(MAPS_DIR, f"{default_map['id']}.json")
            with open(file_path, "w", encoding="utf-8") as f:
                json.dump(default_map, f, ensure_ascii=False)
            print("[MapStorage] 已自动创建经典预设示范地图: map_default_preset")

    def list_maps(self):
        """获取所有已保存地图的概要信息"""
        maps = []
        for fname in os.listdir(MAPS_DIR):
            if fname.endswith(".json"):
                fpath = os.path.join(MAPS_DIR, fname)
                try:
                    with open(fpath, "r", encoding="utf-8") as f:
                        data = json.load(f)
                        # 计算道路数量等元数据
                        grid = data.get("grid", [])
                        road_count = sum(row.count(1) + row.count(2) + row.count(3) for row in grid) if grid else 0
                        maps.append({
                            "id": data.get("id"),
                            "name": data.get("name", "未命名地图"),
                            "createdAt": data.get("createdAt", int(time.time() * 1000)),
                            "updatedAt": data.get("updatedAt", int(time.time() * 1000)),
                            "roadCount": road_count
                        })
                except Exception as e:
                    print(f"[MapStorage] 读取地图文件 {fname} 失败: {e}")
        # 按更新时间倒序排序
        maps.sort(key=lambda m: m["updatedAt"], reverse=True)
        return maps

    def get_map(self, map_id):
        """获取指定 ID 的地图详情"""
        fpath = os.path.join(MAPS_DIR, f"{map_id}.json")
        if not os.path.exists(fpath):
            return None
        try:
            with open(fpath, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception as e:
            print(f"[MapStorage] 加载地图 {map_id} 异常: {e}")
            return None

    def save_map(self, name, grid, map_id=None):
        """保存或更新地图"""
        now = int(time.time() * 1000)
        if not map_id:
            map_id = f"map_{uuid.uuid4().hex[:8]}"
            created_at = now
        else:
            existing = self.get_map(map_id)
            created_at = existing.get("createdAt", now) if existing else now

        map_data = {
            "id": map_id,
            "name": name or "未命名地图",
            "createdAt": created_at,
            "updatedAt": now,
            "min": -100,
            "max": 100,
            "grid": grid
        }

        fpath = os.path.join(MAPS_DIR, f"{map_id}.json")
        with open(fpath, "w", encoding="utf-8") as f:
            json.dump(map_data, f, ensure_ascii=False)
            
        print(f"[MapStorage] 地图已成功保存: {name} (ID: {map_id})")
        return map_data

    def delete_map(self, map_id):
        """删除指定地图"""
        fpath = os.path.join(MAPS_DIR, f"{map_id}.json")
        if os.path.exists(fpath):
            os.remove(fpath)
            print(f"[MapStorage] 地图已删除: {map_id}")
            return True
        return False
