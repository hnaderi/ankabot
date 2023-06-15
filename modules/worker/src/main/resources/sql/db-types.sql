DO $$ begin

create type fetch_result as enum (
  'Success',
  'Timeout',
  'Failure',
  'BadStatus'
);

create type social_network as enum (
  'Linkedin',
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

EXCEPTION
    WHEN duplicate_object THEN null;
END $$;
