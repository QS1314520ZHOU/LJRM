const mongoose = require('mongoose');
const { smartCareConn } = require('../config/db');

const DoctorQualitySchema = new mongoose.Schema({}, {
  collection: 'doctorQuality',
  strict: false,
});

module.exports = smartCareConn.model('DoctorQuality', DoctorQualitySchema);
