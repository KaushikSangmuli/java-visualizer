-- V2 created Spring Session tables for an early server-side login design. Login now uses
-- stateless JWT cookies, so nothing reads or writes these tables.
DROP TABLE IF EXISTS SPRING_SESSION_ATTRIBUTES;
DROP TABLE IF EXISTS SPRING_SESSION;
