package com.example.hotelservice.Amenity.service;

import com.example.hotelservice.Hotel.repository.HotelRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@EnabledIfEnvironmentVariable(named="CHECKINN_RANGE_TEST",matches="true")
class AmenityCatalogQueryIntegrationTest {
    static class CountingJdbc extends JdbcTemplate {
        int reads;
        CountingJdbc(javax.sql.DataSource source) { super(source); }
        @Override public void query(String sql,RowCallbackHandler callback,Object... args) {
            reads++;super.query(sql,callback,args);
        }
    }
    @Test void reviewedCodeMatchingHasBoundedSqlAndPreservesAllGuards() {
        String url=System.getenv("CHECKINN_RANGE_DB_URL");
        assertThat(url).startsWith("jdbc:postgresql://127.0.0.1:").endsWith("/checkinn_hotel");
        var source=new DriverManagerDataSource(url,"checkinn_test",System.getenv("CHECKINN_RANGE_DB_PASSWORD"));
        var jdbc=new CountingJdbc(source);
        new TransactionTemplate(new DataSourceTransactionManager(source)).executeWithoutResult(tx->{
            tx.setRollbackOnly();
            assertThat(jdbc.queryForMap("SELECT current_database() db,current_user usr"))
                    .containsEntry("db","checkinn_hotel").containsEntry("usr","checkinn_test");
            UUID actor=UUID.randomUUID();
            var hotels=List.of(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
            String a="TEST_"+UUID.randomUUID().toString().replace("-","").toUpperCase(),b=a+"_B";
            for(String code:List.of(a,b))jdbc.update("INSERT INTO amenity_catalog(code,label,active,created_by,updated_by) VALUES (?, 'Synthetic code',true,?,?)",code,actor,actor);
            var items=new ArrayList<UUID>();
            for(int i=0;i<3;i++) {
                UUID hotel=hotels.get(i),category=UUID.randomUUID(),item=UUID.randomUUID(),request=UUID.randomUUID();items.add(item);
                jdbc.update("INSERT INTO hotel_amenity_categories(id,hotel_id,title,is_active,created_at,updated_at) VALUES (?,?,'Synthetic query category',true,now(),now())",category,hotel);
                jdbc.update("INSERT INTO hotel_amenities(id,category_id,title,is_active,created_at) VALUES (?,?,'Synthetic raw',true,now())",item,category);
                jdbc.update("INSERT INTO amenity_mapping_requests(id,hotel_id,amenity_id,title_snapshot,requested_by,status) VALUES (?,?,?,'Synthetic raw',?,'APPROVED')",request,hotel,item,actor);
                jdbc.update("INSERT INTO amenity_mapping_states(amenity_id,hotel_id,approved_request_id) VALUES (?,?,?)",item,hotel,request);
                for(String code:i==0?List.of(a,b):i==1?List.of(a):List.of(b))
                    jdbc.update("INSERT INTO amenity_mapping_request_codes(request_id,code) VALUES (?,?)",request,code);
            }
            var service=new AmenityCatalogService(jdbc,mock(HotelRepository.class));
            jdbc.reads=0;
            assertThat(service.matchingHotels(hotels,List.of(a,b,a))).containsExactly(hotels.getFirst());
            assertThat(jdbc.reads).as("reviewed code matching SQL for three hotels").isEqualTo(1);
            var large=new ArrayList<>(hotels);
            while(large.size()<501)large.add(UUID.randomUUID());
            jdbc.reads=0;
            assertThat(service.matchingHotels(large,List.of(a,b))).containsExactly(hotels.getFirst());
            assertThat(jdbc.reads).as("bounded 500-property chunks").isEqualTo(2);
            jdbc.update("UPDATE hotel_amenities SET title='Changed raw label' WHERE id=?",items.getFirst());
            assertThat(service.matchingHotels(hotels,List.of(a,b))).isEmpty();
            jdbc.update("UPDATE hotel_amenities SET title='Synthetic raw',is_active=false WHERE id=?",items.getFirst());
            assertThat(service.matchingHotels(hotels,List.of(a,b))).isEmpty();
            jdbc.update("UPDATE hotel_amenities SET is_active=true WHERE id=?",items.getFirst());
            jdbc.update("UPDATE amenity_catalog SET active=false WHERE code=?",b);
            assertThat(service.matchingHotels(hotels,List.of(a,b))).isEmpty();
            jdbc.reads=0;
            assertThat(service.matchingHotels(hotels,List.of())).containsExactlyInAnyOrderElementsOf(hotels);
            assertThat(service.matchingHotels(List.of(),List.of(a))).isEmpty();
            assertThat(jdbc.reads).isZero();
        });
    }
}
