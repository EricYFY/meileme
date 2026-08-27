from fastapi import FastAPI
from map_generator import MapGenerator
from engine import SimulationEngine

app = FastAPI()

# 初始化地图与引擎
map_gen = MapGenerator()
map_gen.generate()
map_data = map_gen.get_map_data()

engine = SimulationEngine(map_data)
engine.start()

@app.get("/api/map/init")
def get_map_init():
    """
    提供给 Java 或 前端 获取完整地图数据的接口
    """
    return map_data

@app.get("/api/status")
def get_status():
    return {"status": "running", "riders_count": len(engine.riders)}

if __name__ == "__main__":
    import uvicorn
    # 运行在 8081 端口，避免与 Java 8080 冲突
    uvicorn.run(app, host="0.0.0.0", port=8081)
