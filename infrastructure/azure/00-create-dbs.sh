#!/bin/sh
# Runs once on first Postgres start. Add one block per project.
set -e
psql -v ON_ERROR_STOP=1 -U postgres <<SQL
CREATE DATABASE vectorlink;
CREATE DATABASE archsim;
SQL
psql -v ON_ERROR_STOP=1 -U postgres -d vectorlink -f /docker-entrypoint-initdb.d/01-schema.sql
