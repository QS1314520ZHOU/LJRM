const mongoose = require('mongoose');
const { smartCareConn } = require('../config/db');

const DoctorQCSchema = new mongoose.Schema({}, {
  collection: 'doctorQC',
  strict: false,
});

module.exports = smartCareConn.model('DoctorQC', DoctorQCSchema);
