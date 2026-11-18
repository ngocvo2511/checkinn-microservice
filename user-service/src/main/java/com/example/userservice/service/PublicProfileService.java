package com.example.userservice.service;

import com.example.userservice.dto.PublicProfileResponse;
import com.example.userservice.model.User;
import com.example.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/** Public author display data for community content. */
@Service
@RequiredArgsConstructor
public class PublicProfileService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<PublicProfileResponse> findPublicProfiles(Collection<java.util.UUID> ids) {
        return userRepository.findActiveWithProfileByIdIn(ids).stream()
                .map(user -> new PublicProfileResponse(user.getId(), displayName(user), null))
                .toList();
    }

    private static String displayName(User user) {
        return user.getProfile() == null ? null : shortenFullName(user.getProfile().getFullName());
    }

    /**
     * Vietnamese names are family-name first: "Nguyễn Văn An" becomes "An N." (given name plus family initial).
     * A single word is returned unchanged; a blank name returns null.
     */
    static String shortenFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return null;
        }
        String[] words = fullName.strip().split("\\s+");
        if (words.length == 1) {
            return words[0];
        }
        String familyInitial = words[0].substring(0, words[0].offsetByCodePoints(0, 1)).toUpperCase(java.util.Locale.ROOT);
        return words[words.length - 1] + " " + familyInitial + ".";
    }
}
