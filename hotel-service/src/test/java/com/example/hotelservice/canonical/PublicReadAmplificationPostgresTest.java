package com.example.hotelservice.canonical;

import com.example.hotelservice.Amenity.service.AmenityCatalogService;
import com.example.hotelservice.Hotel.controller.HotelOfferController;
import com.example.hotelservice.Hotel.dto.request.HotelOfferCriteria;
import com.example.hotelservice.Hotel.dto.request.HotelOfferPageCriteria;
import com.example.hotelservice.Hotel.dto.response.HotelOfferResponse;
import com.example.hotelservice.Hotel.dto.response.HotelResponse;
import com.example.hotelservice.Hotel.entity.Hotel;
import com.example.hotelservice.Hotel.enums.HotelApprovalStatus;
import com.example.hotelservice.Hotel.mapper.HotelMapper;
import com.example.hotelservice.Hotel.repository.HotelRepository;
import com.example.hotelservice.Hotel.service.HotelOfferCandidates;
import com.example.hotelservice.Hotel.service.HotelOfferSearchService;
import com.example.hotelservice.Hotel.service.HotelService;
import com.example.hotelservice.MediaAsset.entity.MediaAsset;
import com.example.hotelservice.MediaAsset.enums.MediaTargetType;
import com.example.hotelservice.Room.entity.RoomType;
import com.example.hotelservice.Room.repository.RoomTypeRepository;
import com.example.hotelservice.Room.service.RoomInventoryReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mapstruct.factory.Mappers;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Batch8D SQL-count regression on a disposable canonical PostgreSQL database (never hosted).
 * Every JDBC statement prepared through the application DataSource is counted (Hibernate and
 * JdbcTemplate). Public search/catalog/detail statement counts must not grow with hotels/rooms,
 * while approval, pending exclusion, media/policy/FAQ/amenity grouping and paging stay correct.
 */
@EnabledIfEnvironmentVariable(named = "BATCH5B_ACCEPTANCE", matches = "true")
class PublicReadAmplificationPostgresTest {
    private static final LocalDate IN = LocalDate.of(2027, 2, 10), OUT = IN.plusDays(1);
    private final AtomicInteger statements = new AtomicInteger();
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private LocalContainerEntityManagerFactoryBean factory;
    private TransactionTemplate tx;
    private JdbcTemplate jdbc;
    private jakarta.persistence.EntityManager em;
    private HotelRepository hotels;
    private RoomTypeRepository rooms;
    private HotelMapper mapper;
    private AmenityCatalogService catalog;
    private RoomInventoryReviewService reviews;
    private HotelOfferController controller;

    private static Object call(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
        try { return method.invoke(target, args); } catch (InvocationTargetException e) { throw e.getCause(); }
    }

    private DataSource counting(DataSource raw) {
        var loader = getClass().getClassLoader();
        return (DataSource) Proxy.newProxyInstance(loader, new Class<?>[]{DataSource.class}, (proxy, method, args) -> {
            if (method.getName().equals("equals")) return proxy == args[0];
            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
            Object result = call(raw, method, args);
            if (!method.getName().equals("getConnection")) return result;
            Connection connection = (Connection) result;
            return Proxy.newProxyInstance(loader, new Class<?>[]{Connection.class}, (cp, cm, ca) -> {
                if (cm.getName().equals("equals")) return cp == ca[0];
                if (cm.getName().equals("hashCode")) return System.identityHashCode(cp);
                if (cm.getName().startsWith("prepareStatement") || cm.getName().equals("createStatement") || cm.getName().startsWith("prepareCall"))
                    statements.incrementAndGet();
                return call(connection, cm, ca);
            });
        });
    }

