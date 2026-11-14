package com.example.hotelservice.canonical;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="BATCH5B_ACCEPTANCE",matches="true")
class CanonicalPostgresAcceptanceTest {

    private final String schema="public";
    private org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean factory;
    private jakarta.persistence.EntityManager em;
    private org.springframework.jdbc.core.JdbcTemplate jdbc;
    private org.springframework.transaction.support.TransactionTemplate tx;
    @org.junit.jupiter.api.BeforeEach void bootstrap() throws Exception {
        String owner="hotel", db=System.getenv("BATCH5B_DATABASE");
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        assertThat(db).matches("batch5b_"+owner+"_[0-9]+_[0-9]+");
        String url=System.getenv("BATCH5B_URL");
        assertThat(url).isEqualTo("jdbc:postgresql://127.0.0.1:55486/"+db+"?currentSchema=public");
        String migrator="batch5b_"+owner+"_migrator",app="batch5b_"+owner+"_app";
        var migrationDs=new org.springframework.jdbc.datasource.DriverManagerDataSource(url,migrator,System.getenv("BATCH5B_MIGRATION_PASSWORD"));
        var migrationJdbc=new org.springframework.jdbc.core.JdbcTemplate(migrationDs);
        assertThat(migrationJdbc.queryForObject("SELECT current_database()",String.class)).isEqualTo(db);
        assertThat(java.nio.file.Path.of(migrationJdbc.queryForObject("SHOW data_directory",String.class)).toRealPath()).isEqualTo(java.nio.file.Path.of(System.getenv("BATCH5B_DATA_DIR")).toRealPath());
        assertThat(migrationJdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public'",Long.class)).isZero();
        var flyway=org.flywaydb.core.Flyway.configure().dataSource(migrationDs).locations("classpath:db/canonical").defaultSchema("public").schemas("public").baselineOnMigrate(false).cleanDisabled(true).outOfOrder(false).validateOnMigrate(true).load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(migrationJdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='1' AND success AND type='SQL'",Long.class)).isEqualTo(1L);
        assertThat(migrationJdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name<>'flyway_schema_history'",Long.class)).isEqualTo(24L);
        migrationJdbc.execute("GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO "+app);
        migrationJdbc.execute("REVOKE ALL ON flyway_schema_history FROM "+app);
        migrationJdbc.execute("REVOKE UPDATE,DELETE ON checkinn_feature_migrations FROM "+app);
        migrationJdbc.execute("REVOKE INSERT ON checkinn_feature_migrations FROM "+app);
        migrationJdbc.execute("GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO "+app);
        var ds=new org.springframework.jdbc.datasource.DriverManagerDataSource(url,app,System.getenv("BATCH5B_APP_PASSWORD"));
        jdbc=new org.springframework.jdbc.core.JdbcTemplate(ds);
        assertThat(jdbc.queryForObject("SHOW timezone",String.class)).isEqualTo("UTC");
        assertThatThrownBy(()->jdbc.execute("ALTER TABLE amenity_category_types ADD COLUMN forbidden integer")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.execute("DROP TABLE amenity_category_types")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.execute("CREATE TABLE forbidden(id integer)")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.queryForList("SELECT * FROM flyway_schema_history")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT has_database_privilege(current_user,?,'CONNECT')",Boolean.class,"batch5b_"+(owner.equals("user")?"hotel":"user"))).isFalse();
        factory=new org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(ds);factory.setPackagesToScan("com.example.hotelservice");
        factory.setJpaVendorAdapter(new org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(java.util.Map.of("hibernate.hbm2ddl.auto","validate","hibernate.default_schema","public","hibernate.jdbc.time_zone","UTC","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
        factory.afterPropertiesSet();
        em=org.springframework.orm.jpa.SharedEntityManagerCreator.createSharedEntityManager(factory.getObject());
        tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.orm.jpa.JpaTransactionManager(factory.getObject()));
        assertThat(new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em).getRepository(com.example.hotelservice.Room.repository.RoomTypeRepository.class).count()).isZero();
        assertThat(new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em).getRepository(com.example.hotelservice.Room.availability.repository.RoomAvailabilityRepository.class).count()).isZero();
        assertThat(new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em).getRepository(com.example.hotelservice.Room.availability.repository.RoomHoldRepository.class).count()).isZero();
        assertThat(new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em).getRepository(com.example.hotelservice.Review.repository.HotelReviewRepository.class).count()).isZero();
        assertThat(new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em).getRepository(com.example.hotelservice.Review.repository.ReviewFeedbackRepository.class).count()).isZero();
        assertThat(new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em).getRepository(com.example.hotelservice.Review.repository.ReviewResponseRepository.class).count()).isZero();
        assertThat(new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em).getRepository(com.example.hotelservice.Question.repository.QuestionRepository.class).count()).isZero();
        assertThat(new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em).getRepository(com.example.hotelservice.Policy.repository.HotelPolicyRepository.class).count()).isZero();
        assertThat(new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em).getRepository(com.example.hotelservice.Policy.repository.PolicyCategoryTypeRepository.class).count()).isZero();
        assertThat(new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em).getRepository(com.example.hotelservice.MediaAsset.repository.MediaAssetRepository.class).count()).isZero();
        assertThat(new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em).getRepository(com.example.hotelservice.Hotel.repository.AccommodationTypeChangeRepository.class).count()).isZero();
        assertThat(new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em).getRepository(com.example.hotelservice.Hotel.repository.HotelRepository.class).count()).isZero();
        assertThat(new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em).getRepository(com.example.hotelservice.City.repository.CityRepository.class).count()).isZero();
        assertThat(new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em).getRepository(com.example.hotelservice.City.repository.ProvinceRepository.class).count()).isZero();
        assertThat(new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em).getRepository(com.example.hotelservice.Amenity.repository.CategoryTypeRepository.class).count()).isZero();
        assertThat(new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em).getRepository(com.example.hotelservice.Amenity.repository.HotelAmenityCategoryRepository.class).count()).isZero();
        assertThat(new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em).getRepository(com.example.hotelservice.Amenity.repository.HotelAmenityRepository.class).count()).isZero();
    }
    @org.junit.jupiter.api.AfterEach void close() { if(factory!=null)factory.destroy(); }
    private void uuidColumn(String table,String column) {
        assertThat(jdbc.queryForObject("SELECT udt_name FROM information_schema.columns WHERE table_schema=? AND table_name=? AND column_name=?",String.class,schema,table,column)).isEqualTo("uuid");
    }
    @org.junit.jupiter.api.Test void guardsAndInventoryConstraints() {
        new com.example.hotelservice.config.AccommodationSchemaGuard(jdbc).run(null);
        new com.example.hotelservice.config.AmenitySchemaGuard(jdbc).run(null);
        var repos=new org.springframework.data.jpa.repository.support.JpaRepositoryFactory(em);
        var rooms=repos.getRepository(com.example.hotelservice.Room.repository.RoomTypeRepository.class);
        var hotels=repos.getRepository(com.example.hotelservice.Hotel.repository.HotelRepository.class);
        var availability=repos.getRepository(com.example.hotelservice.Room.availability.repository.RoomAvailabilityRepository.class);
        var holds=repos.getRepository(com.example.hotelservice.Room.availability.repository.RoomHoldRepository.class);
        var city=com.example.hotelservice.City.entity.City.builder().name("Batch5B city").hotelCount(0).build();
        var hotel=com.example.hotelservice.Hotel.entity.Hotel.builder().ownerId(UUID.randomUUID()).name("Batch5B hotel").address("{}").businessLicenseNumber("fixture").isActive(true).approvedStatus(com.example.hotelservice.Hotel.enums.HotelApprovalStatus.APPROVED).accommodationType(com.example.hotelservice.Hotel.enums.AccommodationType.HOTEL).build();
        var type=com.example.hotelservice.Room.entity.RoomType.builder().name("Batch5B room").capacity("{}").basePrice(java.math.BigDecimal.TEN).totalRooms(2).isActive(true).build();
        tx.executeWithoutResult(status->{em.persist(city);em.flush();hotel.setCityId(city.getId());em.persist(hotel);em.flush();type.setHotelId(hotel.getId());em.persist(type);em.flush();});
        var service=new com.example.hotelservice.Room.availability.service.AvailabilityService(rooms,hotels,availability,holds,new com.example.hotelservice.config.HoldExpiryProperties());
        var date=java.time.LocalDate.of(2027,1,10);
        var request=new com.example.hotelservice.Room.availability.dto.HoldRequest(type.getId(),date,date.plusDays(1),2,UUID.randomUUID());
        var first=tx.execute(status->service.checkAndHold(request));
        var retry=tx.execute(status->service.checkAndHold(request));
        assertThat(retry).isEqualTo(first);
        assertThatThrownBy(()->tx.execute(status->service.checkAndHold(new com.example.hotelservice.Room.availability.dto.HoldRequest(type.getId(),date,date.plusDays(1),1,UUID.randomUUID())))).isInstanceOf(com.example.hotelservice.Room.availability.service.InsufficientInventoryException.class);
        tx.executeWithoutResult(status->service.confirmHold(first.holdId()));
        tx.executeWithoutResult(status->service.confirmHold(first.holdId()));
        assertThat(jdbc.queryForObject("SELECT booked FROM room_availability WHERE room_type_id=? AND date=?",Integer.class,type.getId(),date)).isEqualTo(2);
        tx.executeWithoutResult(status->service.cancelReservation(first.holdId()));
        tx.executeWithoutResult(status->service.cancelReservation(first.holdId()));
        assertThat(jdbc.queryForObject("SELECT held+booked FROM room_availability WHERE room_type_id=? AND date=?",Integer.class,type.getId(),date)).isZero();
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        var gate=new java.util.concurrent.CountDownLatch(1);
        var winners=new java.util.concurrent.atomic.AtomicInteger();
        var winningHold=new java.util.concurrent.atomic.AtomicReference<UUID>();
        try {
            var futures=new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for(int i=0;i<2;i++) futures.add(pool.submit(()->{
                try {
                    gate.await();
                    var result=tx.execute(status->service.checkAndHold(new com.example.hotelservice.Room.availability.dto.HoldRequest(type.getId(),date,date.plusDays(1),2,UUID.randomUUID())));
                    winners.incrementAndGet();winningHold.set(result.holdId());
                } catch(com.example.hotelservice.Room.availability.service.InsufficientInventoryException expected) {
                    // Competing transaction sees stock held by the committed winner.
                } catch(InterruptedException interrupted) {Thread.currentThread().interrupt();throw new RuntimeException(interrupted);}
            }));
            gate.countDown();for(var f:futures)f.get(30,java.util.concurrent.TimeUnit.SECONDS);
        } catch(Exception failure) {throw new RuntimeException(failure);} finally {pool.shutdownNow();}
        assertThat(winners.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT held+booked FROM room_availability WHERE room_type_id=? AND date=?",Integer.class,type.getId(),date)).isEqualTo(2);
        tx.executeWithoutResult(status->service.releaseHold(winningHold.get()));
        tx.executeWithoutResult(status->service.releaseHold(winningHold.get()));
        var room=UUID.randomUUID();
        jdbc.update("INSERT INTO room_availability(id,room_type_id,date,held,booked,created_at,updated_at) VALUES (?,?,CURRENT_DATE,0,0,now(),now())",UUID.randomUUID(),room);
        assertThatThrownBy(()->jdbc.update("INSERT INTO room_availability(id,room_type_id,date,held,booked,created_at,updated_at) VALUES (?,?,CURRENT_DATE,0,0,now(),now())",UUID.randomUUID(),room)).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE room_availability SET held=-1 WHERE room_type_id=?",room)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT column_default IS NULL FROM information_schema.columns WHERE table_schema='public' AND table_name='room_types' AND column_name='total_rooms'",Boolean.class)).isTrue();
    }

}
