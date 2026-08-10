package com.smartcare.icustats.controller;

import com.smartcare.icustats.dto.ApiResponse;
import com.smartcare.icustats.dto.HealthData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查控制器
 * 原Node.js文件: server.js - app.get('/api/health', ...)
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @Autowired
    private MongoTemplate dataCenterMongoTemplate;

    @Autowired
    private MongoTemplate smartCareMongoTemplate;

    private long startTime = System.currentTimeMillis();

    /**
     * GET /api/health
     * 返回 uptime 和两个数据库的连接状态
     * 原Node.js返回: { code: 200, msg: 'ok', data: { uptime, db: { dataCenter, smartCare } } }
     */
    @GetMapping("/health")
    public ApiResponse<HealthData> health() {
        double uptime = ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0;

        Map<String, String> dbStatus = new LinkedHashMap<>();
        dbStatus.put("dataCenter", pingDatabase(dataCenterMongoTemplate));
        dbStatus.put("smartCare", pingDatabase(smartCareMongoTemplate));

        return ApiResponse.ok("ok", new HealthData(uptime, dbStatus));
    }

    /**
     * 通过ping命令检测数据库连接状态
     * 对应原Node.js的 getConnectionState(conn) 函数
     */
    private String pingDatabase(MongoTemplate template) {
        try {
            template.executeCommand("{ ping: 1 }");
            return "connected";
        } catch (Exception e) {
            return "error";
        }
    }
}
