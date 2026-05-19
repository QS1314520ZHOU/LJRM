const express = require('express');
const cors = require('cors');
const path = require('path');
process.env.TZ = process.env.TZ || 'Asia/Shanghai';
require('dotenv').config();
const { getDbStatus } = require('./config/db');

const statsRoutes = require('./routes/statsRoutes');

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());
app.use('/vendor', express.static(path.join(__dirname, 'node_modules', 'xlsx', 'dist')));
app.use(express.static(path.join(__dirname, 'public')));

app.get('/api/health', (req, res) => {
  res.json({ code: 200, msg: 'ok', data: { uptime: process.uptime(), db: getDbStatus() } });
});

app.use('/api/stats', statsRoutes);

app.get('/', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

app.listen(PORT, () => {
  console.log(`🚀 服务已启动: http://localhost:${PORT}`);
});

