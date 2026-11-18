package com.example.travelsocialservice.service;

import com.example.travelsocialservice.web.dto.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static com.example.travelsocialservice.service.Fixtures.assertApiError;
import static org.assertj.core.api.Assertions.assertThat;

class PagingAndCursorTest {

    @Test
    void cursorRoundTripsMicrosecondTimestamps() {
        KeysetCursor cursor = new KeysetCursor(Instant.parse("2026-11-12T10:00:00.123456Z"), UUID.randomUUID());
        assertThat(KeysetCursor.decode(cursor.encode())).isEqualTo(cursor);
    }

    @Test
    void blankCursorMeansFirstPageAndMalformedCursorIsRejected() {
        assertThat(KeysetCursor.decode(null)).isNull();
        assertThat(KeysetCursor.decode(" ")).isNull();
        for (String raw : List.of("%%%", encode("no-separator"), encode("not-a-time|" + UUID.randomUUID()),
                encode("2026-11-12T10:00:00Z|not-a-uuid"))) {
            assertApiError(() -> KeysetCursor.decode(raw), HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    void pageDropsTheLookaheadRowAndPointsAtTheLastReturnedItem() {
        List<Integer> rows = List.of(1, 2, 3);
        UUID id = UUID.randomUUID();
        PageResponse<String> page = Paging.page(rows, 2, items -> items.stream().map(String::valueOf).toList(),
                row -> new KeysetCursor(Instant.ofEpochSecond(row), id));

        assertThat(page.items()).containsExactly("1", "2");
        assertThat(KeysetCursor.decode(page.nextCursor())).isEqualTo(new KeysetCursor(Instant.ofEpochSecond(2), id));
        assertThat(Paging.page(List.of(1, 2), 2, items -> items, row -> null).nextCursor()).isNull();
    }

    @Test
    void limitDefaultsAndIsBounded() {
        assertThat(Paging.limit(null)).isEqualTo(20);
        assertThat(Paging.limit(50)).isEqualTo(50);
        assertApiError(() -> Paging.limit(0), HttpStatus.BAD_REQUEST);
        assertApiError(() -> Paging.limit(51), HttpStatus.BAD_REQUEST);
    }

    @Test
    void nowIsTruncatedToMicrosecondsSoItMatchesTheStoredValue() {
        Clock nanos = Clock.fixed(Instant.parse("2026-11-12T10:00:00.123456789Z"), ZoneOffset.UTC);
        assertThat(Paging.now(nanos)).isEqualTo(Instant.parse("2026-11-12T10:00:00.123456Z"));
    }

    @Test
    void likePatternEscapesWildcards() {
        assertThat(Paging.likePattern("50%_off!")).isEqualTo("%50!%!_off!!%");
    }

    @Test
    void imageTypeIsDetectedFromLeadingBytes() {
        assertThat(ImageType.detect(Fixtures.jpeg())).isEqualTo(ImageType.JPEG);
        assertThat(ImageType.detect(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})).isEqualTo(ImageType.PNG);
        assertThat(ImageType.detect("RIFF____WEBPVP8 ".getBytes(StandardCharsets.US_ASCII))).isEqualTo(ImageType.WEBP);
        assertThat(ImageType.detect("<svg onload=alert(1)>".getBytes(StandardCharsets.US_ASCII))).isNull();
        assertThat(ImageType.detect(new byte[]{(byte) 0xFF})).isNull();
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
