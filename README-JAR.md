# ICU Stats JAR 部署指南

## 环境要求

- **Java 11** 或更高版本
- **MongoDB** (两个数据库实例):
  - DataCenter
  - SmartCare

## 构建

```bash
# 需要 Maven 3.6+
mvn clean package -DskipTests

# 或使用构建脚本（包含测试）
chmod +x build-jar.sh
./build-jar.sh
```

构建完成后在 `target/` 目录生成可执行 JAR:
```
target/icu-stats-jar-1.0.0.jar
```

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `MONGO_DATACENTER_URI` | `mongodb://127.0.0.1:27017/DataCenter` | DataCenter MongoDB 连接地址 |
| `MONGO_SMARTCARE_URI` | `mongodb://127.0.0.1:27017/SmartCare` | SmartCare MongoDB 连接地址 |
| `SERVER_PORT` | `3000` | 服务端口 |
| `MONGO_TIMEOUT_MS` | `10000` | MongoDB 连接超时时间(毫秒) |
| `ENABLE_DEPT_FILTER` | `false` | 是否按科室字段过滤 |
| `TZ` | `Asia/Shanghai` | 时区设置 |

## 双 MongoDB 配置

系统同时连接两个 MongoDB 数据库:

1. **DataCenter**: 包含 VI_ICU_ZYYZ(医嘱视图)、VI_ICU_QUALITY(质量数据)、VI_ICU_EXAM(VI_ICU_EXAM_ITEM 等检查数据
2. **SmartCare**: 包含 patient、bedside、score、tubeExe、drugExe 等患者数据

## 运行方式

### 前台运行

```bash
export MONGO_DATACENTER_URI='mongodb://127.0.0.1:27017/DataCenter'
export MONGO_SMARTCARE_URI='mongodb://127.0.0.1:27017/SmartCare'
export SERVER_PORT=3000
export ENABLE_DEPT_FILTER=false
export TZ=Asia/Shanghai

java -Duser.timezone=Asia/Shanghai \
  -jar target/icu-stats-jar-1.0.0.jar
```

### nohup 后台运行

```bash
export MONGO_DATACENTER_URI='mongodb://127.0.0.1:27017/DataCenter'
export MONGO_SMARTCARE_URI='mongodb://127.0.0.1:27017/SmartCare'
export SERVER_PORT=3000
export ENABLE_DEPT_FILTER=false
export TZ=Asia/Shanghai

nohup java -Duser.timezone=Asia/Shanghai \
  -jar target/icu-stats-jar-1.0.0.jar \
  > icu-stats.log 2>&1 &
```

### systemd 服务示例

创建 `/etc/systemd/system/icu-stats.service`:

```ini
[Unit]
Description=ICU Stats Service
After=network.target mongodb.service

[Service]
Type=simple
User=icu-stats
WorkingDirectory=/opt/icu-stats
ExecStart=/usr/bin/java -Duser.timezone=Asia/Shanghai -jar /opt/icu-stats/icu-stats-jar-1.0.0.jar
Restart=always
RestartSec=10
Environment=MONGO_DATACENTER_URI=mongodb://127.0.0.1:27017/DataCenter
Environment=MONGO_SMARTCARE_URI=mongodb://127.0.0.1:27017/SmartCare
Environment=SERVER_PORT=3000
Environment=ENABLE_DEPT_FILTER=false
Environment=TZ=Asia/Shanghai

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable icu-stats
sudo systemctl start icu-stats
```

## 健康检查

```bash
curl http://localhost:3000/api/health
```

预期响应:
```json
{
  "code": 200,
  "msg": "ok",
  "data": {
    "uptime": 123.456,
    "db": {
      "dataCenter": "connected",
      "smartCare": "connected"
    }
  }
}
```

## 访问统计页面

浏览器打开:
```
http://localhost:3000/
```

## 常见错误排查

### 1. MongoDB 连接失败

```
错误: dataCenter 数据库连接失败
```

排查:
- 确认 MongoDB 服务已启动
- 检查连接字符串是否正确
- 检查防火墙是否允许 MongoDB 端口(默认 27017)
- 检查 MongoDB 是否启用了认证

### 2. 端口被占用

```
错误: Address already in use
```

排查:
```bash
# 查找占用端口的进程
netstat -tlnp | grep 3000
# 或修改 SERVER_PORT 环境变量
export SERVER_PORT=8080
```

### 3. 时区问题

确保设置:
```bash
export TZ=Asia/Shanghai
java -Duser.timezone=Asia/Shanghai -jar ...
```

### 4. 内存不足

```bash
java -Xms256m -Xmx512m -Duser.timezone=Asia/Shanghai -jar target/icu-stats-jar-1.0.0.jar
```

## OEL8/RHEL8 部署注意事项

1. 确保安装 Java 11:
```bash
sudo yum install java-11-openjdk java-11-openjdk-devel
sudo alternatives --config java  # 选择 Java 11
```

2. 如果使用 SELinux:
```bash
sudo semanage port -a -t http_port_t -p tcp 3000
```

3. 防火墙配置:
```bash
sudo firewall-cmd --permanent --add-port=3000/tcp
sudo firewall-cmd --reload
```

## API 接口

| 接口 | 方法 | 参数 |
|------|------|------|
| `/api/health` | GET | - |
| `/api/stats/indicators` | GET | - |
| `/api/stats/year` | GET | year, department |
| `/api/stats/range` | GET | startMonth, endMonth, department |
| `/api/stats/detail` | GET | indicatorKey, startMonth, endMonth, department |
| `/api/stats/quality` | GET | year/startMonth/endMonth, department |
| `/api/stats/quality/detail` | GET | indicatorKey, year/startMonth/endMonth, department, itemOrder |
| `/api/stats/nutrition/year` | GET | year, department |
| `/api/stats/nutrition/range` | GET | startMonth, endMonth, department |
| `/api/stats/nutrition/detail` | GET | indicatorKey, startMonth, endMonth, department |
| `/api/stats/nutrition/daily` | GET | startDate, endDate, department |
| `/api/stats/nutrition/daily/detail` | GET | date, department |
| `/api/stats/nutrition/daily/detail/range` | GET | startDate, endDate, department |

所有成功响应格式:
```json
{
  "code": 200,
  "msg": "success",
  "data": { ... }
}
```

错误响应格式:
```json
{
  "code": 400,
  "msg": "错误信息"
}
```
