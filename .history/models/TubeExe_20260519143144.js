const mongoose = require('mongoose');
const { smartCareConn } = require('../config/db');

const TubeExeSchema = new mongoose.Schema({
  pid: String,
  type: String,
  startTime: Date,
  endTime: Date,
  valid: Boolean,
  replace: Boolean,
  unPlannedEndTube: Boolean,
}, { collection: 'tubeExe', strict: false });

module.exports = smartCareConn.model('TubeExe', TubeExeSchema);
