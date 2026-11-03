package com.example.hotelservice.security;

import com.example.hotelservice.MediaAsset.controller.MediaAssetController;
import com.example.hotelservice.MediaAsset.service.*;
import com.example.hotelservice.MediaAsset.repository.MediaAssetRepository;
import com.example.hotelservice.MediaAsset.entity.MediaAsset;
import com.example.hotelservice.MediaAsset.enums.MediaTargetType;
import com.example.hotelservice.Hotel.repository.HotelRepository;
import com.example.hotelservice.Room.repository.RoomTypeRepository;
import com.example.hotelservice.Room.entity.RoomType;
import com.example.hotelservice.config.SecurityConfig;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value=MediaAssetController.class, properties={"spring.cloud.config.enabled=false", "spring.cloud.discovery.enabled=false",
 "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration"})
@Import({SecurityConfig.class, MediaServiceImpl.class})
class MediaAuthorizationTest {
    static final UUID HOTEL=UUID.randomUUID(), ROOM=UUID.randomUUID(), MEDIA=UUID.randomUUID(), ACTOR=UUID.randomUUID();
    @Autowired MockMvc mvc;
    @MockBean JwtDecoder decoder;
    @MockBean TokenRevocationService revocation;
    @MockBean MediaAssetRepository assets;
    @MockBean MediaClient storage;
    @MockBean HotelRepository hotels;
    @MockBean RoomTypeRepository rooms;
    @BeforeEach void setup() {
        when(decoder.decode(anyString())).thenAnswer(call -> Jwt.withTokenValue(call.getArgument(0))
            .header("alg","HS256").subject(ACTOR.toString()).claim("role", "admin".equals(call.getArgument(0))?"ADMIN":"OWNER").build());
        when(assets.findById(MEDIA)).thenReturn(Optional.of(MediaAsset.builder().id(MEDIA).targetId(ROOM).targetType(MediaTargetType.ROOM_TYPE).sortOrder(0).url("https://storage.invalid/synthetic.png").build()));
        when(rooms.findById(ROOM)).thenReturn(Optional.of(RoomType.builder().id(ROOM).hotelId(HOTEL).build()));
        when(assets.findByTargetIdAndTargetType(any(),any())).thenReturn(List.of());
        when(assets.save(any())).thenAnswer(call -> call.getArgument(0));
        when(storage.upload(any(),any(),any())).thenReturn("https://storage.invalid/synthetic.png");
    }
    @Test void anonymousDeleteDeniedBeforeStorage() throws Exception {
        mvc.perform(delete("/medias/"+MEDIA)).andExpect(status().isUnauthorized()); verifyNoInteractions(storage);
    }
    @Test void unrelatedOwnerCannotDeleteRoomMedia() throws Exception {
        mvc.perform(delete("/medias/"+MEDIA).header("Authorization","Bearer owner").header("X-OWNER-ID",UUID.randomUUID()))
            .andExpect(status().isForbidden()); verifyNoInteractions(storage); verify(assets,never()).delete(any());
    }
    @Test void unrelatedOwnerCannotChangeThumbnail() throws Exception {
        mvc.perform(patch("/medias/"+MEDIA+"/thumbnail").header("Authorization","Bearer owner"))
            .andExpect(status().isForbidden()); verify(assets,never()).saveAll(any());
    }
    @Test void unrelatedOwnerCannotUploadHotelMedia() throws Exception {
        mvc.perform(multipart("/medias").file(new MockMultipartFile("file","image.png","image/png",new byte[]{1}))
            .param("targetId",HOTEL.toString()).param("targetType","HOTEL").header("Authorization","Bearer owner"))
            .andExpect(status().isForbidden()); verifyNoInteractions(storage);
    }
    @Test void realOwnerCanDeleteRoomMedia() throws Exception {
        when(hotels.existsByIdAndOwnerId(HOTEL,ACTOR)).thenReturn(true);
        when(storage.deleteByUrl(any())).thenReturn(true);
        mvc.perform(delete("/medias/"+MEDIA).header("Authorization","Bearer owner")).andExpect(status().isOk());
        verify(storage).deleteByUrl("https://storage.invalid/synthetic.png");
    }
    @Test void adminCanChangeThumbnail() throws Exception {
        mvc.perform(patch("/medias/"+MEDIA+"/thumbnail").header("Authorization","Bearer admin")).andExpect(status().isOk());
        verify(assets).saveAll(any());
    }
    @Test void publicListingStillAvailable() throws Exception {
        when(hotels.findById(HOTEL)).thenReturn(Optional.of(com.example.hotelservice.Hotel.entity.Hotel.builder()
                .isActive(true).approvedStatus(com.example.hotelservice.Hotel.enums.HotelApprovalStatus.APPROVED).build()));
        mvc.perform(get("/medias").param("targetId",HOTEL.toString()).param("targetType","HOTEL")).andExpect(status().isOk());
    }
    @Test void failedStorageDeleteDoesNotDeleteMetadata() throws Exception {
        when(hotels.existsByIdAndOwnerId(HOTEL,ACTOR)).thenReturn(true);
        when(storage.deleteByUrl(any())).thenReturn(false);
        mvc.perform(delete("/medias/"+MEDIA).header("Authorization","Bearer owner")).andExpect(status().isBadGateway());
        verify(assets,never()).delete(any());
    }
    @Test void hiddenHotelMediaCannotBeReadPubliclyButOwnerCanPreview() throws Exception {
        when(hotels.findById(HOTEL)).thenReturn(Optional.of(com.example.hotelservice.Hotel.entity.Hotel.builder().isActive(false).build()));
        when(hotels.existsByIdAndOwnerId(HOTEL,ACTOR)).thenReturn(true);
        mvc.perform(get("/medias").param("targetId",HOTEL.toString()).param("targetType","HOTEL")).andExpect(status().isNotFound());
        mvc.perform(get("/medias/preview").param("targetId",HOTEL.toString()).param("targetType","HOTEL")).andExpect(status().isUnauthorized());
        mvc.perform(get("/medias/preview").param("targetId",HOTEL.toString()).param("targetType","HOTEL").header("Authorization","Bearer owner")).andExpect(status().isOk());
        verifyNoInteractions(storage);
    }

}
