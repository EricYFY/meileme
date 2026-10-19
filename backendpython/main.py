from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional, List
from map_generator import MapGenerator, analyze_map
from engine import ResortEngine
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
    mapId: Optional[str] = None

class MapSaveRequest(BaseModel):
    name: str
    grid: List[List[int]]

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
def start_simulation(req: Optional[StartRequest] = None):
    global current_map_data, current_engine

    # 停止旧的引擎（如果有）
    if current_engine and current_engine.running:
        current_engine.stop()

    map_id = req.mapId if req else None
    print(f"[Main] 收到启动请求: 地图 {map_id}")

    saved_map = None
    if map_id and map_id != "random":
        saved_map = map_storage.get_map(map_id)

    if saved_map:
        buildable_cells, plots = analyze_map(saved_map["grid"], saved_map.get("min", -100))
        current_map_data = {
            "min": saved_map.get("min", -100),
            "max": saved_map.get("max", 100),
            "grid": saved_map["grid"],
            "buildableCells": buildable_cells,
            "plots": plots,
            "mapName": saved_map.get("name", "自定义地图")
        }
    else:
        if map_id and map_id != "random":
            print(f"[Main] 指定地图 {map_id} 不存在，降级为随机生成")
        map_gen = MapGenerator()
        map_gen.generate()
        current_map_data = map_gen.get_map_data()

    # 初始化并启动可视化引擎
    current_engine = ResortEngine(current_map_data)
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
    print("[Main] 收到停止请求: 可视化引擎已停止并重置状态")
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
    return {
        "status": "running",
        "guests_count": len(current_engine.guests),
        "staff_count": len(current_engine.staff)
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8081)
