package com.meileme.resort.controller;

import com.meileme.resort.service.ConfigRegistry;
import com.meileme.resort.service.GameEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理员配置 REST 接口（标准 HTTP API，便于浏览器 F12 Network 调试、抓包并支持局内热重载）。
 */
@RestController
@RequestMapping("/api/admin/config")
@CrossOrigin(origins = "*")
public class AdminConfigController {

    private static final Logger log = LoggerFactory.getLogger(AdminConfigController.class);

    private final ConfigRegistry registry;
    private final GameEngine gameEngine;

    public AdminConfigController(ConfigRegistry registry, GameEngine gameEngine) {
        this.registry = registry;
        this.gameEngine = gameEngine;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("config", registry.getRawConfigMap());
        res.put("isCustom", registry.isCustomConfig());
        return ResponseEntity.ok(res);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> saveConfig(@RequestBody Map<String, Object> payload) {
        Map<String, Object> res = new LinkedHashMap<>();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> configMap = payload.containsKey("config") 
                    ? (Map<String, Object>) payload.get("config") 
                    : payload;

            registry.saveAdminConfig(configMap);
            File f = registry.resolveAdminConfigFile();
            res.put("success", true);
            res.put("message", "配置已成功保存并热更新完毕");
            res.put("filePath", f.getAbsolutePath());
            res.put("isCustom", true);

            // 广播通知在线 WebSocket 客户端全量配置已更新
            gameEngine.sendConfig();

            log.info("【HTTP REST】管理员配置已通过 POST /api/admin/config 保存成功: {}", f.getAbsolutePath());
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            log.error("【HTTP REST】保存管理员配置失败", e);
            res.put("success", false);
            res.put("message", "保存失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(res);
        }
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetConfig() {
        Map<String, Object> res = new LinkedHashMap<>();
        try {
            registry.resetToDefault();
            res.put("success", true);
            res.put("message", "已重置为出厂默认配置");
            res.put("isCustom", false);

            gameEngine.sendConfig();

            log.info("【HTTP REST】管理员配置已通过 POST /api/admin/config/reset 恢复默认");
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            log.error("【HTTP REST】重置管理员配置失败", e);
            res.put("success", false);
            res.put("message", "重置失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(res);
        }
    }
}
