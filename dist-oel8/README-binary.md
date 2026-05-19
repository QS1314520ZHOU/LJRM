# ICU Stats OEL8.2 二进制部署说明

## 产物内容

使用 `Dockerfile.oel8` 构建后会导出：

- `icu-stats-oel8-x64`：Oracle Linux 8.2 / RHEL 8 x86_64 可执行文件，已打入 Node.js 运行时和 npm 依赖。
- `.env.example`：运行配置示例。
- `README-binary.md`：本说明。

## 构建命令

请在有 Docker 的机器执行。该项目会在 Oracle Linux 8.2 容器内完成构建；不要在 Windows 上直接执行 `npm run build:oel8` 交叉打包。

推荐使用 Docker BuildKit 直接导出产物到 `release/`：

```bash
docker build -f Dockerfile.oel8 --target artifact --output type=local,dest=release .
```

构建完成后：

```bash
ls -l release
```

如果 Docker 版本不支持 `--output`，可以使用：

```bash
docker build -f Dockerfile.oel8 --target artifact -t icu-stats-oel8-artifact .
docker create --name icu-stats-artifact icu-stats-oel8-artifact
docker cp icu-stats-artifact:/ ./release
docker rm icu-stats-artifact
```

## OEL8.2 服务器运行

```bash
mkdir -p /opt/icu-stats
cd /opt/icu-stats
cp /path/to/release/icu-stats-oel8-x64 ./icu-stats
cp /path/to/release/.env.example ./.env
chmod +x ./icu-stats
vi .env
./icu-stats
```

访问：

```text
http://服务器IP:3000
```

## .env 示例

```env
# DataCenter 库：包含 VI_ICU_ZYYZ 等医嘱视图/集合
MONGO_DATACENTER_URI=mongodb://127.0.0.1:27017/DataCenter

# SmartCare 库：包含 patient、bedside 等集合
MONGO_SMARTCARE_URI=mongodb://127.0.0.1:27017/SmartCare

# 服务端口
PORT=3000

# 运行环境
NODE_ENV=production

# MongoDB 连接超时时间，单位毫秒
MONGO_TIMEOUT_MS=10000

# 是否按科室字段过滤；默认 false，避免源数据无科室字段时统计为 0
ENABLE_DEPT_FILTER=false
```

## 后台运行示例

```bash
nohup ./icu-stats > icu-stats.log 2>&1 &
```

## Windows 文件排除

Docker 构建上下文通过 `.dockerignore` 排除了 `node_modules/`、本地缓存、`.env`、`dist/`、`release/` 以及 `*.exe`、`*.dll`、`*.bat`、`*.cmd`、`*.ps1`、`Thumbs.db`、`Desktop.ini` 等 Windows 文件。
