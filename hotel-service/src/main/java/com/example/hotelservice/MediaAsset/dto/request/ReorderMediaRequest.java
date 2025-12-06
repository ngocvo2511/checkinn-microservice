package com.example.hotelservice.MediaAsset.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class ReorderMediaRequest {
    @NotEmpty(message = "Reorder items cannot be empty")
    private List<Item> items;

    @Data
    public static class Item {

        @NotBlank(message = "mediaId is required")
        private String mediaId;

        @NotNull(message = "sortOrder is required")
        @Min(0)
        private Integer sortOrder;
    }
}
