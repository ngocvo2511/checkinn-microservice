package com.example.hotelservice.City.service;

import com.example.hotelservice.City.repository.CityRepository;
import com.example.hotelservice.City.repository.ProvinceRepository;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Actual SQL/loaded-entity budget; isolated uncommitted fixtures, no application/jobs. */
@EnabledIfEnvironmentVariable(named = "CHECKINN_RANGE_TEST", matches = "true")
class LocationSearchIntegrationTest {
    @Test void selectiveSearchDoesNotHydrateTheEntireCatalogOrLoseLiteralUnicodeNames() {
        String url = System.getenv("CHECKINN_RANGE_DB_URL");
        assertThat(url).startsWith("jdbc:postgresql://127.0.0.1:").endsWith("/checkinn_hotel");
        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(new DriverManagerDataSource(url, "checkinn_test", System.getenv("CHECKINN_RANGE_DB_PASSWORD")));
        factory.setPackagesToScan("com.example.hotelservice");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none", "hibernate.show_sql", "false",
                "hibernate.generate_statistics", "true", "hibernate.session.events.log", "false"));
        factory.afterPropertiesSet();
        var emf = factory.getObject();
        assertThat(emf).isNotNull();
        var em = emf.createEntityManager();
        var transaction = em.getTransaction();
        String marker = "location_" + UUID.randomUUID().toString().replace("-", "");
        String literal = marker + " %_\\ Đà Nẵng";
        List<UUID> provinces = new ArrayList<>(), cities = new ArrayList<>();
        try {
            transaction.begin();
            em.createNativeQuery("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ").executeUpdate();
            assertThat((Object[]) em.createNativeQuery("SELECT current_database(),current_user").getSingleResult())
                    .containsExactly("checkinn_hotel", "checkinn_test");
            for (int i = 0; i < 4; i++) {
                UUID province = UUID.randomUUID(), city = UUID.randomUUID();
                provinces.add(province); cities.add(city);
                String prefix = i < 2 ? literal : marker + " XX\\ noise";
                em.createNativeQuery("INSERT INTO provinces(id,name,hotel_count,created_at) VALUES (:id,:name,0,now())")
                        .setParameter("id", province).setParameter("name", prefix + " province" + i).executeUpdate();
                em.createNativeQuery("INSERT INTO cities(id,name,parent_id,hotel_count,created_at) VALUES (:id,:name,:parent,0,now())")
                        .setParameter("id", city).setParameter("name", prefix + " cityonly" + i).setParameter("parent", province).executeUpdate();
            }
            var repositories = new JpaRepositoryFactory(em);
            var service = new LocationService(repositories.getRepository(ProvinceRepository.class), repositories.getRepository(CityRepository.class));
            var statistics = emf.unwrap(SessionFactory.class).getStatistics();
            em.clear(); statistics.clear();
            var matches = service.searchLocations(literal.toUpperCase(Locale.ROOT));
            assertThat(matches).extracting(v -> v.id()).containsExactlyInAnyOrder(provinces.get(0), provinces.get(1), cities.get(0), cities.get(1));
            assertThat(statistics.getEntityLoadCount()).as("hydrate only matches and required parents").isEqualTo(4);
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
            em.clear(); statistics.clear();
            var cityMatches = service.searchLocations(literal + " cityonly");
            assertThat(cityMatches).hasSize(2);
            for (int i = 0; i < 2; i++) {
                UUID cityId = cities.get(i), provinceId = provinces.get(i);
                assertThat(cityMatches).anySatisfy(v -> {
                    assertThat(v.id()).isEqualTo(cityId);
                    assertThat(v.type()).isEqualTo("CITY");
                    assertThat(v.parentId()).isEqualTo(provinceId);
                    assertThat(v.parentName()).startsWith(literal);
                });
            }
            assertThat(statistics.getEntityLoadCount()).isEqualTo(4);
            assertThat(statistics.getPrepareStatementCount()).as("parents fetched without per-city lazy queries").isEqualTo(2);
            assertThat(service.searchLocations(marker + " absent")).isEmpty();
            var publicHotels=new ArrayList<UUID>();
            for(int i=0;i<5;i++) {
                UUID hotelId=UUID.randomUUID();
                boolean active=i!=4;String status=i==3?"DRAFT":"APPROVED";
                UUID cityId=cities.get(i<3?i:i-3);
                assertThat(em.createNativeQuery("INSERT INTO hotels SELECT (jsonb_populate_record(NULL::hotels,to_jsonb(h)||jsonb_build_object('id',:id,'city_id',:city,'name',:name,'is_active',:active,'approved_status',:status))).* FROM hotels h LIMIT 1")
                        .setParameter("id",hotelId.toString()).setParameter("city",cityId.toString()).setParameter("name",marker+" hotel"+i)
                        .setParameter("active",active).setParameter("status",status).executeUpdate()).isOne();
                if(i<2)publicHotels.add(hotelId);
            }
            var hotelRepository=repositories.getRepository(com.example.hotelservice.Hotel.repository.HotelRepository.class);
            // Only read methods are exercised; no external clients/application context start.
            var hotelService=new com.example.hotelservice.Hotel.service.HotelServiceImpl(null,null,null,hotelRepository,null,null,null,null,null,null,null,null,null);
            em.clear();statistics.clear();
            assertThat(hotelService.getByCities(List.of(cities.get(0),cities.get(1),cities.get(0))))
                    .extracting(h->h.getId()).containsExactlyInAnyOrderElementsOf(publicHotels);
            assertThat(statistics.getPrepareStatementCount()).as("province candidate hotel-query budget").isEqualTo(1);
        } finally {
            if (transaction.isActive()) transaction.rollback();
            em.close(); factory.destroy();
        }
    }
}
