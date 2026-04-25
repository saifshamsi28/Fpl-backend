//package com.zpl.handcricket.config;
//
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.boot.ApplicationArguments;
//import org.springframework.boot.ApplicationRunner;
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.stereotype.Component;
//
///**
// * Keeps DB constraints compatible with runtime game rules.
// *
// * Older deployments may still have legacy checks:
// *   balls_batter_pick_check / balls_bowler_pick_check  (1..6)
// * Current runtime semantics allow missed picks as 0, so required range is 0..6.
// */
//@Component
//@Slf4j
//@RequiredArgsConstructor
//public class SchemaRepairConfig implements ApplicationRunner {
//
//    private final JdbcTemplate jdbc;
//
//    @Override
//    public void run(ApplicationArguments args) {
//        try {
//            jdbc.execute("alter table if exists balls drop constraint if exists balls_batter_pick_check");
//            jdbc.execute("alter table if exists balls drop constraint if exists balls_bowler_pick_check");
//
//            jdbc.execute("""
//                    do $$
//                    begin
//                      if exists (select 1 from pg_class where relname = 'balls') then
//                        if not exists (
//                          select 1 from pg_constraint
//                          where conname = 'balls_batter_pick_range_chk'
//                            and conrelid = 'balls'::regclass
//                        ) then
//                          alter table balls
//                            add constraint balls_batter_pick_range_chk
//                            check (batter_pick between 0 and 6);
//                        end if;
//
//                        if not exists (
//                          select 1 from pg_constraint
//                          where conname = 'balls_bowler_pick_range_chk'
//                            and conrelid = 'balls'::regclass
//                        ) then
//                          alter table balls
//                            add constraint balls_bowler_pick_range_chk
//                            check (bowler_pick between 0 and 6);
//                        end if;
//                      end if;
//                    end $$;
//                    """);
//            log.info("Schema repair check completed for balls pick constraints (0..6)");
//        } catch (Exception ex) {
//            // Keep app startup resilient; this should not crash live sessions.
//            log.error("Schema repair failed for balls constraints", ex);
//        }
//    }
//}
//
