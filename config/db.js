const mongoose = require('mongoose');
require('dotenv').config();

const CONNECTION_OPTIONS = {
  serverSelectionTimeoutMS: Number(process.env.MONGO_TIMEOUT_MS || 10000),
};

function createMongoConnection(name, uri) {
  if (!uri) {
    console.warn(`⚠️ ${name} 数据库连接地址未配置`);
    return mongoose.createConnection();
  }

  const conn = mongoose.createConnection(uri, CONNECTION_OPTIONS);

  conn.on('connected', () => {
    console.log(`✅ ${name} 数据库连接成功`);
  });

  conn.on('error', (err) => {
    console.error(`❌ ${name} 数据库连接失败`, err.message);
  });

  conn.on('disconnected', () => {
    console.warn(`⚠️ ${name} 数据库连接已断开`);
  });

  return conn;
}

function getConnectionState(conn) {
  const states = ['disconnected', 'connected', 'connecting', 'disconnecting'];
  return states[conn.readyState] || 'unknown';
}

const dataCenterConn = createMongoConnection('DataCenter', process.env.MONGO_DATACENTER_URI);
const smartCareConn = createMongoConnection('SmartCare', process.env.MONGO_SMARTCARE_URI);

module.exports = {
  dataCenterConn,
  smartCareConn,
  getDbStatus() {
    return {
      dataCenter: getConnectionState(dataCenterConn),
      smartCare: getConnectionState(smartCareConn),
    };
  },
};
