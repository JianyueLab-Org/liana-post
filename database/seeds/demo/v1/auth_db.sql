INSERT INTO user (username,password_hash,display_name,facility_code,status) VALUES
('admin001','${demo_password_hash}','System Admin','LN-NAM-HQ',1),
('clerk001','${demo_password_hash}','Namoa Clerk','LN-NM-01',1),
('manager001','${demo_password_hash}','Namoa Manager','LN-NM-01',1),
('sorter001','${demo_password_hash}','National Sorter','LN-NAM-HQ',1),
('oro-clerk','${demo_password_hash}','Oro Clerk','LN-OR-01',1),
('oro-sorter','${demo_password_hash}','Oro Sorter','LN-OR-RC',1),
('air-sorter','${demo_password_hash}','Air Cargo Sorter','LN-NAM-AIR',1),
('far-reef-clerk','${demo_password_hash}','Far Reef Clerk','LN-SE-02',1);

INSERT INTO user_role (user_id,role_id) SELECT u.id,r.id FROM user u JOIN role r ON r.code='ADMIN' WHERE u.username='admin001';
INSERT INTO user_role (user_id,role_id) SELECT u.id,r.id FROM user u JOIN role r ON r.code='CLERK' WHERE u.username='clerk001';
INSERT INTO user_role (user_id,role_id) SELECT u.id,r.id FROM user u JOIN role r ON r.code='MANAGER' WHERE u.username='manager001';
INSERT INTO user_role (user_id,role_id) SELECT u.id,r.id FROM user u JOIN role r ON r.code='SORTER' WHERE u.username='sorter001';
INSERT INTO user_role (user_id,role_id) SELECT u.id,r.id FROM user u JOIN role r ON r.code='CLERK' WHERE u.username='oro-clerk';
INSERT INTO user_role (user_id,role_id) SELECT u.id,r.id FROM user u JOIN role r ON r.code='SORTER' WHERE u.username='oro-sorter';
INSERT INTO user_role (user_id,role_id) SELECT u.id,r.id FROM user u JOIN role r ON r.code='SORTER' WHERE u.username='air-sorter';
INSERT INTO user_role (user_id,role_id) SELECT u.id,r.id FROM user u JOIN role r ON r.code='CLERK' WHERE u.username='far-reef-clerk';
