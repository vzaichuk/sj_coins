ALTER TABLE sj_coins.accounts ADD is_new BIT(1) DEFAULT b'1' NOT NULL AFTER image;