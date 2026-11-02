-- Reference data for a NEW (e.g. cloud) database: programmes and lab groups only. No students.
-- Run after schema.sql:  npm run db:seed-reference
-- INSERT IGNORE: safe to run twice; existing rows are left alone.

INSERT IGNORE INTO programmes (programme_code, programme_name) VALUES
  ('CS', 'Computer Science'),
  ('IT', 'Information Technology'),
  ('DS', 'Data Science'),
  ('ICTED', 'ICT with Education'),
  ('CIV', 'Civil Engineering'),
  ('MEC', 'Mechanical Engineering'),
  ('EEE', 'Electrical and Electronics Engineering'),
  ('AGE', 'Agricultural Engineering'),
  ('WTE', 'Water Engineering'),
  ('IND', 'Industrial Engineering'),
  ('GEO', 'Geomatics Engineering'),
  ('MST', 'Mathematics and Statistics'),
  ('STA', 'Statistics'),
  ('PHY', 'Physics'),
  ('PHM', 'Physics and Mathematics'),
  ('CHM', 'Chemistry'),
  ('BIO', 'Biological Sciences'),
  ('BCH', 'Bio-Chemistry'),
  ('LAB', 'Laboratory Technology'),
  ('NRM', 'Natural Resources Management'),
  ('MBC', 'Medicine and Surgery (MBChB)'),
  ('BMS', 'Biomedical Sciences'),
  ('PHA', 'Pharmacy');

-- Capacities as on the lecturer's list (the brief's maximum is 15; see db:check-capacity).
INSERT IGNORE INTO lab_groups (group_code, capacity) VALUES
  ('G01', 16),
  ('G02', 15),
  ('G03', 15),
  ('G04', 16),
  ('G05', 15),
  ('G06', 15),
  ('G07', 15),
  ('G08', 15),
  ('G09', 16),
  ('G10', 16),
  ('G11', 16),
  ('G12', 15),
  ('G13', 15),
  ('G14', 15);
