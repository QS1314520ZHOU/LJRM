const mongoose = require('mongoose');
const { smartCareConn } = require('../config/db');

const ConfigDrugMethodSchema = new mongoose.Schema({
  code: String,
  name: String,
  isOnce: Boolean,
  group: String,
  inChannel: String,
  valid: Boolean,
  enName: String,
}, { collection: 'configDrugMethod', strict: false });

module.exports = smartCareConn.model('ConfigDrugMethod', ConfigDrugMethodSchema);
