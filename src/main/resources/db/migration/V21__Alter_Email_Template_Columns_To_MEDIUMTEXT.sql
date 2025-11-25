-- Migration: Alter email_template content and json_design columns from TEXT to MEDIUMTEXT
-- Reason: Email templates with rich HTML and Unlayer JSON designs can exceed TEXT limit (65KB)
-- MEDIUMTEXT supports up to 16MB which is sufficient for complex email templates

ALTER TABLE email_template
MODIFY COLUMN content MEDIUMTEXT,
MODIFY COLUMN json_design MEDIUMTEXT;
