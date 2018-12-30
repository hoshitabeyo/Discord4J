/*
 * This file is part of Discord4J.
 *
 * Discord4J is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Discord4J is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Discord4J. If not, see <http://www.gnu.org/licenses/>.
 */

package discord4j.rest.http.client;

import discord4j.rest.http.ExchangeStrategies;
import discord4j.rest.http.ReaderStrategy;
import discord4j.rest.http.WriterStrategy;
import discord4j.rest.json.response.ErrorResponse;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufMono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;
import reactor.util.Logger;
import reactor.util.Loggers;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * HTTP client tailored for Discord REST API requests.
 */
public class DiscordWebClient {

    private static final Logger log = Loggers.getLogger(DiscordWebClient.class);

    private final HttpClient httpClient;
    private final HttpHeaders defaultHeaders;
    private final ExchangeStrategies exchangeStrategies;
    private final List<StatusHandler> statusHandlers = new ArrayList<>();

    public DiscordWebClient(HttpClient httpClient, HttpHeaders defaultHeaders, ExchangeStrategies exchangeStrategies) {
        this.httpClient = httpClient;
        this.defaultHeaders = defaultHeaders;
        this.exchangeStrategies = exchangeStrategies;
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    public HttpHeaders getDefaultHeaders() {
        return defaultHeaders;
    }

    public ExchangeStrategies getExchangeStrategies() {
        return exchangeStrategies;
    }

    /**
     * Exchange a request for a {@link reactor.core.publisher.Mono} response of the specified type.
     * <p>
     * The request will be processed according to the writer strategies and its response according to the reader
     * strategies available.
     *
     * @param clientRequest the method, headers and URI of the client HTTP request
     * @param body an object representing the body of the request
     * @param responseType the desired response type
     * @param responseConsumer the consumer to use while processing the response
     * @param <R> the type of the request body, can be <code>null</code>
     * @param <T> the type of the response body, can be {@link java.lang.Void}
     * @return a {@link reactor.core.publisher.Mono} of {@link T} with the response
     */
    public <R, T> Mono<T> exchange(ClientRequest clientRequest, @Nullable R body, Class<T> responseType,
            Consumer<HttpClientResponse> responseConsumer) {
        Objects.requireNonNull(responseType);

        HttpHeaders requestHeaders = new DefaultHttpHeaders().add(defaultHeaders).setAll(clientRequest.headers());
        String contentType = requestHeaders.get(HttpHeaderNames.CONTENT_TYPE);
        HttpClient.RequestSender sender = httpClient
                .observe((connection, newState) -> log.debug("{} {}", newState, connection))
                .headers(headers -> headers.setAll(requestHeaders))
                .request(clientRequest.method())
                .uri(clientRequest.url());
        return exchangeStrategies.writers().stream()
                .filter(s -> s.canWrite(body != null ? body.getClass() : null, contentType))
                .findFirst()
                .map(DiscordWebClient::<R>cast)
                .map(writer -> writer.write(sender, body))
                .orElseGet(() -> Mono.error(noWriterException(body, contentType)))
                .flatMap(receiver -> receiver.responseSingle((response, content) -> {
                    responseConsumer.accept(response);

                    for (StatusHandler handler : statusHandlers) {
                        if (handler.test(response.status())) {
                            return handler.apply(response, content).flatMap(Mono::error);
                        }
                    }

                    String responseContentType = response.responseHeaders().get(HttpHeaderNames.CONTENT_TYPE);
                    Optional<ReaderStrategy<?>> readerStrategy = exchangeStrategies.readers().stream()
                            .filter(s -> s.canRead(responseType, responseContentType))
                            .findFirst();
                    int responseStatus = response.status().code();
                    if (responseStatus >= 400 && responseStatus < 600) {
                        return Mono.justOrEmpty(readerStrategy)
                                .map(DiscordWebClient::<ErrorResponse>cast)
                                .flatMap(s -> s.read(content, ErrorResponse.class))
                                .flatMap(s -> Mono.<T>error(clientException(response, s)))
                                .switchIfEmpty(Mono.error(clientException(response, null)));
                    } else {
                        return readerStrategy.map(DiscordWebClient::<T>cast)
                                .map(s -> s.read(content, responseType))
                                .orElseGet(() -> Mono.error(noReaderException(responseType, responseContentType)));
                    }
                }));
    }

    public DiscordWebClient onStatus(Predicate<HttpResponseStatus> statusPredicate,
            BiFunction<HttpClientResponse, ByteBufMono, Mono<? extends Throwable>> exceptionFunction) {
        statusHandlers.add(new StatusHandler(statusPredicate, exceptionFunction));
        return this;
    }

    @SuppressWarnings("unchecked")
    private static <T> WriterStrategy<T> cast(WriterStrategy<?> strategy) {
        return (WriterStrategy<T>) strategy;
    }

    @SuppressWarnings("unchecked")
    private static <T> ReaderStrategy<T> cast(ReaderStrategy<?> strategy) {
        return (ReaderStrategy<T>) strategy;
    }

    private static ClientException clientException(HttpClientResponse response, @Nullable ErrorResponse errorResponse) {
        return new ClientException(response.status(), response.responseHeaders(), errorResponse);
    }

    private static RuntimeException noWriterException(@Nullable Object body, String contentType) {
        return new RuntimeException("No strategies to write this request: " + body + " - " + contentType);
    }

    private static RuntimeException noReaderException(Object body, String contentType) {
        return new RuntimeException("No strategies to read this response: " + body + " - " + contentType);
    }

    private static class StatusHandler {

        private final Predicate<HttpResponseStatus> predicate;
        private final BiFunction<HttpClientResponse, ByteBufMono, Mono<? extends Throwable>> exceptionFunction;

        private StatusHandler(Predicate<HttpResponseStatus> predicate, BiFunction<HttpClientResponse, ByteBufMono,
                Mono<? extends Throwable>> exceptionFunction) {
            this.predicate = predicate;
            this.exceptionFunction = exceptionFunction;
        }

        public boolean test(HttpResponseStatus status) {
            return predicate.test(status);
        }

        public Mono<? extends Throwable> apply(HttpClientResponse response, ByteBufMono content) {
            return exceptionFunction.apply(response, content);
        }
    }
}
