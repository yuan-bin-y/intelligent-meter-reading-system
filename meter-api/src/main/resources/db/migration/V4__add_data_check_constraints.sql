ALTER TABLE sys_user
    ADD CONSTRAINT chk_sys_user_status
        CHECK (status IN (0, 1));

ALTER TABLE sys_role
    ADD CONSTRAINT chk_sys_role_status
        CHECK (status IN (0, 1));

ALTER TABLE meter
    ADD CONSTRAINT chk_meter_type
        CHECK (meter_type IN ('WATER', 'ELECTRIC', 'GAS')),
    ADD CONSTRAINT chk_meter_display_type
        CHECK (display_type IN ('LCD', 'MECHANICAL_ROLLER')),
    ADD CONSTRAINT chk_meter_status
        CHECK (status IN (0, 1, 2, 3)),
    ADD CONSTRAINT chk_meter_integer_digits
        CHECK (integer_digits BETWEEN 1 AND 12),
    ADD CONSTRAINT chk_meter_decimal_digits
        CHECK (decimal_digits BETWEEN 0 AND 3),
    ADD CONSTRAINT chk_meter_initial_reading
        CHECK (initial_reading >= 0),
    ADD CONSTRAINT chk_meter_version
        CHECK (version >= 0),
    ADD CONSTRAINT chk_meter_deleted
        CHECK (deleted IN (0, 1));
