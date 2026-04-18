CREATE SCHEMA IF NOT EXISTS user_schema;

CREATE TABLE user_schema.user_roles (
    id INT PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL
);

CREATE TABLE user_schema.user_statuses (
    id INT PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL
);

CREATE TABLE user_schema.genders (
    id INT PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL
);


CREATE TABLE user_schema.users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role_id INT REFERENCES user_schema.user_roles(id),
    status_id INT REFERENCES user_schema.user_statuses(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version INT DEFAULT 0 NOT NULL
);

-- UNIQUE constraint explicitly maintains a b-tree index
-- CREATE INDEX idx_users_email ON user_schema.users USING btree (email); 

CREATE TABLE user_schema.profiles (
    user_id UUID PRIMARY KEY REFERENCES user_schema.users(id),
    full_name VARCHAR(255) NOT NULL,
    birth_date DATE,
    gender_id INT REFERENCES user_schema.genders(id),
    username VARCHAR(100) UNIQUE NOT NULL,
    avatar_url TEXT,
    psych_profile JSONB,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE user_schema.user_outbox_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
