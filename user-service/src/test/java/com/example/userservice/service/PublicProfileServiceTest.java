package com.example.userservice.service;

import com.example.userservice.dto.PublicProfileResponse;
import com.example.userservice.model.User;
import com.example.userservice.model.UserProfile;
import com.example.userservice.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PublicProfileServiceTest {

    @Test
    void shortensVietnameseFullNames() {
        assertThat(PublicProfileService.shortenFullName("Nguyễn Văn An")).isEqualTo("An N.");
        assertThat(PublicProfileService.shortenFullName("  trần   thị  bích ")).isEqualTo("bích T.");
        assertThat(PublicProfileService.shortenFullName("Đặng Minh")).isEqualTo("Minh Đ.");
        assertThat(PublicProfileService.shortenFullName("Madonna")).isEqualTo("Madonna");
        assertThat(PublicProfileService.shortenFullName("   ")).isNull();
        assertThat(PublicProfileService.shortenFullName(null)).isNull();
    }

    @Test
    void mapsUsersToPublicFieldsOnly() {
        UserRepository repository = mock(UserRepository.class);
        User withProfile = User.builder().id(UUID.randomUUID()).username("an.nguyen").email("private@example.invalid").build();
        withProfile.setProfile(UserProfile.builder().fullName("Nguyễn Văn An").phone("0900000000").address("private").build());
        User withoutProfile = User.builder().id(UUID.randomUUID()).username("no.profile").build();
        Set<UUID> ids = Set.of(withProfile.getId(), withoutProfile.getId());
        when(repository.findActiveWithProfileByIdIn(ids)).thenReturn(List.of(withProfile, withoutProfile));

        List<PublicProfileResponse> result = new PublicProfileService(repository).findPublicProfiles(ids);

        assertThat(result).containsExactlyInAnyOrder(
                new PublicProfileResponse(withProfile.getId(), "An N.", null),
                new PublicProfileResponse(withoutProfile.getId(), null, null));
    }
}
