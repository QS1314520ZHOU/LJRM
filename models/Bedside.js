const mongoose = require('mongoose');
const { smartCareConn } = require('../config/db');

const BedsideSchema = new mongoose.Schema({
  pid: String,
  code: String,
  time: Date,
  strVal: String,
  valid: Boolean,
  editTime: Date,
}, { collection: 'bedside', strict: false });

module.exports = smartCareConn.model('Bedside', BedsideSchema);
