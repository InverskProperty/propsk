-- =====================================================
-- V18: Add Property Leads & Tenant Lifecycle System
-- =====================================================
-- This migration adds support for:
-- 1. Property vacancy tracking (notice given, advertising)
-- 2. Property rental leads (extending existing trigger_lead table)
-- 3. Viewing scheduling
-- 4. Automated task/reminder tracking
-- 5. Lead to tenant conversion workflow
-- =====================================================

-- =====================================================
-- PART 1: Extend Properties Table for Vacancy Tracking
-- =====================================================

ALTER TABLE properties
ADD COLUMN occupancy_status VARCHAR(20) DEFAULT 'OCCUPIED' COMMENT 'Current occupancy status: OCCUPIED, NOTICE_GIVEN, ADVERTISING, AVAILABLE, MAINTENANCE, OFF_MARKET',
ADD COLUMN notice_given_date DATE COMMENT 'Date when tenant gave notice',
ADD COLUMN expected_vacancy_date DATE COMMENT 'Expected date property will become vacant',
ADD COLUMN advertising_start_date DATE COMMENT 'Date when property advertising began',
ADD COLUMN available_from_date DATE COMMENT 'Date property is available for new tenancy',
ADD COLUMN last_occupancy_change DATETIME COMMENT 'Timestamp of last occupancy status change';

-- Add index for querying properties by occupancy status
CREATE INDEX idx_properties_occupancy_status ON properties(occupancy_status);
CREATE INDEX idx_properties_expected_vacancy ON properties(expected_vacancy_date);
CREATE INDEX idx_properties_available_from ON properties(available_from_date);

-- =====================================================
-- PART 2: Extend trigger_lead Table for Property Leads
-- =====================================================

ALTER TABLE trigger_lead
ADD COLUMN property_id BIGINT COMMENT 'Property this lead is enquiring about',
ADD COLUMN lead_type VARCHAR(20) DEFAULT 'BUSINESS' COMMENT 'Type of lead: BUSINESS or PROPERTY_RENTAL',
ADD COLUMN desired_move_in_date DATE COMMENT 'When the prospective tenant wants to move in',
ADD COLUMN budget_min DECIMAL(10,2) COMMENT 'Minimum budget (monthly rent)',
ADD COLUMN budget_max DECIMAL(10,2) COMMENT 'Maximum budget (monthly rent)',
ADD COLUMN number_of_occupants INT COMMENT 'Number of people who will occupy the property',
ADD COLUMN has_pets BOOLEAN DEFAULT FALSE COMMENT 'Whether tenant has pets',
ADD COLUMN has_guarantor BOOLEAN DEFAULT FALSE COMMENT 'Whether tenant has a guarantor',
ADD COLUMN employment_status VARCHAR(50) COMMENT 'Employment status: EMPLOYED, SELF_EMPLOYED, STUDENT, RETIRED, UNEMPLOYED',
ADD COLUMN lead_source VARCHAR(100) COMMENT 'Where the lead came from: website, openrent, spareroom, referral, walk-in, phone',
ADD COLUMN converted_at DATETIME COMMENT 'Timestamp when lead was converted to tenant',
ADD COLUMN converted_to_customer_id INT UNSIGNED COMMENT 'Customer ID created from this lead',
ADD CONSTRAINT fk_lead_property FOREIGN KEY (property_id) REFERENCES properties(id) ON DELETE SET NULL,
ADD CONSTRAINT fk_lead_converted_customer FOREIGN KEY (converted_to_customer_id) REFERENCES customers(customer_id) ON DELETE SET NULL;

-- Update status constraint to include property rental workflow stages
ALTER TABLE trigger_lead
DROP CHECK IF EXISTS trigger_lead_chk_1;

