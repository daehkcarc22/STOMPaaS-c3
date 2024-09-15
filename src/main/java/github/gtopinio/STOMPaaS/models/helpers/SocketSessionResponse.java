package github.gtopinio.STOMPaaS.models.helpers;

import lombok.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

@Getter
@Setter
@Builder
public class SocketSessionResponse {
    private UUID socketRoomId;
    private ResponseEntity<String> responseEntity;

    public static SocketSessionResponse of(UUID socketRoomId, String body, HttpStatus status) {
        return SocketSessionResponse.builder()
                .socketRoomId(socketRoomId)
                .responseEntity(new ResponseEntity<>(body, status))
                .build();
    }
}