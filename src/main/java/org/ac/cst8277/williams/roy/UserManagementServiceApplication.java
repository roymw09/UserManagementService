package org.ac.cst8277.williams.roy;

import org.ac.cst8277.williams.roy.model.Publisher;
import org.ac.cst8277.williams.roy.model.Subscriber;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.List;
import java.util.Map;
import static org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction.oauth2AuthorizedClient;

@SpringBootApplication
@EnableDiscoveryClient
public class UserManagementServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserManagementServiceApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public ReactiveRedisOperations<String, Publisher> publisherTokenTemplate(LettuceConnectionFactory lettuceConnectionFactory){
        RedisSerializer<Publisher> valueSerializer = new Jackson2JsonRedisSerializer<>(Publisher.class);
        RedisSerializationContext<String, Publisher> serializationContext = RedisSerializationContext.<String, Publisher>newSerializationContext(RedisSerializer.string())
                .value(valueSerializer)
                .build();
        return new ReactiveRedisTemplate<>(lettuceConnectionFactory, serializationContext);
    }

    @Bean
    public ReactiveRedisOperations<String, Subscriber> subscriberTokenTemplate(LettuceConnectionFactory lettuceConnectionFactory){
        RedisSerializer<Subscriber> valueSerializer = new Jackson2JsonRedisSerializer<>(Subscriber.class);
        RedisSerializationContext<String, Subscriber> serializationContext = RedisSerializationContext.<String, Subscriber>newSerializationContext(RedisSerializer.string())
                .value(valueSerializer)
                .build();
        return new ReactiveRedisTemplate<>(lettuceConnectionFactory, serializationContext);
    }

    /*
    @Bean
    LettuceConnectionFactory lettuceConnectionFactory() {
        RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration();
        redisStandaloneConfiguration.setHostName("redis-cache-server"); // redis-cache-server
        redisStandaloneConfiguration.setPort(6379);

        LettuceClientConfiguration.LettuceClientConfigurationBuilder lettuceClientConfigurationBuilder = LettuceClientConfiguration.builder();

        return new LettuceConnectionFactory(redisStandaloneConfiguration, lettuceClientConfigurationBuilder.build());
    }*/

    @Bean
    public OAuth2UserService<OAuth2UserRequest, OAuth2User> oauth2UserService(WebClient rest) {
        DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
        return request -> {
            OAuth2User user = delegate.loadUser(request);
            if (!"github".equals(request.getClientRegistration().getRegistrationId())) {
                return user;
            }

            OAuth2AuthorizedClient client = new OAuth2AuthorizedClient
                    (request.getClientRegistration(), user.getName(), request.getAccessToken());
            String url = user.getAttribute("organizations_url");
            List<Map<String, Object>> orgs = rest
                    .get().uri(url)
                    .attributes(oauth2AuthorizedClient(client))
                    .retrieve()
                    .bodyToMono(List.class)
                    .block();

            assert orgs != null;
            if (orgs.stream().noneMatch(org -> "spring-projects".equals(org.get("login")))) {
                return user;
            }

            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token", "Not in Spring Team", ""));
        };
    }

    @Bean
    public WebClient rest(ClientRegistrationRepository clients, OAuth2AuthorizedClientRepository authz) {
        ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2 =
                new ServletOAuth2AuthorizedClientExchangeFilterFunction(clients, authz);
        return WebClient.builder()
                .filter(oauth2).build();
    }
}
