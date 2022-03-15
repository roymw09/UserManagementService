package org.ac.cst8277.williams.roy.repository;

import org.ac.cst8277.williams.roy.model.User;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface UserRepository extends ReactiveCrudRepository<User, Long> {
    @Query("select * from users where id = :userId")
    Mono<User> findById(@Param("userId") Integer userId);

    @Query("select * from users where email = :userEmail and token = :userToken")
    Mono<User> checkUserToken(@Param("userEmail") String userEmail, @Param("userToken") String userToken);
}