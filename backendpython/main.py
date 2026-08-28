from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional, List
import random
from map_generator import MapGenerator, TILE_COM_LOW, TILE_COM_HIGH, TILE_RES_LOW, TILE_RES_HIGH, ROAD_TILES
from engine import SimulationEngine
from map_storage import MapStorage

app = FastAPI()

# 配置 CORS 允许前端跨域访问
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

map_storage = MapStorage()

# 全局变量保存当前状态
current_map_data = None
current_engine = None

class StartRequest(BaseModel):
    merchantCount: int
    riderCount: int
    mapId: Optional[str] = None

class MapSaveRequest(BaseModel):
    name: str
    grid: List[List[int]]

def extract_merchants_and_res_cells(grid, merchant_count, grid_min=-100):
    """从给定的自定义网格中智能提取 8-邻域临路商家和临路送餐点"""
    size = len(grid)
    roadside_com_cells = []
    residential_cells = []

    for i in range(size):
        for j in range(size):
            tile = grid[i][j]
            # 判断 8-邻域是否有道路
            has_road = False
            for di in [-1, 0, 1]:
                for dj in [-1, 0, 1]:
                    if di == 0 and dj == 0: continue
                    ni, nj = i + di, j + dj
                    if 0 <= ni < size and 0 <= nj < size and grid[ni][nj] in ROAD_TILES:
                        has_road = True
                        break
                if has_road:
                    break

            if has_road:
                if tile in (TILE_COM_LOW, TILE_COM_HIGH):
                    roadside_com_cells.append((i, j))
                elif tile in (TILE_RES_LOW, TILE_RES_HIGH):
                    residential_cells.append((i + grid_min, j + grid_min))

    # 如果商业区格子不足，降级将部分临路住宅格子当作商家
    if len(roadside_com_cells) < merchant_count:
        for i in range(size):
            for j in range(size):
                if grid[i][j] not in ROAD_TILES and (i, j) not in roadside_com_cells:
                    for di in [-1, 0, 1]:
                        for dj in [-1, 0, 1]:
                            if di == 0 and dj == 0: continue
                            ni, nj = i + di, j + dj
                            if 0 <= ni < size and 0 <= nj < size and grid[ni][nj] in ROAD_TILES:
                                roadside_com_cells.append((i, j))
                                break

    selected_com = random.sample(roadside_com_cells, min(merchant_count, len(roadside_com_cells)))
    merchants = []
    for idx, (cx, cy) in enumerate(selected_com):
        merchants.append({
            "id": f"merchant-{idx + 1}",
            "x": cx + grid_min,
            "y": cy + grid_min
        })

    return merchants, residential_cells

# ================= 地图 CRUD 接口 =================
@app.get("/api/maps")
def list_maps():
    """获取所有已保存地图列表"""
    return map_storage.list_maps()

@app.get("/api/maps/{map_id}")
def get_map(map_id: str):
    """获取指定地图详情"""
    m = map_storage.get_map(map_id)
    if not m:
        raise HTTPException(status_code=404, detail="Map not found")
    return m

@app.post("/api/maps")
def create_map(req: MapSaveRequest):
    """新建保存地图"""
    return map_storage.save_map(name=req.name, grid=req.grid)

@app.put("/api/maps/{map_id}")
def update_map(map_id: str, req: MapSaveRequest):
    """更新保存已有地图"""
    return map_storage.save_map(name=req.name, grid=req.grid, map_id=map_id)

@app.delete("/api/maps/{map_id}")
def delete_map(map_id: str):
    """删除地图"""
    success = map_storage.delete_map(map_id)
    if not success:
        raise HTTPException(status_code=404, detail="Map not found")
    return {"status": "deleted", "id": map_id}

# ================= 模拟生命周期接口 =================
@app.post("/api/simulation/start")
def start_simulation(req: StartRequest):
    global current_map_data, current_engine
    
    # 停止旧的引擎（如果有）
    if current_engine and current_engine.running:
        current_engine.stop()
        
    print(f"[Main] 收到启动请求: 商家数 {req.merchantCount}, 骑手数 {req.riderCount}, 地图: {req.mapId}")
    
    if req.mapId and req.mapId != "random":
        saved_map = map_storage.get_map(req.mapId)
        if saved_map:
            merchants, res_cells = extract_merchants_and_res_cells(saved_map["grid"], req.merchantCount, saved_map.get("min", -100))
            current_map_data = {
                "min": saved_map.get("min", -100),
                "max": saved_map.get("max", 100),
                "grid": saved_map["grid"],
                "merchants": merchants,
                "residentialCells": res_cells,
                "mapName": saved_map.get("name", "自定义地图")
            }
        else:
            print(f"[Main] 指定地图 {req.mapId} 不存在，降级为随机生成")
            map_gen = MapGenerator(merchant_count=req.merchantCount)
            map_gen.generate()
            current_map_data = map_gen.get_map_data()
    else:
        # 随机生成地图
        map_gen = MapGenerator(merchant_count=req.merchantCount)
        map_gen.generate()
        current_map_data = map_gen.get_map_data()
    
    # 初始化并启动引擎
    current_engine = SimulationEngine(current_map_data, rider_count=req.riderCount)
    current_engine.start()
    
    return current_map_data

@app.post("/api/simulation/pause")
def pause_simulation():
    global current_engine
    if current_engine:
        current_engine.pause()
    print("[Main] 收到暂停请求")
    return {"status": "paused"}

@app.post("/api/simulation/resume")
def resume_simulation():
    global current_engine
    if current_engine:
        current_engine.resume()
    print("[Main] 收到恢复请求")
    return {"status": "running"}

@app.post("/api/simulation/stop")
def stop_simulation():
    global current_map_data, current_engine
    if current_engine:
        current_engine.stop()
        current_engine = None
    current_map_data = None
    print("[Main] 收到停止请求: 模拟引擎已停止并重置状态")
    return {"status": "stopped"}

@app.get("/api/map/init")
def get_map_init():
    if current_map_data is None:
        return {"error": "Simulation not started"}
    return current_map_data

@app.get("/api/status")
def get_status():
    if not current_engine:
        return {"status": "stopped"}
    return {"status": "running", "riders_count": len(current_engine.riders)}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8081)
