package com.example.hotelservice.MediaAsset.mapper;

import com.example.hotelservice.MediaAsset.dto.request.MediaUploadRequest;
import com.example.hotelservice.MediaAsset.dto.response.MediaAssetResponse;
import com.example.hotelservice.MediaAsset.entity.MediaAsset;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface MediaAssetMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    MediaAsset toMediaAsset(MediaUploadRequest request);

    MediaAssetResponse toMediaAssetResponse(MediaAsset entity);
}