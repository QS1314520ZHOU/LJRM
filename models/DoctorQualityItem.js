const mongoose = require('mongoose');
const { smartCareConn } = require('../config/db');

const DoctorQualityItemSchema = new mongoose.Schema({}, {
  collection: 'doctorQualityItem',
  strict: false,
});

module.exports = smartCareConn.model('DoctorQualityItem', DoctorQualityItemSchema);
