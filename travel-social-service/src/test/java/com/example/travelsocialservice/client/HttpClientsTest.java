package com.example.travelsocialservice.client;

import com.example.travelsocialservice.client.AuthorClient.Author;
import com.example.travelsocialservice.client.PlaceClient.PlaceRef;
import com.example.travelsocialservice.config.SocialProperties;
import com.example.travelsocialservice.security.ServiceTokenProvider;
import com.example.travelsocialservice.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class HttpClientsTest {

    static final SocialProperties PROPERTIES = new SocialProperties("http://place.invalid", "http://user.invalid",
            new SocialProperties.Client(Duration.ofSeconds(2), Duration.ofSeconds(3), Duration.ofMinutes(5)),
            null, null, null);

    private MockRestServiceServer server;
    private RestClient.Builder builder;
    private ValueOperations<String, String> cache;
    private StringRedisTemplate redis;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        redis = mock(StringRedisTemplate.class);
        cache = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(cache);
    }

    private PlaceClient placeClient() {
        return new PlaceClient(builder, PROPERTIES);
    }

    private AuthorClient authorClient() {
        ServiceTokenProvider tokens = mock(ServiceTokenProvider.class);
        when(tokens.authorization()).thenReturn("Bearer service-token");
        return new AuthorClient(builder, PROPERTIES, tokens, redis);
    }

    @Test
    void knownPlaceIsReturnedAndUnknownPlaceIsEmpty() {
        UUID known = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        server.expect(requestTo("http://place.invalid/places/batch?ids=" + known))
                .andRespond(withSuccess("[{\"id\":\"" + known + "\",\"name\":\"Cầu Rồng\",\"category\":\"ATTRACTION\","
                        + "\"provinceName\":\"Đà Nẵng\",\"googlePlaceId\":\"ignored\"}]", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://place.invalid/places/batch?ids=" + unknown))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        PlaceClient client = placeClient();

        assertThat(client.require(known)).contains(new PlaceRef(known, "Cầu Rồng", "ATTRACTION", "Đà Nẵng"));
        assertThat(client.require(unknown)).isEmpty();
    }

    @Test
    void publishingFailsButRenderingContinuesWhenThePlaceCatalogIsDown() {
        server.expect(org.springframework.test.web.client.ExpectedCount.times(2), requestTo(org.hamcrest.Matchers.startsWith("http://place.invalid/places/batch")))
                .andRespond(withServerError());
        PlaceClient client = placeClient();

        assertThatThrownBy(() -> client.require(UUID.randomUUID())).isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(client.findAll(List.of(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void authorsAreFetchedWithTheServiceTokenAndCached() {
        UUID known = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        when(cache.multiGet(any())).thenReturn(Arrays.asList(null, null));
        server.expect(requestTo("http://user.invalid/internal/users/public-profiles"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer service-token"))
                .andExpect(jsonPath("$.ids.length()").value(2))
                .andRespond(withSuccess("[{\"id\":\"" + known + "\",\"displayName\":\"An N.\",\"avatarUrl\":null}]", MediaType.APPLICATION_JSON));

        Map<UUID, Author> authors = authorClient().findAll(List.of(known, unknown, known));

        assertThat(authors.get(known)).isEqualTo(new Author(known, "An N.", null));
        assertThat(authors.get(unknown)).isEqualTo(Author.placeholder(unknown));
        verify(cache).set(AuthorClient.CACHE_PREFIX + known, "An N.", Duration.ofMinutes(5));
        verify(cache, never()).set(eq(AuthorClient.CACHE_PREFIX + unknown), anyString(), any(Duration.class));
        server.verify();
    }

    @Test
    void cachedAuthorsSkipTheCall() {
        UUID cached = UUID.randomUUID();
        UUID cachedWithoutName = UUID.randomUUID();
        when(cache.multiGet(any())).thenReturn(Arrays.asList("An N.", ""));

        Map<UUID, Author> authors = authorClient().findAll(List.of(cached, cachedWithoutName));

        assertThat(authors.get(cached).displayName()).isEqualTo("An N.");
        assertThat(authors.get(cachedWithoutName).displayName()).isNull();
        server.verify();
    }

    @Test
    void authorLookupFailuresAndCacheFailuresFallBackToPlaceholders() {
        UUID author = UUID.randomUUID();
        when(cache.multiGet(any())).thenThrow(new IllegalStateException("redis down"));
        server.expect(requestTo("http://user.invalid/internal/users/public-profiles")).andRespond(withServerError());

        Map<UUID, Author> authors = authorClient().findAll(List.of(author));

        assertThat(authors).containsEntry(author, Author.placeholder(author));
        assertThat(authorClient().findAll(List.of())).isEmpty();
    }
}
