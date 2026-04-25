create extension if not exists "pgcrypto";

create table if not exists teams (
  id serial primary key,
  code varchar(16) unique not null,
  name varchar(64) not null,
  landmark varchar(64) not null,
  primary_color varchar(16) not null
);

insert into teams (code, name, landmark, primary_color) values
  ('MUM', 'Mumbai',    'Gateway of India', '#F77F00'),
  ('HYD', 'Hyderabad', 'Charminar',        '#E63946'),
  ('DEL', 'Delhi',     'India Gate',       '#1D4ED8'),
  ('KOL', 'Kolkata',   'Howrah Bridge',    '#7C3AED'),
  ('BLR', 'Bangalore', 'Vidhana Soudha',   '#F59E0B'),
  ('CHE', 'Chennai',   'Meenakshi Temple', '#FB7185')
on conflict (code) do nothing;

create table if not exists users (
  id uuid primary key default gen_random_uuid(),
  username varchar(32) unique not null,
  password_hash varchar(255) not null,
  team_id integer references teams(id),
  total_runs integer not null default 0,
  matches_played integer not null default 0,
  matches_won integer not null default 0,
  matches_left_today integer not null default 3,
  last_reset_at timestamptz not null default now(),
  created_at timestamptz not null default now()
);

create index if not exists idx_users_username on users(username);

create table if not exists rooms (
  id uuid primary key default gen_random_uuid(),
  code varchar(5) unique not null,
  host_user_id uuid not null references users(id) on delete cascade,
  guest_user_id uuid references users(id) on delete set null,
  status varchar(16) not null default 'WAITING',
  created_at timestamptz not null default now()
);

create index if not exists idx_rooms_status on rooms(status);

create table if not exists matches (
  id uuid primary key default gen_random_uuid(),
  room_id uuid references rooms(id) on delete set null,
  player1_id uuid not null references users(id),
  player2_id uuid not null references users(id),
  player1_runs integer not null default 0,
  player2_runs integer not null default 0,
  winner_id uuid references users(id),
  is_friendly boolean not null default false,
  current_innings integer not null default 1,
  batter_id uuid references users(id),
  bowler_id uuid references users(id),
  toss_winner_id uuid references users(id),
  status varchar(16) not null default 'ONGOING',
  started_at timestamptz not null default now(),
  finished_at timestamptz
);

create index if not exists idx_matches_status on matches(status);
create index if not exists idx_matches_players on matches(player1_id, player2_id);

create table if not exists balls (
  id bigserial primary key,
  match_id uuid not null references matches(id) on delete cascade,
  innings integer not null,
  ball_no integer not null,
  batter_pick integer not null check (batter_pick between 0 and 6),
  bowler_pick integer not null check (bowler_pick between 0 and 6),
  runs_scored integer not null,
  is_wicket boolean not null default false,
  created_at timestamptz not null default now()
);

alter table balls drop constraint if exists balls_batter_pick_check;
alter table balls drop constraint if exists balls_bowler_pick_check;

do $$
begin
  if not exists (
    select 1 from pg_constraint
    where conname = 'balls_batter_pick_range_chk'
      and conrelid = 'balls'::regclass
  ) then
    alter table balls
      add constraint balls_batter_pick_range_chk
      check (batter_pick between 0 and 6);
  end if;

  if not exists (
    select 1 from pg_constraint
    where conname = 'balls_bowler_pick_range_chk'
      and conrelid = 'balls'::regclass
  ) then
    alter table balls
      add constraint balls_bowler_pick_range_chk
      check (bowler_pick between 0 and 6);
  end if;
end $$;

create index if not exists idx_balls_match on balls(match_id, innings, ball_no);
