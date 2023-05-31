DO $$ begin

create type social_network as enum (
  'LinkedIn',
  'Telegram',
  'Instagram',
  'Twitter',
  'Facebook',
  'Youtube'
);

create type pricing as enum (
  'Low', 'Mid', 'High',
  'Freemium', 'Onetime', 'Recurring', 'PriceOnAsking', 'PayAsYouGo'
);


CREATE TABLE IF NOT EXISTS technologies (
  id varchar NOT NULL,
  description varchar NULL,
  website varchar NULL,
  saas bool NULL,
  oss bool NULL,
  CONSTRAINT technologies_pk PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS results (
  id uuid NOT NULL,
  "domain" varchar NOT NULL,
  "date" timestamptz NOT NULL,
  duration interval NOT NULL,
  success bool NOT NULL,
  total_children int4 NOT NULL,
  fetched_children int4 NOT NULL,
  CONSTRAINT results_pk PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS results_date_idx ON results USING btree (date);
CREATE INDEX IF NOT EXISTS results_status_idx ON results USING btree (success);

CREATE TABLE IF NOT EXISTS extracted_technologies (
  technology_id varchar NOT NULL,
  result_id uuid NOT NULL,
  CONSTRAINT extracted_technologies_fk FOREIGN KEY (technology_id) REFERENCES technologies(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT extracted_technologies_fk_1 FOREIGN KEY (result_id) REFERENCES results(id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE TABLE IF NOT EXISTS emails (
  result_id uuid NOT NULL,
  value varchar NOT NULL,
  CONSTRAINT emails_fk FOREIGN KEY (result_id) REFERENCES results(id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE TABLE IF NOT EXISTS socials (
  result_id uuid NOT NULL,
  "type" social_network NOT NULL,
  value varchar NOT NULL,
  CONSTRAINT socials_fk FOREIGN KEY (result_id) REFERENCES results(id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE TABLE IF NOT EXISTS phones (
  result_id uuid NOT NULL,
  value varchar NOT NULL,
  CONSTRAINT phones_fk FOREIGN KEY (result_id) REFERENCES results(id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE TABLE IF NOT EXISTS technology_pricing (
  technology_id varchar NOT NULL,
  value pricing NOT NULL,
  CONSTRAINT technology_pricing_fk FOREIGN KEY (technology_id) REFERENCES technologies(id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

EXCEPTION
    WHEN duplicate_object THEN null;
END $$;
