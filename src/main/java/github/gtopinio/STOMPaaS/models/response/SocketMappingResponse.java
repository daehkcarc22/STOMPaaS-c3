package github.gtopinio.STOMPaaS.models.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;

@ToString
@Getter
@Setter
@Builder
public class SocketMappingResponse {
    private UUID socketRoomId;
    private Integer socketRoomCount;
    private boolean processStatus;
}
