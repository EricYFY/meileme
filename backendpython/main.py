from fastapi import FastAPI
from pydantic import BaseModel
from map_generator import MapGenerator
from engine import SimulationEngine

app = FastAPI()

# 全局变量保存当前状态
current_map_data = None
current_engine = None

class StartRequest(BaseModel):
    merchantCount: int
    riderCount: int

@app.post("/api/simulation/start")
def start_simulation(req: StartRequest):
    global current_map_data, current_engine
    
    # 停止旧的引擎（如果有）
    if current_engine and current_engine.running:
        current_engine.stop()
        
    print(f"[Main] 收到启动请求: 商家数 {req.merchantCount}, 骑手数 {req.riderCount}")
    
    # 初始化地图
    map_gen = MapGenerator(merchant_count=req.merchantCount)
    map_gen.generate()
    current_map_data = map_gen.get_map_data()
    
    # 初始化并启动引擎
    current_engine = SimulationEngine(current_map_data, rider_count=req.riderCount)
    current_engine.start()
    
    return current_map_data

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
    # 运行在 8081 端口，避免与 Java 8080 冲突
    uvicorn.run(app, host="0.0.0.0", port=8081)
