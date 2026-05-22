package com.example.hotelservice.Policy.controller;

import com.example.hotelservice.Hotel.dto.response.HotelResponse;
import com.example.hotelservice.Hotel.mapper.HotelMapper;
import com.example.hotelservice.Hotel.service.HotelService;
import com.example.hotelservice.Policy.dto.request.PolicyRequest;
import com.example.hotelservice.Policy.dto.response.PolicyCategoryResponse;
import com.example.hotelservice.Policy.service.PolicyCategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/hotels")
@RequiredArgsConstructor
@Slf4j
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
		log.info("[POLICY_CONTROLLER_GET_CATEGORIES] Fetch available policy categories request received");
		var categories = policyCategoryService.getAvailableCategories();
		log.info("[POLICY_CONTROLLER_GET_CATEGORIES] Fetch available policy categories success - count: {}", categories.size());
		return ResponseEntity.ok(categories);
	}

	// 1) Cập nhật toàn bộ policies
	@PutMapping("/{hotelId}/policies")
	public ResponseEntity<HotelResponse> updatePolicies(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID hotelId,
			@RequestBody List<PolicyRequest> policies
	) {
		UUID ownerId = getOwnerId(jwt.getSubject());
		log.info("[POLICY_CONTROLLER_UPDATE] Update policies request received - hotelId: {}, ownerId: {}, policiesCount: {}",
				hotelId, ownerId, policies != null ? policies.size() : 0);
		var updated = hotelService.updatePolicies(hotelId, policies, ownerId);
		log.info("[POLICY_CONTROLLER_UPDATE] Update policies success - hotelId: {}, ownerId: {}", hotelId, ownerId);
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
		log.info("[POLICY_CONTROLLER_ADD] Add policy request received - hotelId: {}, ownerId: {}", hotelId, ownerId);
		var updated = hotelService.addPolicy(hotelId, request, ownerId);
		log.info("[POLICY_CONTROLLER_ADD] Add policy success - hotelId: {}, ownerId: {}", hotelId, ownerId);
		return ResponseEntity.ok(hotelMapper.toHotelResponse(updated));
	}

	// 3) Xóa tất cả policies
	@DeleteMapping("/{hotelId}/policies")
	public ResponseEntity<HotelResponse> clearPolicies(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID hotelId
	) {
		UUID ownerId = getOwnerId(jwt.getSubject());
		log.info("[POLICY_CONTROLLER_CLEAR] Clear policies request received - hotelId: {}, ownerId: {}", hotelId, ownerId);
		var updated = hotelService.clearPolicies(hotelId, ownerId);
		log.info("[POLICY_CONTROLLER_CLEAR] Clear policies success - hotelId: {}, ownerId: {}", hotelId, ownerId);
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
		log.info("[POLICY_CONTROLLER_REMOVE] Remove policy request received - hotelId: {}, ownerId: {}, policyId: {}",
				hotelId, ownerId, policyId);
		var updated = hotelService.removePolicy(hotelId, policyId, ownerId);
		log.info("[POLICY_CONTROLLER_REMOVE] Remove policy success - hotelId: {}, ownerId: {}, policyId: {}",
				hotelId, ownerId, policyId);
		return ResponseEntity.ok(hotelMapper.toHotelResponse(updated));
	}
}
