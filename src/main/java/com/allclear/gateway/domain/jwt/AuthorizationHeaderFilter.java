package com.allclear.gateway.domain.jwt;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Component
public class AuthorizationHeaderFilter extends AbstractGatewayFilterFactory<AuthorizationHeaderFilter.Config> {

    private final JwtUtil jwtUtil;


    public AuthorizationHeaderFilter(JwtUtil jwtUtil) {
        super(Config.class);
        this.jwtUtil = jwtUtil;
    }

    // GatewayFilter 설정을 위한 Config 클래스
    public static class Config {

    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            // HTTP 요청 헤더에서 Authorization 헤더를 가져옴
            HttpHeaders headers = request.getHeaders();
            if (!headers.containsKey(HttpHeaders.AUTHORIZATION)) {
                return onError(exchange, "HTTP 요청 헤더에 Authorization 헤더가 포함되어 있지 않습니다.", HttpStatus.UNAUTHORIZED);
            }

            String authorizationHeader = Objects.requireNonNull(headers.get(HttpHeaders.AUTHORIZATION)).get(0);

            // JWT 토큰 가져오기
            String accessToken = authorizationHeader.replace("Bearer ", "");

            // JWT 토큰 유효성 검사
            if(!jwtUtil.validateTokenBoolean(accessToken)){
                //TODO 에러
            }

            // JWT 토큰에서 사용자 email 추출
            Long studentId = jwtUtil.getMemberIdFromJwtToken(accessToken);


            // user-service에서 학생 정보를 가져옴
            String userServiceUri = "http://localhost:8082/api/users/student-info?studentId=" + studentId;
            return WebClient.create()
                    .get()
                    .uri(userServiceUri)
                    .retrieve()
                    .bodyToMono(StudentResponseDTO.GetStudentDTO.class) // StudentDTO 클래스를 생성해줘야 함
                    .flatMap(studentDTO -> {
                        // 사용자 정보를 HTTP 요청 헤더에 추가하여 전달
                        ServerHttpRequest newRequest = request.mutate()
                                .header("studentId", studentDTO.getStudentId().toString())
                                .header("studentIdNumber", studentDTO.getStudentIdNumber())
                                .header("grade", String.valueOf(studentDTO.getGrade()))
                                .build();

                        return chain.filter(exchange.mutate().request(newRequest).build());
                    })
                    .onErrorResume(e -> onError(exchange, "user-service에서 정보 가져오는 중 오류 발생", HttpStatus.INTERNAL_SERVER_ERROR));
        };
    }

    // Mono(단일 값), Flux(다중 값) -> Spring WebFlux
    private Mono<Void> onError(ServerWebExchange exchange, String errorMsg, HttpStatus httpStatus) {

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);

        return response.setComplete();
    }


}
