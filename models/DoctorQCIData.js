const mongoose = require('mongoose');
const { smartCareConn } = require('../config/db');

const DoctorQCIDataSchema = new mongoose.Schema({}, {
  collection: 'doctorQCIData',
  strict: false,
});

module.exports = smartCareConn.model('DoctorQCIData', DoctorQCIDataSchema);
