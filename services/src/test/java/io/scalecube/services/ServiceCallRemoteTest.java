package io.scalecube.services;

import static io.scalecube.services.TestRequests.GREETING_EMPTY_REQUEST_RESPONSE;
import static io.scalecube.services.TestRequests.GREETING_ERROR_REQ;
import static io.scalecube.services.TestRequests.GREETING_FAILING_VOID_REQ;
import static io.scalecube.services.TestRequests.GREETING_FAIL_REQ;
import static io.scalecube.services.TestRequests.GREETING_NO_PARAMS_REQUEST;
import static io.scalecube.services.TestRequests.GREETING_REQ;
import static io.scalecube.services.TestRequests.GREETING_REQUEST_REQ;
import static io.scalecube.services.TestRequests.GREETING_REQUEST_TIMEOUT_REQ;
import static io.scalecube.services.TestRequests.GREETING_THROWING_VOID_REQ;
import static io.scalecube.services.TestRequests.GREETING_VOID_REQ;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.discovery.ScalecubeServiceDiscovery;
import io.scalecube.services.exceptions.ServiceException;
import io.scalecube.services.sut.EmptyGreetingResponse;
import io.scalecube.services.sut.GreetingResponse;
import io.scalecube.services.sut.GreetingServiceImpl;
import io.scalecube.services.sut.QuoteService;
import io.scalecube.services.sut.SimpleQuoteService;
import io.scalecube.services.transport.rsocket.RSocketServiceTransport;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class ServiceCallRemoteTest extends BaseTest {

  public static final int TIMEOUT = 3;
  private Duration timeout = Duration.ofSeconds(TIMEOUT);

  private static ScaleCube gateway;
  private static ScaleCube provider;

  @BeforeAll
  public static void setup() {
    gateway = gateway();
    provider = serviceProvider(new GreetingServiceImpl());
  }

  @AfterAll
  public static void tearDown() {
    try {
      gateway.shutdown().block();
    } catch (Exception ignore) {
      // no-op
    }

    try {
      provider.shutdown().block();
    } catch (Exception ignore) {
      // no-op
    }
  }

  private static ScaleCube serviceProvider(Object service) {
    return ScaleCube.builder()
        .discovery(
            endpoint ->
                new ScalecubeServiceDiscovery(endpoint)
                    .membership(cfg -> cfg.seedMembers(gateway.discovery().address())))
        .transport(RSocketServiceTransport::new)
        .services(service)
        .startAwait();
  }

  @Test
  public void test_remote_async_greeting_no_params() {

    ServiceCall serviceCall = gateway.call();

    // call the service.
    Publisher<ServiceMessage> future =
        serviceCall.requestOne(GREETING_NO_PARAMS_REQUEST, GreetingResponse.class);

    ServiceMessage message = Mono.from(future).block(timeout);

    assertEquals("hello unknown", ((GreetingResponse) message.data()).getResult());
  }

  @Test
  public void test_remote_void_greeting() {
    // When
    StepVerifier.create(gateway.call().oneWay(GREETING_VOID_REQ)).expectComplete().verify(timeout);
  }

  @Test
  public void test_remote_mono_empty_request_response_greeting_messsage() {
    StepVerifier.create(
            gateway.call().requestOne(GREETING_EMPTY_REQUEST_RESPONSE, EmptyGreetingResponse.class))
        .expectNextMatches(resp -> resp.data() instanceof EmptyGreetingResponse)
        .expectComplete()
        .verify(timeout);
  }

  @Test
  public void test_remote_failing_void_greeting() {

    // When
    StepVerifier.create(gateway.call().requestOne(GREETING_FAILING_VOID_REQ, Void.class))
        .expectErrorMessage(GREETING_FAILING_VOID_REQ.data().toString())
        .verify(Duration.ofSeconds(TIMEOUT));
  }

  @Test
  public void test_remote_throwing_void_greeting() {
    // When
    StepVerifier.create(gateway.call().oneWay(GREETING_THROWING_VOID_REQ))
        .expectErrorMessage(GREETING_THROWING_VOID_REQ.data().toString())
        .verify(Duration.ofSeconds(TIMEOUT));
  }

  @Test
  public void test_remote_fail_greeting() {
    // When
    Throwable exception =
        assertThrows(
            ServiceException.class,
            () ->
                Mono.from(gateway.call().requestOne(GREETING_FAIL_REQ, GreetingResponse.class))
                    .block(timeout));
    assertEquals("GreetingRequest{name='joe'}", exception.getMessage());
  }

  @Test
  public void test_remote_exception_void() {

    // When
    Throwable exception =
        assertThrows(
            ServiceException.class,
            () ->
                Mono.from(gateway.call().requestOne(GREETING_ERROR_REQ, GreetingResponse.class))
                    .block(timeout));
    assertEquals("GreetingRequest{name='joe'}", exception.getMessage());
  }

  @Test
  public void test_remote_async_greeting_return_string() {

    Publisher<ServiceMessage> resultFuture = gateway.call().requestOne(GREETING_REQ, String.class);

    // Then
    ServiceMessage result = Mono.from(resultFuture).block(Duration.ofSeconds(TIMEOUT));
    assertNotNull(result);
    assertEquals(GREETING_REQ.qualifier(), result.qualifier());
    assertEquals(" hello to: joe", result.data());
  }

  @Test
  public void test_remote_async_greeting_return_GreetingResponse() {

    // When
    Publisher<ServiceMessage> result =
        gateway.call().requestOne(GREETING_REQUEST_REQ, GreetingResponse.class);

    // Then
    GreetingResponse greeting = Mono.from(result).block(Duration.ofSeconds(TIMEOUT)).data();
    assertEquals(" hello to: joe", greeting.getResult());
  }

  @Test
  public void test_remote_greeting_request_timeout_expires() {

    ServiceCall service = gateway.call();

    // call the service.
    Publisher<ServiceMessage> future = service.requestOne(GREETING_REQUEST_TIMEOUT_REQ);
    Throwable exception =
        assertThrows(RuntimeException.class, () -> Mono.from(future).block(Duration.ofSeconds(1)));
    assertTrue(exception.getMessage().contains("Timeout on blocking read"));
  }

  // Since here and below tests were not reviewed [sergeyr]
  @Test
  public void test_remote_async_greeting_return_Message() {
    ServiceCall service = gateway.call();

    // call the service.
    Publisher<ServiceMessage> future = service.requestOne(GREETING_REQUEST_REQ);

    Mono.from(future)
        .doOnNext(
            result -> {
              // print the greeting.
              System.out.println("10. remote_async_greeting_return_Message :" + result.data());
              // print the greeting.
              assertThat(result.data(), instanceOf(GreetingResponse.class));
              assertEquals(" hello to: joe", ((GreetingResponse) result.data()).getResult());
            });
  }

  @Test
  public void test_remote_dispatcher_remote_greeting_request_completes_before_timeout() {

    Publisher<ServiceMessage> result =
        gateway.call().requestOne(GREETING_REQUEST_REQ, GreetingResponse.class);

    GreetingResponse greetings = Mono.from(result).block(Duration.ofSeconds(TIMEOUT)).data();
    System.out.println("greeting_request_completes_before_timeout : " + greetings.getResult());
    assertEquals(" hello to: joe", greetings.getResult());
  }

  @Test
  public void test_service_address_lookup_occur_only_after_subscription() {

    Flux<ServiceMessage> quotes =
        gateway
            .call()
            .requestMany(
                ServiceMessage.builder()
                    .qualifier(QuoteService.NAME, "onlyOneAndThenNever")
                    .data(null)
                    .build());

    // Add service to cluster AFTER creating a call object.
    // (prove address lookup occur only after subscription)
    ScaleCube quotesService = serviceProvider(new SimpleQuoteService());

    StepVerifier.create(quotes.take(1)).expectNextCount(1).expectComplete().verify(timeout);

    try {
      quotesService.shutdown();
    } catch (Exception ignored) {
      // no-op
    }
  }

  @Test
  public void test_many_stream_block_first() {
    ServiceCall call = gateway.call();

    ServiceMessage request = TestRequests.GREETING_MANY_STREAM_30;

    for (int i = 0; i < 100; i++) {
      //noinspection ConstantConditions
      long first =
          call.requestMany(request, Long.class)
              .map(ServiceMessage::<Long>data)
              .filter(k -> k != 0)
              .take(1)
              .blockFirst();
      assertEquals(1, first);
    }
  }

  private static ScaleCube gateway() {
    return ScaleCube.builder()
        .discovery(ScalecubeServiceDiscovery::new)
        .transport(RSocketServiceTransport::new)
        .startAwait();
  }
}
