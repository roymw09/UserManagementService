package org.ac.cst8277.williams.roy.controller;

import lombok.extern.slf4j.Slf4j;
import org.ac.cst8277.williams.roy.dto.User;
import org.ac.cst8277.williams.roy.dto.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DirtiesContext
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@Slf4j
public class UserControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DatabaseClient databaseClient;

    private List<User> getData() {
        return Arrays.asList(
                new User(null, "roymw@testmail.com", "testuser1", "testpass1", new UUID(2, 10)),
                new User(null, "martin@testmail.com", "testuser2", "testpass2", new UUID(2, 10)),
                new User(null, "morty@testmail.com", "testuser3", "testpass3", new UUID(2, 10))
        );
    }

    @BeforeEach
    public void setup() {
        List<String> statements = Arrays.asList(
                "DROP TABLE IF EXISTS users",
                "CREATE TABLE users (\n" +
                        "    id INT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,\n" +
                        "    email VARCHAR(100) NOT NULL,\n" +
                        "    username VARCHAR(100) NOT NULL,\n" +
                        "    password CHAR(255),\n" +
                        "    token VARCHAR(255)\n" +
                        ");"
        );
        statements.forEach(it -> databaseClient.sql(it)
                .fetch()
                .rowsUpdated()
                .block());

        userRepository.deleteAll()
                .thenMany(Flux.fromIterable(getData()))
                .flatMap(userRepository::save)
                .doOnNext(user -> {
                   System.out.println("User inserted from UserControllerTest: " + user);
                })
                .blockLast();
    }

    @Test
    public void notNull() {
        assertNotNull(webTestClient);
    }

    @Test
    public void getAllUsersValidateCount(){
        webTestClient.get().uri("/users").exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON_VALUE)
                .expectBodyList(User.class)
                .hasSize(3)
                .consumeWith(user ->{
                    List<User> users = user.getResponseBody();
                    users.forEach( u ->{
                        assertNotNull(u.getId());
                    });
                });
    }
    @Test
    public void getAllUsersValidateResponse(){
        Flux<User> userFlux = webTestClient.get().uri("/users").exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON_VALUE)
                .returnResult(User.class)
                .getResponseBody();
        StepVerifier.create(userFlux.log("Receiving values !!!"))
                .expectNextCount(3)
                .verifyComplete();

    }
    @Test
    public void getUserById(){
        webTestClient.get().uri("/users".concat("/{userId}"),"1")
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.email","roymw@testmail.com");
    }
    @Test
    public void getUserById_NotFound(){
        webTestClient.get().uri("/users".concat("/{userId}"),"6")
                .exchange().expectStatus().isNotFound();
    }
    @Test
    public void createUser(){
        User user = new User(null, "morty@testmail.com", "testuser3", "testpass3", new UUID(2, 10));
        webTestClient.post().uri("/users").contentType(MediaType.valueOf(MediaType.APPLICATION_JSON_VALUE))
                .body(Mono.just(user),User.class)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isNotEmpty()
                .jsonPath("$.username").isEqualTo("testuser3");
    }
    @Test
    public void deleteUser(){
        webTestClient.delete().uri("/users".concat("/{userId}"),"1")
                .accept(MediaType.valueOf(MediaType.APPLICATION_JSON_VALUE))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Void.class);
    }
    @Test
    public void updateUser(){
        User user = new User(null, "roymw@testmail.com", "testuser4", "testpass3", new UUID(2, 10));
        webTestClient.put().uri("/users".concat("/{userId}"),"1")
                .contentType(MediaType.valueOf(MediaType.APPLICATION_JSON_VALUE))
                .accept(MediaType.valueOf(MediaType.APPLICATION_JSON_VALUE))
                .body(Mono.just(user),User.class)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.email").isEqualTo("roymw@testmail.com");
    }
    @Test
    public void updateUser_notFound(){
        User user = new User(null, "roymw@testmail.com", "testuser3", "testpass3", new UUID(2, 10));
        webTestClient.put().uri("/users".concat("/{userId}"),"6")
                .contentType(MediaType.valueOf(MediaType.APPLICATION_JSON_VALUE))
                .accept(MediaType.valueOf(MediaType.APPLICATION_JSON_VALUE))
                .body(Mono.just(user),User.class)
                .exchange()
                .expectStatus().isBadRequest();
    }
}