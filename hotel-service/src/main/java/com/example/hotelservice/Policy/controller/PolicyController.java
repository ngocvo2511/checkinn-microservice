package com.example.hotelservice.Policy.controller;

import com.example.hotelservice.Hotel.dto.response.HotelResponse;
import com.example.hotelservice.Hotel.mapper.HotelMapper;
import com.example.hotelservice.Hotel.service.HotelService;
import com.example.hotelservice.Policy.dto.request.PolicyRequest;
import com.example.hotelservice.Policy.dto.response.PolicyCategoryResponse;
import com.example.hotelservice.Policy.service.PolicyCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/hotels")
@RequiredArgsConstructor
public class PolicyController {

	private final HotelService hotelService;
	private final HotelMapper hotelMapper;
	private final PolicyCategoryService policyCategoryService;

	private UUID getOwnerId(String header) {
		return UUID.fromString(header);
	}

	// 0) Danh sách danh mục policy có sẵn
	@GetMapping("/policies/categories")
	public ResponseEntity<List<PolicyCategoryResponse>> getAvailablePolicyCategories() {
		return ResponseEntity.ok(policyCategoryService.getAvailableCategories());
	}

	// 1) Cập nhật toàn bộ policies
	@PutMapping("/{hotelId}/policies")
	public ResponseEntity<HotelResponse> updatePolicies(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID hotelId,
			@RequestBody List<PolicyRequest> policies
	) {
		UUID ownerId = getOwnerId(jwt.getSubject());
		var updated = hotelService.updatePolicies(hotelId, policies, ownerId);
		return ResponseEntity.ok(hotelMapper.toHotelResponse(updated));
	}

	// 2) Thêm một policy
	@PostMapping("/{hotelId}/policies")
	public ResponseEntity<HotelResponse> addPolicy(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID hotelId,
			@RequestBody PolicyRequest request
	) {
		UUID ownerId = getOwnerId(jwt.getSubject());
		var updated = hotelService.addPolicy(hotelId, request, ownerId);
		return ResponseEntity.ok(hotelMapper.toHotelResponse(updated));
	}

	// 3) Xóa tất cả policies
	@DeleteMapping("/{hotelId}/policies")
	public ResponseEntity<HotelResponse> clearPolicies(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID hotelId
	) {
		UUID ownerId = getOwnerId(jwt.getSubject());
		var updated = hotelService.clearPolicies(hotelId, ownerId);
		return ResponseEntity.ok(hotelMapper.toHotelResponse(updated));
	}

	// 4) Xóa 1 policy theo ID
	@DeleteMapping("/{hotelId}/policies/{policyId}")
	public ResponseEntity<HotelResponse> removePolicy(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID hotelId,
			@PathVariable UUID policyId
	) {
		UUID ownerId = getOwnerId(jwt.getSubject());
		var updated = hotelService.removePolicy(hotelId, policyId, ownerId);
		return ResponseEntity.ok(hotelMapper.toHotelResponse(updated));
	}
}