    @org.junit.jupiter.api.BeforeEach void bootstrap() {
        String owner = "hotel", db = System.getenv("BATCH5B_DATABASE");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        assertThat(db).matches("batch5b_" + owner + "_[0-9]+_[0-9]+");
        String url = System.getenv("BATCH5B_URL");
        assertThat(url).isEqualTo("jdbc:postgresql://127.0.0.1:55486/" + db + "?currentSchema=public");
        String migrator = "batch5b_" + owner + "_migrator", app = "batch5b_" + owner + "_app";
        var migrationDs = new DriverManagerDataSource(url, migrator, System.getenv("BATCH5B_MIGRATION_PASSWORD"));
        var migrationJdbc = new JdbcTemplate(migrationDs);
        assertThat(migrationJdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public'", Long.class)).isZero();
        var flyway = org.flywaydb.core.Flyway.configure().dataSource(migrationDs).locations("classpath:db/canonical").defaultSchema("public")
                .schemas("public").baselineOnMigrate(false).cleanDisabled(true).outOfOrder(false).validateOnMigrate(true).load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        migrationJdbc.execute("GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO " + app);
        migrationJdbc.execute("REVOKE ALL ON flyway_schema_history FROM " + app);
        var ds = counting(new DriverManagerDataSource(url, app, System.getenv("BATCH5B_APP_PASSWORD")));
        jdbc = new JdbcTemplate(ds);
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(ds); factory.setPackagesToScan("com.example.hotelservice");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate", "hibernate.default_schema", "public", "hibernate.jdbc.time_zone", "UTC",
                "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
        factory.afterPropertiesSet();
        em = SharedEntityManagerCreator.createSharedEntityManager(factory.getObject());
        var tm = new JpaTransactionManager(factory.getObject()); tm.setDataSource(ds);
        tx = new TransactionTemplate(tm);
        var repos = new JpaRepositoryFactory(em);
        hotels = repos.getRepository(HotelRepository.class);
        rooms = repos.getRepository(RoomTypeRepository.class);
        catalog = new AmenityCatalogService(jdbc, hotels);
        reviews = new RoomInventoryReviewService(jdbc, rooms, hotels);
        mapper = Mappers.getMapper(HotelMapper.class); mapper.setObjectMapper(json);
        var mediaMapper = Mappers.getMapper(com.example.hotelservice.MediaAsset.mapper.MediaAssetMapper.class);
        var roomMapper = Mappers.getMapper(com.example.hotelservice.Room.mapper.RoomTypeMapper.class);
        ReflectionTestUtils.setField(roomMapper, "objectMapper", json);
        ReflectionTestUtils.setField(roomMapper, "mediaAssetMapper", mediaMapper);
        ReflectionTestUtils.setField(mapper, "mediaAssetMapper", mediaMapper);
        ReflectionTestUtils.setField(mapper, "roomTypeMapper", roomMapper);
        ReflectionTestUtils.setField(mapper, "inventoryRooms", rooms);
        ReflectionTestUtils.setField(mapper, "mediaAssetRepository", repos.getRepository(com.example.hotelservice.MediaAsset.repository.MediaAssetRepository.class));
        ReflectionTestUtils.setField(mapper, "hotelPolicyRepository", repos.getRepository(com.example.hotelservice.Policy.repository.HotelPolicyRepository.class));
        ReflectionTestUtils.setField(mapper, "questionRepository", repos.getRepository(com.example.hotelservice.Question.repository.QuestionRepository.class));
        ReflectionTestUtils.setField(mapper, "hotelAmenityCategoryRepository", repos.getRepository(com.example.hotelservice.Amenity.repository.HotelAmenityCategoryRepository.class));
        ReflectionTestUtils.setField(mapper, "hotelAmenityRepository", repos.getRepository(com.example.hotelservice.Amenity.repository.HotelAmenityRepository.class));
        ReflectionTestUtils.setField(mapper, "amenityCatalog", catalog);
        var availability = new com.example.hotelservice.Room.availability.service.AvailabilityService(rooms, hotels,
                repos.getRepository(com.example.hotelservice.Room.availability.repository.RoomAvailabilityRepository.class),
                repos.getRepository(com.example.hotelservice.Room.availability.repository.RoomHoldRepository.class),
                new com.example.hotelservice.config.HoldExpiryProperties());
        var hotelService = mock(HotelService.class);
        when(hotelService.getByCities(any())).thenAnswer(c -> hotels.findByCityIdInAndIsActiveTrueAndApprovedStatus(c.getArgument(0), HotelApprovalStatus.APPROVED));
        var summaries = mock(com.example.hotelservice.Review.service.ReviewSummaryService.class);
        when(summaries.summarize(anyList())).thenAnswer(c -> ((List<UUID>) c.getArgument(0)).stream()
                .map(id -> new com.example.hotelservice.Review.dto.response.ReviewSummaryResponse(id, 0, null)).toList());
        controller = new HotelOfferController(hotelService, mapper, new HotelOfferSearchService(availability),
                mock(com.example.hotelservice.City.service.LocationService.class), mock(com.example.hotelservice.City.repository.CityRepository.class),
                catalog, summaries, new HotelOfferCandidates(rooms, repos.getRepository(com.example.hotelservice.Amenity.repository.HotelAmenityRepository.class), mapper));
    }

    @org.junit.jupiter.api.AfterEach void close() { if (factory != null) factory.destroy(); }

    record Shape(UUID city, List<UUID> hotels, Map<UUID, UUID> cheapestApproved, Set<UUID> pending, Set<UUID> inactive, Set<UUID> approved) {}

    private Shape shape(String name, int hotelCount, int roomsPerHotel, UUID admin) {
        return tx.execute(status -> {
            var city = com.example.hotelservice.City.entity.City.builder().name("Batch8D " + name).hotelCount(0).build();
            em.persist(city); em.flush();
            var category = jdbc.queryForObject("SELECT id FROM policy_category_types WHERE name='Batch8D policy'", UUID.class);
            var hotelIds = new ArrayList<UUID>(); var cheapest = new HashMap<UUID, UUID>();
            var pending = new HashSet<UUID>(); var inactive = new HashSet<UUID>(); var approved = new HashSet<UUID>();
            for (int h = 0; h < hotelCount; h++) {
                var owner = UUID.randomUUID();
                var hotel = Hotel.builder().ownerId(owner).cityId(city.getId()).name("Batch8D " + name + " " + h)
                        .address("{\"street\":\"Synthetic\",\"city\":\"Synthetic\",\"country\":\"VN\"}").businessLicenseNumber("SYN")
                        .isActive(true).approvedStatus(HotelApprovalStatus.APPROVED)
                        .accommodationType(com.example.hotelservice.Hotel.enums.AccommodationType.HOTEL).build();
                em.persist(hotel); em.flush(); hotelIds.add(hotel.getId());
                em.persist(MediaAsset.builder().targetId(hotel.getId()).targetType(MediaTargetType.HOTEL).url("https://example.invalid/h").isThumbnail(true).sortOrder(0).build());
                // Edge: a ROOM_TYPE-typed row pointing at the hotel id must never appear as hotel media.
                em.persist(MediaAsset.builder().targetId(hotel.getId()).targetType(MediaTargetType.ROOM_TYPE).url("https://example.invalid/x").isThumbnail(false).sortOrder(9).build());
                jdbc.update("INSERT INTO hotel_policies(id,hotel_id,category_id,content,is_active,created_at,updated_at) VALUES (?,?,?,?,true,now(),now())", UUID.randomUUID(), hotel.getId(), category, "Policy " + h);
                jdbc.update("INSERT INTO hotel_questions(id,hotel_id,question,answer,is_active,created_at,updated_at) VALUES (?,?,?,?,true,now(),now())", UUID.randomUUID(), hotel.getId(), "Question " + h, "Answer");
                UUID activeCategory = UUID.randomUUID(), hiddenCategory = UUID.randomUUID(), wifi = UUID.randomUUID();
                jdbc.update("INSERT INTO hotel_amenity_categories(id,hotel_id,title,is_active,created_at,updated_at) VALUES (?,?,'General',true,now(),now()),(?,?,'Hidden',false,now(),now())", activeCategory, hotel.getId(), hiddenCategory, hotel.getId());
                jdbc.update("INSERT INTO hotel_amenities(id,category_id,title,is_active,created_at) VALUES (?,?,'Wi-Fi',true,now()),(?,?,'Inactive',false,now()),(?,?,'Hidden item',true,now())",
                        wifi, activeCategory, UUID.randomUUID(), activeCategory, UUID.randomUUID(), hiddenCategory);
                var ownerActor = new AmenityCatalogService.Actor(owner, "OWNER");
                var mapping = catalog.propose(hotel.getId(), wifi, new AmenityCatalogService.Proposal(Set.of("WIFI_FREE"), 0L, "Synthetic"), ownerActor);
                catalog.review((UUID) mapping.get("id"), new AmenityCatalogService.Review(true, ((Number) mapping.get("revision")).longValue(), "Synthetic reviewed"), new AmenityCatalogService.Actor(admin, "ADMIN"));
                BigDecimal best = null;
                for (int r = 0; r < roomsPerHotel; r++) {
                    boolean isPending = r == 0, isInactive = r == 1 && roomsPerHotel >= 3;
                    var room = RoomType.builder().hotelId(hotel.getId()).name("Room " + r).basePrice(BigDecimal.valueOf(1000 + r * 10 + h))
                            .capacity("{\"adults\":2,\"children\":1}").totalRooms(2).isActive(true).build();
                    em.persist(room); em.flush();
                    if (!isPending) {
                        var proposal = reviews.propose(room.getId(), new RoomInventoryReviewService.Proposal(2, 0L, "Synthetic register"), new RoomInventoryReviewService.Actor(owner, "OWNER"));
                        reviews.review((UUID) proposal.get("id"), new RoomInventoryReviewService.Review(true, 1L, "Synthetic checked"), new RoomInventoryReviewService.Actor(admin, "ADMIN"));
                    } else {
                        reviews.propose(room.getId(), new RoomInventoryReviewService.Proposal(2, 0L, "Synthetic pending"), new RoomInventoryReviewService.Actor(owner, "OWNER"));
                    }
                    if (isInactive) { room.setIsActive(false); em.flush(); } // via the managed entity: no later stale full-row flush
                    if (isPending) pending.add(room.getId()); else if (isInactive) inactive.add(room.getId()); else {
                        approved.add(room.getId());
                        for (int m = 0; m < 2; m++)
                            em.persist(MediaAsset.builder().targetId(room.getId()).targetType(MediaTargetType.ROOM_TYPE).url("https://example.invalid/r" + m).isThumbnail(m == 0).sortOrder(m).build());
                        if (best == null || room.getBasePrice().compareTo(best) < 0) { best = room.getBasePrice(); cheapest.put(hotel.getId(), room.getId()); }
                    }
                }
                em.flush();
            }
            return new Shape(city.getId(), hotelIds, cheapest, pending, inactive, approved);
        });
    }

    private <T> int measure(String shape, String endpoint, java.util.function.Supplier<T> body, List<T> sink) {
        int count = tx.execute(status -> { em.clear(); statements.set(0); sink.add(body.get()); return statements.get(); });
        System.out.println("BATCH8D_SQL_COUNT {\"shape\":\"" + shape + "\",\"endpoint\":\"" + endpoint + "\",\"statements\":" + count + "}");
        return count;
    }

    private HotelOfferCriteria criteria(UUID city, boolean dated) {
        return new HotelOfferCriteria(city, null, null, null, dated ? IN : null, dated ? OUT : null, 1, 1, 0, null, null, null, null);
    }

    private void assertPublic(HotelResponse hotel, Shape shape) {
        assertThat(hotel.roomTypes()).isNotEmpty();
        assertThat(hotel.roomTypes()).allSatisfy(room -> {
            assertThat(shape.approved()).contains(room.id());
            assertThat(room.mediaAssets()).hasSize(2).allSatisfy(m -> assertThat(m.targetId()).isEqualTo(room.id()));
        });
        assertThat(hotel.roomTypes()).noneMatch(r -> shape.pending().contains(r.id()) || shape.inactive().contains(r.id()));
        assertThat(hotel.mediaAssets()).hasSize(1).allSatisfy(m -> assertThat(m.targetType()).isEqualTo(MediaTargetType.HOTEL));
        assertThat(hotel.policies()).extracting(p -> p.getCategory()).containsExactly("Batch8D policy");
        assertThat(hotel.faqs()).hasSize(1);
        assertThat(hotel.amenityCategories()).hasSize(1);
        assertThat(hotel.amenityCategories().get(0).getItems()).extracting(i -> i.getTitle()).containsExactly("Wi-Fi");
        assertThat(hotel.amenityCategories().get(0).getItems().get(0).getVerifiedCodes()).extracting(c -> c.code()).containsExactly("WIFI_FREE");
        var cheapestPrice = hotel.roomTypes().stream().map(r -> r.basePrice()).min(BigDecimal::compareTo).orElseThrow();
        assertThat(hotel.lowestPrice()).isEqualByComparingTo(cheapestPrice);
    }

    @Test void publicReadStatementCountsDoNotGrowWithHotelsOrRooms() {
        var admin = UUID.randomUUID();
        jdbc.update("INSERT INTO amenity_catalog(code,label,active,created_by,updated_by) VALUES ('WIFI_FREE','Wi-Fi miễn phí',true,?,?)", admin, admin);
        jdbc.update("INSERT INTO policy_category_types(id,name) VALUES (?,'Batch8D policy')", UUID.randomUUID());
        var shapes = new LinkedHashMap<String, Shape>();
        shapes.put("2x2", shape("small", 2, 2, admin));
        shapes.put("1x10", shape("detail", 1, 10, admin));
        shapes.put("3x5", shape("medium", 3, 5, admin));
        shapes.put("5x10", shape("larger", 5, 10, admin));
        // Dated availability: one approved room per larger hotel has one held unit.
        var larger = shapes.get("5x10");
        for (var hotel : larger.hotels())
            jdbc.update("INSERT INTO room_availability(id,room_type_id,date,held,booked,created_at,updated_at) VALUES (?,?,?,1,0,now(),now())", UUID.randomUUID(), larger.cheapestApproved().get(hotel), IN);

        var counts = new LinkedHashMap<String, Map<String, Integer>>();
        for (var entry : shapes.entrySet()) {
            var shape = entry.getValue(); var name = entry.getKey(); var perEndpoint = new LinkedHashMap<String, Integer>();
            var undated = new ArrayList<List<HotelOfferResponse>>(); var dated = new ArrayList<List<HotelOfferResponse>>();
            var catalogResult = new ArrayList<List<HotelResponse>>(); var detail = new ArrayList<HotelResponse>();
            perEndpoint.put("search_undated", measure(name, "search_undated", () -> controller.search(criteria(shape.city(), false)), undated));
            perEndpoint.put("search_dated", measure(name, "search_dated", () -> controller.search(criteria(shape.city(), true)), dated));
            perEndpoint.put("public_catalog", measure(name, "public_catalog", () -> mapper.toPublicHotelResponses(hotels.findByCityIdInAndIsActiveTrueAndApprovedStatus(List.of(shape.city()), HotelApprovalStatus.APPROVED)), catalogResult));
            perEndpoint.put("hotel_detail", measure(name, "hotel_detail", () -> mapper.toPublicHotelResponse(hotels.findById(shape.hotels().get(0)).orElseThrow()), detail));
            counts.put(name, perEndpoint);

            assertThat(undated.get(0)).extracting(o -> o.hotel().id()).containsExactlyInAnyOrderElementsOf(shape.hotels());
            for (var offer : undated.get(0)) {
                assertPublic(offer.hotel(), shape);
                assertThat(offer.offer().roomTypeId()).isEqualTo(shape.cheapestApproved().get(offer.hotel().id()));
                assertThat(offer.offer().availableRooms()).isNull();
            }
            assertThat(dated.get(0)).extracting(o -> o.hotel().id()).containsExactlyInAnyOrderElementsOf(shape.hotels());
            for (var offer : dated.get(0)) {
                assertThat(shape.approved()).contains(offer.offer().roomTypeId());
                assertThat(offer.offer().availableRooms()).isEqualTo(name.equals("5x10") ? 1 : 2);
            }
            assertThat(catalogResult.get(0)).extracting(HotelResponse::id).containsExactlyInAnyOrderElementsOf(shape.hotels());
            catalogResult.get(0).forEach(h -> assertPublic(h, shape));
            assertPublic(detail.get(0), shape);
        }
        // Read amplification: identical statement counts regardless of hotel/room counts.
        for (var endpoint : List.of("search_undated", "search_dated", "public_catalog"))
            assertThat(counts.values().stream().map(c -> c.get(endpoint)).distinct().toList()).as(endpoint + " " + counts).hasSize(1);
        assertThat(counts.values().stream().map(c -> c.get("hotel_detail")).distinct().toList()).as("detail " + counts).hasSize(1);
        System.out.println("BATCH8D_SQL_COUNT_SUMMARY " + counts);
        assertThat(counts.get("5x10").get("search_undated")).isLessThanOrEqualTo(14);
        assertThat(counts.get("5x10").get("search_dated")).isLessThanOrEqualTo(17);
        assertThat(counts.get("5x10").get("hotel_detail")).isLessThanOrEqualTo(12);

        // Paging over the batched search: stable order, no loss/duplication, correct totals.
        var seen = new ArrayList<UUID>(); int total = -1;
        for (int page = 0; page < 3; page++) {
            int p = page;
            var result = tx.execute(status -> { em.clear(); return controller.searchPage(criteria(larger.city(), false),
                    new HotelOfferPageCriteria(p, 2, "PRICE_ASC", null, null, null), new org.springframework.mock.web.MockHttpServletRequest()); });
            total = (int) result.totalElements();
            assertThat(result.totalPages()).isEqualTo(3);
            result.content().forEach(r -> { assertPublic(r.hotel(), larger); seen.add(r.hotel().id()); });
        }
        assertThat(total).isEqualTo(5);
        assertThat(seen).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(larger.hotels());
        var prices = new ArrayList<BigDecimal>();
        for (var id : seen) prices.add(rooms.findById(larger.cheapestApproved().get(id)).orElseThrow().getBasePrice());
        assertThat(prices).isSortedAccordingTo(Comparator.naturalOrder());
    }
}
