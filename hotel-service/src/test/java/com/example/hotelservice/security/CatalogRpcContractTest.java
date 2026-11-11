package com.example.hotelservice.security;

import com.example.hotelservice.grpc.*;
import com.example.hotelservice.Hotel.entity.Hotel;
import com.example.hotelservice.Hotel.enums.HotelApprovalStatus;
import com.example.hotelservice.Hotel.service.HotelService;
import com.example.hotelservice.Room.entity.RoomType;
import com.example.hotelservice.Room.service.RoomTypeService;
import com.example.hotelservice.MediaAsset.service.MediaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.*;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
@SuppressWarnings({"unchecked","rawtypes"})
class CatalogRpcContractTest {
 final HotelService hotels=mock(HotelService.class);
 final RoomTypeService rooms=mock(RoomTypeService.class);
 final MediaService media=mock(MediaService.class);
 final HotelGrpcServiceImpl rpc=new HotelGrpcServiceImpl(hotels,rooms,media,new ObjectMapper());
 final UUID hotel=UUID.randomUUID(),id=UUID.randomUUID();
 RoomType room(){return RoomType.builder().id(id).hotelId(hotel).name("Synthetic room").isActive(true).totalRooms(3)
  .basePrice(new BigDecimal("125000.50")).capacity("{\"adults\":2,\"children\":0}").amenities("[]").build();}
 Hotel hotel(){return Hotel.builder().id(hotel).ownerId(UUID.randomUUID()).name("Synthetic hotel").isActive(true).approvedStatus(HotelApprovalStatus.APPROVED).build();}
 void failure(StreamObserver observer,Status.Code code){
  verify(observer).onError(argThat(error->Status.fromThrowable((Throwable)error).getCode()==code));verify(observer,never()).onNext(any());verify(observer,never()).onCompleted();
 }
 @ParameterizedTest @ValueSource(strings={"{\"adults\":2.5,\"children\":0}","{\"adults\":\"2\",\"children\":0}","{\"adults\":true,\"children\":0}","{}","{\"adults\":-1,\"children\":0}","{\"adults\":2147483648,\"children\":0}","{\"adults\":2,\"children\":0.5}","[]"})
 void storedCapacityCannotBeCoercedIntoInventedOccupancy(String json){
  var row=room();row.setCapacity(json);when(rooms.getPublicById(id)).thenReturn(row);var observer=mock(StreamObserver.class);
  rpc.getRoomTypeById(GetRoomTypeByIdRequest.newBuilder().setRoomTypeId(id.toString()).build(),observer);failure(observer,Status.Code.DATA_LOSS);
 }
 @Test void absentOptionalAmenitiesWrittenAsJsonNullRemainReadable(){
  var row=room();row.setAmenities("null");when(rooms.getPublicById(id)).thenReturn(row);StreamObserver<GetRoomTypeByIdResponse> observer=mock(StreamObserver.class);
  rpc.getRoomTypeById(GetRoomTypeByIdRequest.newBuilder().setRoomTypeId(id.toString()).build(),observer);
  verify(observer).onNext(argThat(result->result.getRoomType().getAmenitiesCount()==0&&result.getRoomType().getCapacity().getAdults()==2));verify(observer).onCompleted();verify(observer,never()).onError(any());
 }
 @Test void structuredAmenitiesAreNotSilentlyConvertedToStrings(){
  var row=room();row.setAmenities("[7]");when(rooms.getPublicById(id)).thenReturn(row);var observer=mock(StreamObserver.class);
  rpc.getRoomTypeById(GetRoomTypeByIdRequest.newBuilder().setRoomTypeId(id.toString()).build(),observer);failure(observer,Status.Code.DATA_LOSS);
 }
 @ParameterizedTest @ValueSource(strings={"null","0","-1","1e10000","1e-10000"})
 void invalidOrUnrepresentableStoredPriceNeverBecomesAQuote(String price){
  var row=room();row.setBasePrice(price.equals("null")?null:new BigDecimal(price));when(rooms.getPublicById(id)).thenReturn(row);var observer=mock(StreamObserver.class);
  rpc.getRoomTypeById(GetRoomTypeByIdRequest.newBuilder().setRoomTypeId(id.toString()).build(),observer);failure(observer,Status.Code.DATA_LOSS);
 }
 @ParameterizedTest @ValueSource(strings={"null","0","-1"})
 void missingInventoryCannotBecomeAnAssumedRoomTotal(String quantity){
  var row=room();row.setTotalRooms(quantity.equals("null")?null:Integer.valueOf(quantity));when(hotels.getPublishedById(hotel)).thenReturn(hotel());when(rooms.getPublicByHotel(hotel)).thenReturn(List.of(row));var observer=mock(StreamObserver.class);
  rpc.getHotelById(GetHotelByIdRequest.newBuilder().setHotelId(hotel.toString()).build(),observer);failure(observer,Status.Code.FAILED_PRECONDITION);
 }
 @Test void abbreviatedUuidIsRejectedBeforeDataAccess(){
  var observer=mock(StreamObserver.class);rpc.getHotelById(GetHotelByIdRequest.newBuilder().setHotelId("1-1-1-1-1").build(),observer);
  failure(observer,Status.Code.INVALID_ARGUMENT);verifyNoInteractions(hotels,rooms,media);
 }
 @ParameterizedTest @ValueSource(strings={"hotel","rooms","room"})
 void storageOutagesRemainUnavailableAndDoNotLogPrivateCauses(String method,CapturedOutput output){
  String marker="synthetic-private-catalog-storage-error";var error=new org.springframework.dao.DataAccessResourceFailureException(marker);
  when(hotels.getPublishedById(hotel)).thenThrow(error);when(rooms.getPublicByHotel(hotel)).thenThrow(error);when(rooms.getPublicById(id)).thenThrow(error);var observer=mock(StreamObserver.class);
  if(method.equals("hotel"))rpc.getHotelById(GetHotelByIdRequest.newBuilder().setHotelId(hotel.toString()).build(),observer);
  if(method.equals("rooms"))rpc.getRoomTypesByHotel(GetRoomTypesByHotelRequest.newBuilder().setHotelId(hotel.toString()).build(),observer);
  if(method.equals("room"))rpc.getRoomTypeById(GetRoomTypeByIdRequest.newBuilder().setRoomTypeId(id.toString()).build(),observer);
  failure(observer,Status.Code.UNAVAILABLE);assertThat(output.getAll()).doesNotContain(marker);
 }
 @Test void roomListDoesNotLoadMediaOncePerRoom(){
  var rows=new ArrayList<RoomType>();for(int i=0;i<3;i++){var row=room();row.setId(UUID.randomUUID());rows.add(row);}
  when(rooms.getPublicByHotel(hotel)).thenReturn(rows);StreamObserver<GetRoomTypesByHotelResponse> observer=mock(StreamObserver.class);
  rpc.getRoomTypesByHotel(GetRoomTypesByHotelRequest.newBuilder().setHotelId(hotel.toString()).build(),observer);
  verify(observer).onNext(argThat(result->result.getRoomTypesCount()==3));verify(media,never()).getByTarget(any(),any());
 }
 @Test void unknownCapacityUsesAbsentMessageRatherThanFabricatedZeros(){
  var row=room();row.setCapacity("null");when(rooms.getPublicById(id)).thenReturn(row);StreamObserver<GetRoomTypeByIdResponse> observer=mock(StreamObserver.class);
  rpc.getRoomTypeById(GetRoomTypeByIdRequest.newBuilder().setRoomTypeId(id.toString()).build(),observer);
  verify(observer).onNext(argThat(result->!result.getRoomType().hasCapacity()));verify(observer).onCompleted();
 }
 @org.junit.jupiter.params.ParameterizedTest @ValueSource(booleans={false,true})
 void newWritersRejectNullAmenityElements(boolean create){
  var values=new HashMap<String,Object>();values.put("amenities",Arrays.asList((String)null));
  values.put("hotelId",hotel.toString());values.put("name","Synthetic room");values.put("basePrice",125000);values.put("roomAmount",3);values.put("capacity",Map.of("adults",2,"children",0));
  var mapper=new ObjectMapper().configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false);
  Object request=create?mapper.convertValue(values,com.example.hotelservice.Room.dto.request.RoomTypeCreateRequest.class):mapper.convertValue(values,com.example.hotelservice.Room.dto.request.RoomTypeUpdateRequest.class);
  assertThatThrownBy(()->com.example.hotelservice.validation.InputValidation.require(request)).isInstanceOf(com.example.hotelservice.validation.InputValidation.InvalidInput.class);
 }
 @Test void batchMediaCannotIntroduceAnImageOfAnotherRoom(){
  when(rooms.getPublicByHotel(hotel)).thenReturn(List.of(room()));
  when(media.getPublicRoomMediaByHotel(hotel)).thenReturn(List.of(com.example.hotelservice.MediaAsset.entity.MediaAsset.builder().id(UUID.randomUUID()).targetId(UUID.randomUUID()).targetType(com.example.hotelservice.MediaAsset.enums.MediaTargetType.ROOM_TYPE).build()));
  var observer=mock(StreamObserver.class);rpc.getRoomTypesByHotel(GetRoomTypesByHotelRequest.newBuilder().setHotelId(hotel.toString()).build(),observer);failure(observer,Status.Code.DATA_LOSS);
 }

}
