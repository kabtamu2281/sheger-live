-- Ethio Live: initial gift catalog and coin packages.
-- Artwork and animation keys map to original vector artwork drawn in the Android app (ui/gifts/GiftArt.kt).
-- Prices are starting values; finance staff change them from the admin dashboard (audited).

insert into gifts (id, name, name_am, category, price_coins, artwork_key, animation_key, is_animated_fullscreen, sort_order) values
  ('adey',          'Adey Flower',   'አደይ አበባ',   'free',             0,    'adey',     'float',    false, 1),
  ('clap',          'Applause',      'ጭብጨባ',      'free',             0,    'clap',     'pop',      false, 2),
  ('buna',          'Buna Cup',      'ቡና',        'basic',            10,   'buna',     'float',    false, 10),
  ('rose',          'Rose',          'ጽጌረዳ',      'basic',            20,   'rose',     'float',    false, 11),
  ('heart_wave',    'Heart Wave',    'የልብ ሞገድ',   'creator_favorite', 30,   'heart',    'burst',    false, 12),
  ('injera',        'Injera Platter','እንጀራ',      'basic',            50,   'injera',   'pop',      false, 13),
  ('jebena',        'Jebena',        'ጀበና',       'basic',            99,   'jebena',   'pour',     false, 14),
  ('enkutatash',    'New Year Bloom','እንቁጣጣሽ',    'seasonal',         149,  'bloom',    'burst',    false, 20),
  ('kebero',        'Kebero Drum',   'ከበሮ',       'premium',          199,  'kebero',   'shake',    false, 21),
  ('demera',        'Demera Fire',   'ደመራ',       'seasonal',         299,  'demera',   'flame',    true,  22),
  ('gelada',        'Gelada',        'ጌላዳ',       'premium',          499,  'gelada',   'jump',     true,  23),
  ('lion',          'Golden Lion',   'አንበሳ',      'premium',          999,  'lion',     'roar',     true,  30),
  ('crown',         'Crown',         'ዘውድ',       'premium',          1999, 'crown',    'shine',    true,  31),
  ('lalibela',      'Lalibela',      'ላሊበላ',      'premium',          4999, 'lalibela', 'rise',     true,  32);

insert into coin_packages (id, coins, bonus_promo_coins, price_minor, currency, play_product_id, sort_order) values
  ('coins_100',   100,  0,    5000,   'ETB', 'el_coins_100',  1),
  ('coins_500',   500,  25,   24000,  'ETB', 'el_coins_500',  2),
  ('coins_1200',  1200, 100,  55000,  'ETB', 'el_coins_1200', 3),
  ('coins_3000',  3000, 400,  135000, 'ETB', 'el_coins_3000', 4),
  ('coins_6500',  6500, 1000, 280000, 'ETB', 'el_coins_6500', 5);