ALTER TABLE trigger_lead
ADD CONSTRAINT trigger_lead_chk_status CHECK (
    status IN (
        -- Original business lead statuses
        'meeting-to-schedule', 'scheduled', 'archived', 'success', 'assign-to-sales',
        -- New property rental lead statuses
        'enquiry', 'viewing-scheduled', 'viewing-completed', 'interested',
        'application-submitted', 'referencing', 'in-contracts', 'converted', 'lost'
    )
);

-- Add indexes for property lead queries
CREATE INDEX idx_lead_property ON trigger_lead(property_id);
CREATE INDEX idx_lead_type ON trigger_lead(lead_type);
CREATE INDEX idx_lead_status ON trigger_lead(status);
CREATE INDEX idx_lead_source ON trigger_lead(lead_source);
CREATE INDEX idx_lead_move_in_date ON trigger_lead(desired_move_in_date);

-- =====================================================
-- PART 3: Create property_viewings Table
-- =====================================================

CREATE TABLE property_viewings (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    lead_id INT UNSIGNED NOT NULL COMMENT 'Lead who is viewing the property',
    property_id BIGINT NOT NULL COMMENT 'Property being viewed',
    scheduled_datetime DATETIME NOT NULL COMMENT 'Date and time of viewing',
    duration_minutes INT DEFAULT 30 COMMENT 'Expected duration in minutes',
    viewing_type VARCHAR(50) DEFAULT 'IN_PERSON' COMMENT 'IN_PERSON or VIRTUAL',
    status VARCHAR(50) NOT NULL DEFAULT 'SCHEDULED' COMMENT 'SCHEDULED, CONFIRMED, COMPLETED, CANCELLED, NO_SHOW, RESCHEDULED',
    assigned_to_user_id BIGINT COMMENT 'Staff member conducting viewing',
    attendees TEXT COMMENT 'JSON array of attendee information',
    notes TEXT COMMENT 'Notes about the viewing (before)',
    feedback TEXT COMMENT 'Feedback from the viewing (after)',
    interested_level VARCHAR(20) COMMENT 'Interest level after viewing: VERY_INTERESTED, INTERESTED, NEUTRAL, NOT_INTERESTED',
    google_calendar_event_id VARCHAR(255) COMMENT 'Google Calendar event ID for sync',
    reminder_sent BOOLEAN DEFAULT FALSE COMMENT 'Whether reminder email was sent',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT COMMENT 'User who created this viewing',
    CONSTRAINT fk_viewing_lead FOREIGN KEY (lead_id) REFERENCES trigger_lead(lead_id) ON DELETE CASCADE,
    CONSTRAINT fk_viewing_property FOREIGN KEY (property_id) REFERENCES properties(id) ON DELETE CASCADE,
    CONSTRAINT fk_viewing_assigned_user FOREIGN KEY (assigned_to_user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_viewing_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) COMMENT 'Property viewing scheduling and tracking';

-- Add indexes for viewing queries
CREATE INDEX idx_viewing_scheduled_datetime ON property_viewings(scheduled_datetime);
CREATE INDEX idx_viewing_property ON property_viewings(property_id);
CREATE INDEX idx_viewing_lead ON property_viewings(lead_id);
CREATE INDEX idx_viewing_status ON property_viewings(status);
CREATE INDEX idx_viewing_assigned_user ON property_viewings(assigned_to_user_id);

-- =====================================================
-- PART 4: Create property_vacancy_tasks Table
-- =====================================================

CREATE TABLE property_vacancy_tasks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    property_id BIGINT NOT NULL COMMENT 'Property this task relates to',
    task_type VARCHAR(50) NOT NULL COMMENT 'Type: INSPECTION, PHOTOGRAPHY, REPAIRS, CLEANING, LISTING_CREATION, KEY_HANDOVER, etc',
    title VARCHAR(255) NOT NULL COMMENT 'Task title',
    description TEXT COMMENT 'Detailed task description',
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, IN_PROGRESS, COMPLETED, CANCELLED',
    priority VARCHAR(20) DEFAULT 'MEDIUM' COMMENT 'LOW, MEDIUM, HIGH, URGENT',
    due_date DATE COMMENT 'When task should be completed',
    completed_date DATETIME COMMENT 'When task was actually completed',
    assigned_to_user_id BIGINT COMMENT 'User responsible for this task',
    created_by_user_id BIGINT COMMENT 'User who created this task',
    auto_created BOOLEAN DEFAULT FALSE COMMENT 'Whether task was auto-created by system',
    trigger_event VARCHAR(50) COMMENT 'What triggered auto-creation: NOTICE_GIVEN, VACANCY_APPROACHING, etc',
    notes TEXT COMMENT 'Additional notes about the task',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_vacancy_task_property FOREIGN KEY (property_id) REFERENCES properties(id) ON DELETE CASCADE,
    CONSTRAINT fk_vacancy_task_assigned_user FOREIGN KEY (assigned_to_user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_vacancy_task_created_by FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE SET NULL
) COMMENT 'Tasks and reminders for property vacancy management';

-- Add indexes for task queries
CREATE INDEX idx_vacancy_task_property ON property_vacancy_tasks(property_id);
CREATE INDEX idx_vacancy_task_status ON property_vacancy_tasks(status);
CREATE INDEX idx_vacancy_task_due_date ON property_vacancy_tasks(due_date);
CREATE INDEX idx_vacancy_task_assigned_user ON property_vacancy_tasks(assigned_to_user_id);
CREATE INDEX idx_vacancy_task_type ON property_vacancy_tasks(task_type);

-- =====================================================
-- PART 5: Create lead_communications Table
-- =====================================================

CREATE TABLE lead_communications (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    lead_id INT UNSIGNED NOT NULL COMMENT 'Lead this communication relates to',
    communication_type VARCHAR(50) NOT NULL COMMENT 'EMAIL, SMS, CALL, WHATSAPP, IN_PERSON',
    direction VARCHAR(20) NOT NULL COMMENT 'INBOUND or OUTBOUND',
    subject VARCHAR(500) COMMENT 'Subject line (for emails)',
    message_content TEXT COMMENT 'Message body or call notes',
    sent_at DATETIME COMMENT 'When message was sent',
    delivered_at DATETIME COMMENT 'When message was delivered',
    opened_at DATETIME COMMENT 'When email was opened (if tracked)',
    clicked_at DATETIME COMMENT 'When link in email was clicked',
    replied_at DATETIME COMMENT 'When recipient replied',
    status VARCHAR(50) COMMENT 'SENT, DELIVERED, FAILED, BOUNCED, OPENED, REPLIED',
    email_template_id INT COMMENT 'Template used (if automated)',
    gmail_message_id VARCHAR(255) COMMENT 'Gmail message ID for sync',
    created_by_user_id BIGINT COMMENT 'User who initiated communication',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_comm_lead FOREIGN KEY (lead_id) REFERENCES trigger_lead(lead_id) ON DELETE CASCADE,
    CONSTRAINT fk_comm_template FOREIGN KEY (email_template_id) REFERENCES email_template(template_id) ON DELETE SET NULL,
    CONSTRAINT fk_comm_created_by FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE SET NULL
) COMMENT 'Track all communications with property leads';

-- Add indexes for communication queries
CREATE INDEX idx_comm_lead ON lead_communications(lead_id);
CREATE INDEX idx_comm_sent_at ON lead_communications(sent_at);
CREATE INDEX idx_comm_type ON lead_communications(communication_type);
CREATE INDEX idx_comm_direction ON lead_communications(direction);

-- =====================================================
-- PART 6: Update Existing Data (Backwards Compatibility)
-- =====================================================

-- Set default occupancy status based on current lease status
-- If property has an active rent invoice, mark as OCCUPIED
UPDATE properties p
SET p.occupancy_status =
    CASE
        WHEN EXISTS (
            SELECT 1 FROM invoices i
            WHERE i.property_id = p.id
            AND i.invoice_type = 'Rent'
            AND i.sync_status = 'active'
        ) THEN 'OCCUPIED'
        ELSE 'AVAILABLE'
    END
WHERE p.occupancy_status IS NULL OR p.occupancy_status = 'OCCUPIED';

-- Set lead_type for all existing leads (default to BUSINESS)
UPDATE trigger_lead
SET lead_type = 'BUSINESS'
WHERE lead_type IS NULL;

-- =====================================================
-- PART 7: Create Views for Reporting
-- =====================================================

-- View: Properties requiring attention (notice given, upcoming vacancy)
CREATE OR REPLACE VIEW v_properties_requiring_attention AS
SELECT
    p.id,
    p.address,
    p.addressLine1,
    p.city,
    p.postcode,
    p.occupancy_status,
    p.notice_given_date,
    p.expected_vacancy_date,
    p.advertising_start_date,
    DATEDIFF(p.expected_vacancy_date, CURDATE()) AS days_until_vacant,
    COUNT(DISTINCT l.lead_id) AS enquiry_count,
    COUNT(DISTINCT v.id) AS viewing_count,
    COUNT(DISTINCT t.id) AS pending_task_count
FROM properties p
LEFT JOIN trigger_lead l ON l.property_id = p.id AND l.lead_type = 'PROPERTY_RENTAL' AND l.status NOT IN ('converted', 'lost', 'archived')
LEFT JOIN property_viewings v ON v.property_id = p.id AND v.status IN ('SCHEDULED', 'CONFIRMED')
LEFT JOIN property_vacancy_tasks t ON t.property_id = p.id AND t.status IN ('PENDING', 'IN_PROGRESS')
WHERE p.occupancy_status IN ('NOTICE_GIVEN', 'ADVERTISING', 'AVAILABLE')
GROUP BY p.id, p.address, p.addressLine1, p.city, p.postcode,
         p.occupancy_status, p.notice_given_date, p.expected_vacancy_date,
         p.advertising_start_date;

-- View: Lead conversion funnel metrics
CREATE OR REPLACE VIEW v_lead_conversion_funnel AS
SELECT
    COUNT(*) AS total_leads,
    SUM(CASE WHEN status = 'enquiry' THEN 1 ELSE 0 END) AS enquiries,
    SUM(CASE WHEN status IN ('viewing-scheduled', 'viewing-completed') THEN 1 ELSE 0 END) AS viewings,
    SUM(CASE WHEN status = 'interested' THEN 1 ELSE 0 END) AS interested,
    SUM(CASE WHEN status = 'application-submitted' THEN 1 ELSE 0 END) AS applications,
    SUM(CASE WHEN status = 'referencing' THEN 1 ELSE 0 END) AS referencing,
    SUM(CASE WHEN status = 'in-contracts' THEN 1 ELSE 0 END) AS in_contracts,
    SUM(CASE WHEN status = 'converted' THEN 1 ELSE 0 END) AS converted,
    SUM(CASE WHEN status = 'lost' THEN 1 ELSE 0 END) AS lost,
    ROUND(100.0 * SUM(CASE WHEN status = 'converted' THEN 1 ELSE 0 END) / NULLIF(COUNT(*), 0), 2) AS conversion_rate
FROM trigger_lead
WHERE lead_type = 'PROPERTY_RENTAL'
AND created_at >= DATE_SUB(CURDATE(), INTERVAL 90 DAY);

-- =====================================================
-- PART 8: Add Comments to Columns
-- =====================================================

-- Add helpful comments
ALTER TABLE properties
    MODIFY COLUMN occupancy_status VARCHAR(20) DEFAULT 'OCCUPIED'
    COMMENT 'OCCUPIED: Has tenants | NOTICE_GIVEN: Tenant given notice | ADVERTISING: Being marketed | AVAILABLE: Ready for new tenant | MAINTENANCE: Under repair | OFF_MARKET: Not available';

-- =====================================================
-- Migration Complete
-- =====================================================
