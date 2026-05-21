const mongoose = require('mongoose');
const { smartCareConn } = require('../config/db');

const ScoreSchema = new mongoose.Schema({
  pid: String,
  time: Date,
  scoreType: String,
  total: Number,
  valid: Boolean,
  apacheII: mongoose.Schema.Types.Mixed,
}, { collection: 'score', strict: false });

module.exports = smartCareConn.model('Score', ScoreSchema);
