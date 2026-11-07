package com.example.hotelservice.Hotel.service;

import com.example.hotelservice.Hotel.mapper.HotelMapper;
import com.example.hotelservice.Hotel.repository.HotelRepository;
import com.example.hotelservice.Room.mapper.RoomTypeMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mapstruct.factory.Mappers;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Candidate facts only: measured SQL excludes parent lookup and final selected detail mapping. */
@EnabledIfEnvironmentVariable(named = "CHECKINN_RANGE_TEST", matches = "true")
class HotelOfferCandidatesIntegrationTest {
    @Test void candidateFactsDoNotReadRoomAndAmenityCollectionsPerProperty() {
        String url=System.getenv("CHECKINN_RANGE_DB_URL");
        assertThat(url).startsWith("jdbc:postgresql://127.0.0.1:").endsWith("/checkinn_hotel");
        var factory=new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(new DriverManagerDataSource(url,"checkinn_test",System.getenv("CHECKINN_RANGE_DB_PASSWORD")));
        factory.setPackagesToScan("com.example.hotelservice");factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","none","hibernate.show_sql","false",
                "hibernate.generate_statistics","true","hibernate.session.events.log","false"));
        factory.afterPropertiesSet();var emf=factory.getObject();assertThat(emf).isNotNull();
        var em=emf.createEntityManager();var transaction=em.getTransaction();
        var hotels=List.of(UUID.randomUUID(),UUID.randomUUID());
        var rooms=List.of(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        var categories=List.of(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        try {
            transaction.begin();em.createNativeQuery("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ").executeUpdate();
            assertThat((Object[])em.createNativeQuery("SELECT current_database(),current_user").getSingleResult()).containsExactly("checkinn_hotel","checkinn_test");
            for(UUID id:hotels)assertThat(em.createNativeQuery("INSERT INTO hotels SELECT (jsonb_populate_record(NULL::hotels,to_jsonb(h)||jsonb_build_object('id',:id,'name','Synthetic candidate facts','is_active',true,'approved_status','APPROVED'))).* FROM hotels h LIMIT 1")
                    .setParameter("id",id.toString()).executeUpdate()).isOne();
            for(int i=0;i<3;i++) {
                assertThat(em.createNativeQuery("INSERT INTO room_types SELECT (jsonb_populate_record(NULL::room_types,to_jsonb(r)||jsonb_build_object('id',:id,'hotel_id',:hotel,'base_price',:price,'total_rooms',3,'capacity',jsonb_build_object('adults',:adults,'children',0),'is_active',:active))).* FROM room_types r LIMIT 1")
                        .setParameter("id",rooms.get(i).toString()).setParameter("hotel",hotels.get(i==1?1:0).toString())
                        .setParameter("price",i==1?200000:100000).setParameter("adults",i==1?4:2).setParameter("active",i!=2).executeUpdate()).isOne();
                em.createNativeQuery("INSERT INTO hotel_amenity_categories(id,hotel_id,title,is_active,created_at,updated_at) VALUES (:id,:hotel,:title,:active,now(),now())")
                        .setParameter("id",categories.get(i)).setParameter("hotel",hotels.get(i==2?1:0)).setParameter("title","Synthetic category"+i).setParameter("active",i!=1).executeUpdate();
            }
            String[] labels={"Pool, Indoor","Hidden item","Hidden category amenity","Wi-Fi trả phí"};
            for(int i=0;i<4;i++)em.createNativeQuery("INSERT INTO hotel_amenities(id,category_id,title,is_active,created_at) VALUES (:id,:category,:title,:active,now())")
                    .setParameter("id",UUID.randomUUID()).setParameter("category",categories.get(i<2?0:i-1))
                    .setParameter("title",labels[i]).setParameter("active",i!=1).executeUpdate();
            var repositories=new JpaRepositoryFactory(em);
            var mapper=Mappers.getMapper(HotelMapper.class);var roomMapper=Mappers.getMapper(RoomTypeMapper.class);
            ReflectionTestUtils.setField(roomMapper,"objectMapper",new ObjectMapper());
            ReflectionTestUtils.setField(mapper,"roomTypeMapper",roomMapper);
            ReflectionTestUtils.setField(mapper,"hotelAmenityCategoryRepository",repositories.getRepository(com.example.hotelservice.Amenity.repository.HotelAmenityCategoryRepository.class));
            ReflectionTestUtils.setField(mapper,"hotelAmenityRepository",repositories.getRepository(com.example.hotelservice.Amenity.repository.HotelAmenityRepository.class));
            // Fixture items have no reviewed code mappings; this test measures JPA room/raw-facet reads only.
            ReflectionTestUtils.setField(mapper,"amenityCatalog",mock(com.example.hotelservice.Amenity.service.AmenityCatalogService.class));
            em.clear();var candidates=repositories.getRepository(HotelRepository.class).findAllById(hotels);
            var statistics=emf.unwrap(SessionFactory.class).getStatistics();statistics.clear();
            var reader=new HotelOfferCandidates(repositories.getRepository(com.example.hotelservice.Room.repository.RoomTypeRepository.class),
                    repositories.getRepository(com.example.hotelservice.Amenity.repository.HotelAmenityRepository.class),mapper);
            var result=reader.read(candidates);
            var byHotel=result.stream().collect(Collectors.toMap(r->r.id(),r->r));
            assertThat(byHotel).containsOnlyKeys(hotels.toArray(UUID[]::new));
            for(int i=0;i<2;i++) {
                var facts=byHotel.get(hotels.get(i));
                assertThat(facts.roomTypes()).extracting(r->r.id()).containsExactly(rooms.get(i));
                assertThat(facts.roomTypes().get(0).capacity().adults()).isEqualTo(i==0?2:4);
                assertThat(facts.amenityCategories().stream().flatMap(c->c.getItems().stream()).map(a->a.getTitle()).toList())
                        .containsExactly(i==0?"Pool, Indoor":"Wi-Fi trả phí");
            }
            assertThat(statistics.getPrepareStatementCount()).as("candidate room/facet SQL budget for two hotels").isEqualTo(2);
        } finally {
            if(transaction.isActive())transaction.rollback();em.close();factory.destroy();
        }
    }
}
