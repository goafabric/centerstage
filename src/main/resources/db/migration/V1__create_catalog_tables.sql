CREATE TABLE component_eo (
    id          VARCHAR(255) NOT NULL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    kind        VARCHAR(64)  NOT NULL,
    type        VARCHAR(128),
    lifecycle   VARCHAR(128),
    owner       VARCHAR(255),
    description TEXT,
    tags        TEXT,
    links       TEXT,
    annotations TEXT,
    provides_apis TEXT,
    depends_on    TEXT,
    dependency_of TEXT,
    source_path    VARCHAR(1024),
    definition_url TEXT,
    search_text    TEXT
);

CREATE TABLE adr_eo (
    id              VARCHAR(255) NOT NULL PRIMARY KEY,
    component_name  VARCHAR(255) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    content         TEXT,
    search_text     TEXT
);

CREATE TABLE doc_eo (
    id              VARCHAR(255) NOT NULL PRIMARY KEY,
    component_name  VARCHAR(255) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    content         TEXT,
    search_text     TEXT
);
