
require('dotenv').config();

const express = require('express');
const helmet = require('helmet');
const cors = require('cors');
const pool = require('./config/database');

const app = express();

// Hosted behind an HTTPS proxy (Render, Railway, nginx): trust it so req.secure is correct.
if (process.env.TRUST_PROXY === 'true') app.set('trust proxy', 1);

app.use(helmet());

// The brief requires HTTPS for the remote backend. With REQUIRE_HTTPS=true every plain-HTTP
// request is refused (except the health check the host uses). Leave it off on your own PC.
app.use((req, res, next) => {
  if (process.env.REQUIRE_HTTPS === 'true' && !req.secure && req.path !== '/api/health') {
    return res.status(426).json({ error: 'HTTPS_REQUIRED', message: 'Use HTTPS.' });
  }
  next();
});
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
const passwordReset = require('./routes/passwordReset');
app.use('/api/students', passwordReset.lecturerRouter);   // POST /api/students/:id/reset-code
app.use('/api/auth', passwordReset.publicRouter);         // forgot-password, reset-with-code, lecturer-reset-password
app.use('/api/account', passwordReset.accountRouter);     // GET/PUT /api/account/email (recovery e-mail)
const inboxRoutes = require('./routes/inbox');
app.use('/api/notifications', inboxRoutes.notificationsRouter);   // own feed, unread count, mark read
app.use('/api/devices', inboxRoutes.devicesRouter);               // phone push registration
app.use('/api/group-requests', inboxRoutes.groupRequestsRouter);  // LECTURER: approve / reject group requests
app.use('/api/announcements', inboxRoutes.announcementsRouter);   // LECTURER: send announcements
// Create the notification tables at start-up (never inside a request's transaction).
require('./notifications').ensureSchema().catch((e) => console.error('Notification tables:', e.message));

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

