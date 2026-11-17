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
        verifyEncodingRoundTrips(type);
        var service=new com.example.hotelservice.Room.availability.service.AvailabilityService(rooms,hotels,availability,holds,new com.example.hotelservice.config.HoldExpiryProperties());
        var date=java.time.LocalDate.of(2027,1,10);
        var request=new com.example.hotelservice.Room.availability.dto.HoldRequest(type.getId(),date,date.plusDays(1),2,UUID.randomUUID());
        assertThatThrownBy(()->tx.execute(status->service.checkAndHold(request)))
                .isInstanceOf(com.example.hotelservice.Room.availability.service.InventoryApprovalRequiredException.class);
        assertThat(tx.<java.util.Map<UUID,Integer>>execute(status->service.getAvailableRoomCounts(java.util.List.of(type.getId()),date,date.plusDays(1))))
                .containsEntry(type.getId(),0);
        var initialReviews=new com.example.hotelservice.Room.service.RoomInventoryReviewService(jdbc,rooms,hotels);
        var initialOwner=new com.example.hotelservice.Room.service.RoomInventoryReviewService.Actor(hotel.getOwnerId(),"OWNER");
        var initialAdmin=new com.example.hotelservice.Room.service.RoomInventoryReviewService.Actor(UUID.randomUUID(),"ADMIN");
        var initialProposal=tx.execute(status->initialReviews.propose(type.getId(),
                new com.example.hotelservice.Room.service.RoomInventoryReviewService.Proposal(2,0L,"Synthetic register"),initialOwner));
        tx.executeWithoutResult(status->initialReviews.review((UUID)initialProposal.get("id"),
                new com.example.hotelservice.Room.service.RoomInventoryReviewService.Review(true,1L,"Synthetic register checked"),initialAdmin));
        verifyAdmissionEvidence(rooms,type,service,date);
        var first=tx.execute(status->service.checkAndHold(request));
        var retry=tx.execute(status->service.checkAndHold(request));
        assertThat(retry).isEqualTo(first);
        jdbc.update("UPDATE room_inventory_review_requests SET status='REJECTED' WHERE id=?",initialProposal.get("id"));
        assertThat(tx.<com.example.hotelservice.Room.availability.dto.HoldResponse>execute(status->service.checkAndHold(request))).isEqualTo(first);
        assertThatThrownBy(()->tx.execute(status->service.checkAndHold(new com.example.hotelservice.Room.availability.dto.HoldRequest(type.getId(),date,date.plusDays(1),1,UUID.randomUUID()))))
                .isInstanceOf(com.example.hotelservice.Room.availability.service.InventoryApprovalRequiredException.class);
        tx.executeWithoutResult(status->service.confirmHold(first.holdId()));
        jdbc.update("UPDATE room_inventory_review_requests SET status='APPROVED' WHERE id=?",initialProposal.get("id"));

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
        verifyReviewedInventoryConcurrency(rooms,hotels,service,type,hotel,date);
        var room=UUID.randomUUID();
        jdbc.update("INSERT INTO room_availability(id,room_type_id,date,held,booked,created_at,updated_at) VALUES (?,?,CURRENT_DATE,0,0,now(),now())",UUID.randomUUID(),room);
        assertThatThrownBy(()->jdbc.update("INSERT INTO room_availability(id,room_type_id,date,held,booked,created_at,updated_at) VALUES (?,?,CURRENT_DATE,0,0,now(),now())",UUID.randomUUID(),room)).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE room_availability SET held=-1 WHERE room_type_id=?",room)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT column_default IS NULL FROM information_schema.columns WHERE table_schema='public' AND table_name='room_types' AND column_name='total_rooms'",Boolean.class)).isTrue();
    }

    private void verifyAdmissionEvidence(com.example.hotelservice.Room.repository.RoomTypeRepository rooms,
            com.example.hotelservice.Room.entity.RoomType room,
            com.example.hotelservice.Room.availability.service.AvailabilityService service,java.time.LocalDate date) {
        assertThat(rooms.hasEffectiveInventoryApproval(room.getId())).isTrue();
        assertThat(rooms.findEffectivelyApprovedRoomIds(java.util.List.of(room.getId(),UUID.randomUUID()))).containsExactly(room.getId());
        UUID approval=jdbc.queryForObject("SELECT approved_request_id FROM room_inventory_review_states WHERE room_id=?",UUID.class,room.getId());
        var original=jdbc.queryForMap("SELECT * FROM room_inventory_review_requests WHERE id=?",approval);
        var invalid=new java.util.LinkedHashMap<String,Object>();
        invalid.put("status","REJECTED");invalid.put("room_id",UUID.randomUUID());invalid.put("hotel_id",UUID.randomUUID());
        invalid.put("proposed_quantity",3);invalid.put("reviewed_by",null);invalid.put("reviewed_at",null);invalid.put("review_reason"," ");
        for(var mutation:invalid.entrySet()) {
            jdbc.update("UPDATE room_inventory_review_requests SET "+mutation.getKey()+"=? WHERE id=?",mutation.getValue(),approval);
            assertThat(rooms.hasEffectiveInventoryApproval(room.getId())).as(mutation.getKey()).isFalse();
            assertThat(rooms.findEffectivelyApprovedRoomIds(java.util.List.of(room.getId()))).as("batch predicate: "+mutation.getKey()).isEmpty();
            assertThatThrownBy(()->tx.execute(status->service.checkAndHold(new com.example.hotelservice.Room.availability.dto.HoldRequest(room.getId(),date,date.plusDays(1),1,UUID.randomUUID()))))
                    .isInstanceOf(com.example.hotelservice.Room.availability.service.InventoryApprovalRequiredException.class);
            jdbc.update("UPDATE room_inventory_review_requests SET "+mutation.getKey()+"=? WHERE id=?",original.get(mutation.getKey()),approval);
        }
        jdbc.update("UPDATE room_inventory_review_states SET revision=0 WHERE room_id=?",room.getId());
        assertThat(rooms.hasEffectiveInventoryApproval(room.getId())).isFalse();
        jdbc.update("UPDATE room_inventory_review_states SET revision=2 WHERE room_id=?",room.getId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM room_holds WHERE room_type_id=?",Long.class,room.getId())).isZero();
    }

    private void verifyReviewedInventoryConcurrency(
            com.example.hotelservice.Room.repository.RoomTypeRepository rooms,
            com.example.hotelservice.Hotel.repository.HotelRepository hotels,
            com.example.hotelservice.Room.availability.service.AvailabilityService availability,
            com.example.hotelservice.Room.entity.RoomType room,
            com.example.hotelservice.Hotel.entity.Hotel hotel,java.time.LocalDate date) {
        var reviews=new com.example.hotelservice.Room.service.RoomInventoryReviewService(jdbc,rooms,hotels);
        var owner=new com.example.hotelservice.Room.service.RoomInventoryReviewService.Actor(hotel.getOwnerId(),"OWNER");
        var admin=new com.example.hotelservice.Room.service.RoomInventoryReviewService.Actor(UUID.randomUUID(),"ADMIN");
        var held=tx.execute(s->availability.checkAndHold(new com.example.hotelservice.Room.availability.dto.HoldRequest(room.getId(),date,date.plusDays(1),2,UUID.randomUUID())));
        var proposal=tx.execute(s->reviews.propose(room.getId(),new com.example.hotelservice.Room.service.RoomInventoryReviewService.Proposal(1,2L,"Synthetic physical count"),owner));
        UUID proposalId=(UUID)proposal.get("id");
        var approval=new com.example.hotelservice.Room.service.RoomInventoryReviewService.Review(true,3L,"Synthetic evidence reviewed");
        assertThatThrownBy(()->tx.execute(s->reviews.review(proposalId,approval,admin)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(jdbc.queryForObject("SELECT total_rooms FROM room_types WHERE id=?",Integer.class,room.getId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT status FROM room_inventory_review_requests WHERE id=?",String.class,proposalId)).isEqualTo("PENDING");
        tx.executeWithoutResult(s->availability.releaseHold(held.holdId()));
        var gate=new java.util.concurrent.CountDownLatch(1);
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        var approved=new java.util.concurrent.atomic.AtomicBoolean();
        var holdId=new java.util.concurrent.atomic.AtomicReference<UUID>();
        try {
            var reviewFuture=executor.submit(()->{
                try {gate.await();tx.execute(s->reviews.review(proposalId,approval,admin));approved.set(true);}
                catch(org.springframework.web.server.ResponseStatusException conflict) {assertThat(conflict.getStatusCode().value()).isEqualTo(409);}
                catch(InterruptedException e) {Thread.currentThread().interrupt();throw new RuntimeException(e);}
            });
            var holdFuture=executor.submit(()->{
                try {gate.await();holdId.set(tx.execute(s->availability.checkAndHold(new com.example.hotelservice.Room.availability.dto.HoldRequest(room.getId(),date,date.plusDays(1),2,UUID.randomUUID()))).holdId());}
                catch(com.example.hotelservice.Room.availability.service.InsufficientInventoryException expected) { /* approved reduction won */ }
                catch(InterruptedException e) {Thread.currentThread().interrupt();throw new RuntimeException(e);}
            });
            gate.countDown();reviewFuture.get(30,java.util.concurrent.TimeUnit.SECONDS);holdFuture.get(30,java.util.concurrent.TimeUnit.SECONDS);
        } catch(Exception e) {throw new RuntimeException(e);} finally {executor.shutdownNow();}
        assertThat(approved.get()).isEqualTo(holdId.get()==null);
        assertThat(jdbc.queryForObject("SELECT total_rooms FROM room_types WHERE id=?",Integer.class,room.getId())).isEqualTo(approved.get()?1:2);
        if(holdId.get()!=null) {
            tx.executeWithoutResult(s->availability.releaseHold(holdId.get()));
            tx.executeWithoutResult(s->reviews.review(proposalId,approval,admin));
        }
        assertThat(tx.execute(s->reviews.state(room.getId(),owner)).verified()).isTrue();
        // Rejecting a later proposal preserves the previous approved physical source.
        var rejected=tx.execute(s->reviews.propose(room.getId(),new com.example.hotelservice.Room.service.RoomInventoryReviewService.Proposal(3,4L,"Synthetic second count"),owner));
        tx.executeWithoutResult(s->reviews.review((UUID)rejected.get("id"),new com.example.hotelservice.Room.service.RoomInventoryReviewService.Review(false,5L,"Source not sufficient"),admin));
        assertThat(tx.execute(s->reviews.state(room.getId(),owner)).verified()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM room_inventory_review_requests WHERE room_id=?",Long.class,room.getId())).isEqualTo(3L);
        verifyConcurrentReviewCommands(reviews,room,owner,admin);
        verifyProvenanceReport(room.getId());
    }

    private void verifyConcurrentReviewCommands(
            com.example.hotelservice.Room.service.RoomInventoryReviewService reviews,
            com.example.hotelservice.Room.entity.RoomType room,
            com.example.hotelservice.Room.service.RoomInventoryReviewService.Actor owner,
            com.example.hotelservice.Room.service.RoomInventoryReviewService.Actor admin) {
        // Two OWNER proposals with the same displayed revision: one accepted.
        var proposalWins=new java.util.concurrent.atomic.AtomicInteger();
        Runnable propose=()->{
            try { tx.execute(s->reviews.propose(room.getId(),new com.example.hotelservice.Room.service.RoomInventoryReviewService.Proposal(1,6L,"Concurrent source"),owner));proposalWins.incrementAndGet(); }
            catch(org.springframework.web.server.ResponseStatusException conflict) {assertThat(conflict.getStatusCode().value()).isEqualTo(409);}
        };
        race(propose,propose);assertThat(proposalWins.get()).isEqualTo(1);
        UUID pending=jdbc.queryForObject("SELECT latest_request_id FROM room_inventory_review_states WHERE room_id=?",UUID.class,room.getId());
        // Two ADMIN reviewers of one proposal: one audited decision.
        var reviewWins=new java.util.concurrent.atomic.AtomicInteger();
        Runnable approve=()->{
            try {tx.execute(s->reviews.review(pending,new com.example.hotelservice.Room.service.RoomInventoryReviewService.Review(true,7L,"Checked concurrent source"),admin));reviewWins.incrementAndGet();}
            catch(org.springframework.web.server.ResponseStatusException conflict) {assertThat(conflict.getStatusCode().value()).isEqualTo(409);}
        };
        race(approve,approve);assertThat(reviewWins.get()).isEqualTo(1);
        var older=tx.execute(s->reviews.propose(room.getId(),new com.example.hotelservice.Room.service.RoomInventoryReviewService.Proposal(1,8L,"Older source"),owner));
        var newer=tx.execute(s->reviews.propose(room.getId(),new com.example.hotelservice.Room.service.RoomInventoryReviewService.Proposal(1,9L,"Newer source"),owner));
        assertThatThrownBy(()->tx.execute(s->reviews.review((UUID)older.get("id"),new com.example.hotelservice.Room.service.RoomInventoryReviewService.Review(true,10L,"Stale source"),admin)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        // OWNER replacing and ADMIN approving a proposal concurrently cannot both win revision 10.
        var commandWins=new java.util.concurrent.atomic.AtomicInteger();
        race(()->{
            try {tx.execute(s->reviews.propose(room.getId(),new com.example.hotelservice.Room.service.RoomInventoryReviewService.Proposal(1,10L,"Replacement source"),owner));commandWins.incrementAndGet();}
            catch(org.springframework.web.server.ResponseStatusException conflict) {assertThat(conflict.getStatusCode().value()).isEqualTo(409);}
        },()->{
            try {tx.execute(s->reviews.review((UUID)newer.get("id"),new com.example.hotelservice.Room.service.RoomInventoryReviewService.Review(true,10L,"Checked newer source"),admin));commandWins.incrementAndGet();}
            catch(org.springframework.web.server.ResponseStatusException conflict) {assertThat(conflict.getStatusCode().value()).isEqualTo(409);}
        });
        assertThat(commandWins.get()).isEqualTo(1);
        assertThat(tx.execute(s->reviews.state(room.getId(),owner)).verified()).isTrue();
    }

    private void race(Runnable first,Runnable second) {
        var gate=new java.util.concurrent.CountDownLatch(1);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var futures=new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for(var action:java.util.List.of(first,second)) futures.add(pool.submit(()->{
                try {gate.await();action.run();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}
            }));
            gate.countDown();for(var future:futures)future.get(30,java.util.concurrent.TimeUnit.SECONDS);
        }catch(Exception e){throw new RuntimeException(e);}finally{pool.shutdownNow();}
    }

    private void verifyProvenanceReport(UUID verifiedRoom) {
        try {
            var script=java.nio.file.Files.readString(java.nio.file.Path.of(System.getProperty("basedir"),"..","docker","batch7-inventory-provenance.sql"));
            String query=script.substring(script.indexOf("WITH counters"),script.lastIndexOf("COMMIT;"));
            var rows=jdbc.queryForList(query);
            assertThat(rows).filteredOn(row->verifiedRoom.equals(row.get("room_id")))
                    .singleElement().satisfies(row->assertThat(row.get("provenance_class")).isEqualTo("VERIFIED"));
            jdbc.update("UPDATE room_inventory_review_requests SET reviewed_at=NULL WHERE id=(SELECT approved_request_id FROM room_inventory_review_states WHERE room_id=?)",verifiedRoom);
            assertThat(jdbc.queryForList(query)).filteredOn(row->verifiedRoom.equals(row.get("room_id")))
                    .singleElement().satisfies(row->assertThat(row.get("provenance_class")).isEqualTo("UNVERIFIED"));
            // Synthetic null and ten have identical unknown provenance; never alter either value.
            for(Integer stock:new Integer[]{null,10}) {
                UUID id=UUID.randomUUID();
                jdbc.update("INSERT INTO room_types(id,hotel_id,name,base_price,capacity,is_active,total_rooms,created_at,updated_at) SELECT ?,hotel_id,'Synthetic provenance',base_price,capacity,true,?,now(),now() FROM room_types WHERE id=?",id,stock,verifiedRoom);
                assertThat(jdbc.queryForList(query)).filteredOn(row->id.equals(row.get("room_id")))
                        .singleElement().satisfies(row->{assertThat(row.get("provenance_class")).isEqualTo("UNKNOWN_SOURCE");assertThat(row.get("total_rooms")).isEqualTo(stock);});
            }
            jdbc.update("UPDATE room_types SET total_rooms=0 WHERE id=?",verifiedRoom);
            assertThat(jdbc.queryForList(query)).filteredOn(row->verifiedRoom.equals(row.get("room_id")))
                    .singleElement().satisfies(row->assertThat(row.get("provenance_class")).isEqualTo("INVALID_CONFIRMED"));
        } catch(java.io.IOException e) {throw new java.io.UncheckedIOException(e);}
    }

    /** Synthetic contract checks; these are not evidence about an actual legacy corpus. */
    private void verifyEncodingRoundTrips(com.example.hotelservice.Room.entity.RoomType type) {
        var mapper=org.mapstruct.factory.Mappers.getMapper(com.example.hotelservice.Room.mapper.RoomTypeMapper.class);
        org.springframework.test.util.ReflectionTestUtils.setField(mapper,"objectMapper",new com.fasterxml.jackson.databind.ObjectMapper());
        org.springframework.test.util.ReflectionTestUtils.setField(mapper,"mediaAssetMapper",
                org.mapstruct.factory.Mappers.getMapper(com.example.hotelservice.MediaAsset.mapper.MediaAssetMapper.class));
        for(String text:java.util.Arrays.asList(null,"plain text","null","\"quoted\"","{\"legacy\":true}","Tiếng Việt 🌊\nDòng hai")) {
            tx.executeWithoutResult(status->{
                var row=em.find(com.example.hotelservice.Room.entity.RoomType.class,type.getId());
                row.setDescription(text);row.setAmenities("[\"Tiếng Việt 🌊\",\"null\"]");em.flush();em.clear();
                var loaded=em.find(com.example.hotelservice.Room.entity.RoomType.class,type.getId());
                var dto=mapper.toRoomTypeResponse(loaded);
                assertThat(dto.description()).isEqualTo(text);
                assertThat(dto.amenities()).containsExactly("Tiếng Việt 🌊","null");
            });
        }
        for(String stored:java.util.Arrays.asList(null,"null","[]","[\"quoted\\\" label\"]","{}","[7]","\"legacy\"","\"[\\\"nested\\\"]\"")) {
            jdbc.update("UPDATE room_types SET amenities=CAST(? AS jsonb) WHERE id=?",stored,type.getId());
            String before=jdbc.queryForObject("SELECT amenities::text FROM room_types WHERE id=?",String.class,type.getId());
            tx.executeWithoutResult(status->{
                em.clear();var loaded=em.find(com.example.hotelservice.Room.entity.RoomType.class,type.getId());
                if(stored==null || "null".equals(stored))assertThat(mapper.toRoomTypeResponse(loaded).amenities()).isNull();
                else if(stored.startsWith("[\""))assertThat(mapper.toRoomTypeResponse(loaded).amenities()).containsExactly("quoted\" label");
                else if("[]".equals(stored))assertThat(mapper.toRoomTypeResponse(loaded).amenities()).isEmpty();
                else assertThatThrownBy(()->mapper.toRoomTypeResponse(loaded)).isInstanceOf(IllegalStateException.class);
            });
            assertThat(jdbc.queryForObject("SELECT amenities::text FROM room_types WHERE id=?",String.class,type.getId())).isEqualTo(before);
        }
        jdbc.update("UPDATE room_types SET amenities=NULL WHERE id=?",type.getId());
    }

}
