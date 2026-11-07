package com.example.hotelservice.Room.availability.service;

import com.example.hotelservice.Hotel.repository.HotelRepository;
import com.example.hotelservice.Room.availability.repository.RoomAvailabilityRepository;
import com.example.hotelservice.Room.availability.repository.RoomHoldRepository;
import com.example.hotelservice.Room.repository.RoomTypeRepository;
import com.example.hotelservice.config.HoldExpiryProperties;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Opt-in real PostgreSQL/JPA read budget. All fixture writes roll back; no application/jobs start. */
@EnabledIfEnvironmentVariable(named = "CHECKINN_RANGE_TEST", matches = "true")
class AvailabilityRangeIntegrationTest {
    @Test
    void stayLengthDoesNotMultiplySqlAndCheckoutRemainsExclusive() {
        String url = System.getenv("CHECKINN_RANGE_DB_URL");
        assertThat(url).startsWith("jdbc:postgresql://127.0.0.1:").endsWith("/checkinn_hotel");
        var dataSource = new DriverManagerDataSource(url, "checkinn_test", System.getenv("CHECKINN_RANGE_DB_PASSWORD"));
        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("com.example.hotelservice");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none", "hibernate.show_sql", "false",
                "hibernate.generate_statistics", "true", "hibernate.session.events.log", "false"));
        factory.afterPropertiesSet();
        var emf = factory.getObject();
        assertThat(emf).isNotNull();
        var em = emf.createEntityManager();
        var transaction = em.getTransaction();
        UUID hotelId = UUID.randomUUID(), roomId = UUID.randomUUID();
        LocalDate start = LocalDate.of(2028, 2, 1);
        try {
            transaction.begin();
            em.createNativeQuery("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ").executeUpdate();
            Object[] identity = (Object[]) em.createNativeQuery("SELECT current_database(),current_user").getSingleResult();
            assertThat(identity).containsExactly("checkinn_hotel", "checkinn_test");
            // Copy valid shape only inside this uncommitted, rolled-back fixture transaction.
            assertThat(em.createNativeQuery("INSERT INTO hotels SELECT (jsonb_populate_record(NULL::hotels,to_jsonb(h)||"
                    + "jsonb_build_object('id',:id,'owner_id',:owner,'name','Synthetic range read',"
                    + "'is_active',true,'approved_status','APPROVED'))).* FROM hotels h LIMIT 1")
                    .setParameter("id", hotelId.toString()).setParameter("owner", UUID.randomUUID().toString()).executeUpdate()).isOne();
            assertThat(em.createNativeQuery("INSERT INTO room_types SELECT (jsonb_populate_record(NULL::room_types,to_jsonb(r)||"
                    + "jsonb_build_object('id',:id,'hotel_id',:hotel,'total_rooms',10,'is_active',true))).* FROM room_types r LIMIT 1")
                    .setParameter("id", roomId.toString()).setParameter("hotel", hotelId.toString()).executeUpdate()).isOne();
            for (int offset : new int[]{-1, 0, 10, 15, 30}) {
                int held = offset == 0 ? 1 : offset == 10 ? 4 : offset == 15 ? 2 : 100;
                int booked = offset == 0 ? 1 : offset == 10 ? 0 : offset == 15 ? 3 : 100;
                em.createNativeQuery("INSERT INTO room_availability(id,room_type_id,date,held,booked,created_at,updated_at)"
                        + " VALUES (:id,:room,:date,:held,:booked,now(),now())")
                        .setParameter("id", UUID.randomUUID()).setParameter("room", roomId)
                        .setParameter("date", start.plusDays(offset)).setParameter("held", held).setParameter("booked", booked).executeUpdate();
            }
            var repositories = new JpaRepositoryFactory(em);
            var service = new AvailabilityService(repositories.getRepository(RoomTypeRepository.class),
                    repositories.getRepository(HotelRepository.class), repositories.getRepository(RoomAvailabilityRepository.class),
                    mock(RoomHoldRepository.class), new HoldExpiryProperties());
            var statistics = emf.unwrap(SessionFactory.class).getStatistics();
            for (int nights : new int[]{1, 30}) {
                em.clear();
                statistics.clear();
                assertThat(service.getAvailableRoomCount(roomId, start, start.plusDays(nights))).isEqualTo(nights == 1 ? 8 : 5);
                // One room + one published parent + one stay-range read, regardless of nights.
                assertThat(statistics.getPrepareStatementCount()).as("SQL budget for %s nights", nights).isEqualTo(3);
            }
            // No rows inside the stay: use configured stock, without creating dated rows.
            assertThat(service.getAvailableRoomCount(roomId, start.plusDays(31), start.plusDays(35))).isEqualTo(10);
            UUID secondRoom = UUID.randomUUID(), thirdRoom = UUID.randomUUID();
            for (UUID id : java.util.List.of(secondRoom, thirdRoom)) {
                em.createNativeQuery("INSERT INTO room_types SELECT (jsonb_populate_record(NULL::room_types,to_jsonb(r)||jsonb_build_object('id',:id))).* FROM room_types r WHERE id=:original")
                        .setParameter("id", id.toString()).setParameter("original", roomId).executeUpdate();
            }
            em.createNativeQuery("INSERT INTO room_availability(id,room_type_id,date,held,booked,created_at,updated_at) VALUES (:id,:room,:date,3,4,now(),now())")
                    .setParameter("id", UUID.randomUUID()).setParameter("room", secondRoom).setParameter("date", start).executeUpdate();
            em.clear(); statistics.clear();
            assertThat(service.getAvailableRoomCounts(java.util.List.of(roomId, secondRoom, thirdRoom, roomId), start, start.plusDays(1)))
                    .containsExactlyInAnyOrderEntriesOf(Map.of(roomId,8,secondRoom,3,thirdRoom,10));
            assertThat(statistics.getPrepareStatementCount()).as("bulk room SQL budget").isEqualTo(3);
            // Aggregate arithmetic must widen before adding the two integer columns.
            em.createNativeQuery("UPDATE room_availability SET held=2147483647,booked=2147483647 WHERE room_type_id=:room AND date=:date")
                    .setParameter("room", roomId).setParameter("date", start).executeUpdate();
            em.clear();
            assertThat(service.getAvailableRoomCount(roomId, start, start.plusDays(1))).isZero();
            em.createNativeQuery("UPDATE room_availability SET held=-1,booked=5 WHERE room_type_id=:room AND date=:date")
                    .setParameter("room", roomId).setParameter("date", start).executeUpdate();
            em.clear();
            org.assertj.core.api.Assertions.assertThatThrownBy(()->service.getAvailableRoomCount(roomId,start,start.plusDays(1)))
                    .isInstanceOf(IllegalStateException.class);
            org.assertj.core.api.Assertions.assertThatThrownBy(()->service.getAvailableRoomCounts(java.util.List.of(roomId,secondRoom),start,start.plusDays(1)))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            if (transaction.isActive()) transaction.rollback();
            em.close();
            factory.destroy();
        }
    }
}
