CREATE TABLE app_user (
    google_id       VARCHAR PRIMARY KEY,
    email           VARCHAR NOT NULL,
    display_name    VARCHAR NOT NULL,
    picture_url     VARCHAR
);
