-- 1.1.3.8 voice command mode preference.
-- Default FALSE so existing rows opt out unless the user toggles it on in the
-- accessibility settings.
ALTER TABLE registered_user
    ADD COLUMN IF NOT EXISTS voice_commands_enabled BOOLEAN NOT NULL DEFAULT FALSE;
