DO $$ begin

create type fetch_result as enum (
  'Success',
  'Timeout',
  'Failure',
  'BadStatus',
  'BadContent'
);

create type social_network as enum (
  'Linkedin',
  'LinkedinCompany',
  'Telegram',
  'Instagram',
  'Twitter',
  'Facebook',
  'Youtube',
  'Github',
  'Gitlab',
  'Pinterest',
  'Medium',
  'Crunchbase',
  'Twitch',
  'TikTok'
);

create type pricing as enum (
  'Low', 'Mid', 'High',
  'Freemium', 'Onetime', 'Recurring', 'PriceOnAsking', 'PayAsYouGo'
);

EXCEPTION
    WHEN duplicate_object THEN null;
END $$;
