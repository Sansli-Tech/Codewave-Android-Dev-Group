
require('dotenv').config();

const express = require('express');
const helmet = require('helmet');
const cors = require('cors');
const pool = require('./config/database');

const app = express();

app.use(helmet());
app.use(cors());
app.use(express.json({ limit: '20kb' }));

app.get('/', (req, res) => {
  res.json({
    message: 'ICT361 Student Registration API',
    status: 'running'
  });
});

app.get('/api/health', async (req, res) => {
  try {
    const [rows] = await pool.query('SELECT 1 AS connected');

    res.json({
      status: 'OK',
      database: rows[0].connected === 1 ? 'connected' : 'unknown'
    });
  } catch (error) {
    console.error('Health check failed:', error.message);
    res.status(503).json({
      status: 'ERROR',
      message: 'Database connection failed'
    });
  }
});

app.use('/api/auth', require('./routes/auth'));
app.use('/api/students', require('./routes/students'));
app.use('/api/groups', require('./routes/groups'));
app.use('/api/sync', require('./routes/sync'));
app.use('/api/me', require('./routes/me'));
app.use('/api/correction-requests', require('./routes/correctionRequests'));

app.use((req, res) => {
  res.status(404).json({
    error: 'NOT_FOUND',
    message: 'The requested endpoint does not exist.'
  });
});

const PORT = process.env.PORT || 5000;

app.listen(PORT, () => {
  console.log(`ICT361 API running on port ${PORT}`);
});

