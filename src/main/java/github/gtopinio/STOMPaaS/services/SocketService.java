package github.gtopinio.STOMPaaS.services;

import github.gtopinio.STOMPaaS.models.DTOs.SocketDTO;
import github.gtopinio.STOMPaaS.models.classes.SocketMessage;
import github.gtopinio.STOMPaaS.models.enums.MessageType;
import github.gtopinio.STOMPaaS.models.enums.UserType;
import github.gtopinio.STOMPaaS.models.factories.SocketSessionResponseFactory;
import github.gtopinio.STOMPaaS.models.helpers.SocketInputValidator;
import github.gtopinio.STOMPaaS.models.helpers.SocketSessionMapper;
import github.gtopinio.STOMPaaS.models.response.SocketMappingResponse;
import github.gtopinio.STOMPaaS.models.response.SocketSessionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.*;

@Service
@Slf4j
public class SocketService {
    private final SimpMessagingTemplate messagingTemplate;
    private final SocketInputValidator socketInputValidator;
    private final SocketSessionMapper socketSessionMapper;

    public SocketService(
        SimpMessagingTemplate messagingTemplate,
        SocketInputValidator socketInputValidator,
        SocketSessionMapper socketSessionMapper
    ) {
        this.messagingTemplate = messagingTemplate;
        this.socketInputValidator = socketInputValidator;
        this.socketSessionMapper = socketSessionMapper;
    }

    /**
     * This service method is used to link the socket session to the desired socket room.
     *
     * @param input The SocketDTO object containing the socket connection details.
     * @param headerAccessor The SimpMessageHeaderAccessor object containing the message header.
     */
    public SocketSessionResponse linkSocketSession(
        @Payload SocketDTO input,
        SimpMessageHeaderAccessor headerAccessor
    ) {
        // Validate input
        if (!this.socketInputValidator.validate(input)) {
            log.error("Linking socket session failed: Invalid input");
            return SocketSessionResponseFactory.createBadRequestResponse(null, "Invalid input");
        }

        if (!input.getMessageType().equals(MessageType.JOIN)) {
            log.error("Linking socket session failed: Invalid message type when linking socket session");
            return SocketSessionResponseFactory.createBadRequestResponse(null, "Invalid message type when linking socket session");
        }

        SocketMappingResponse upsertSocketSessionResponse = this.socketSessionMapper.upsertSocketSession(
            input.getSenderSocketId(),
            input.getOrganizationId(),
            input.getCategories(),
            input.getSocketRoomId(),
            input.getIsForMultipleUsers()
        );

        if (upsertSocketSessionResponse == null || !upsertSocketSessionResponse.isProcessStatus()) {
            log.error("Linking socket session failed: Response ID is null");
            return SocketSessionResponseFactory.createErrorResponse(null, "Error linking socket session");
        }

        // This is a message to be sent to the socket room by the system
        var responseMessage = SocketMessage.builder()
                .content("User " + input.getSenderUsername() + " has joined the chat")
                .senderUsername(UserType.SYSTEM.toString())
                .senderSocketId(null)
                .type(MessageType.JOIN)
                .socketRoomCount(upsertSocketSessionResponse.getSocketRoomCount())
                .exIncHubGamingRoomCount(upsertSocketSessionResponse.getExIncHubGamingRoomCount())
                .build();

        this.handleJoinMessage(headerAccessor, input.getSenderSocketId(), input.getSocketRoomId(), responseMessage);

        // This is telling the ExIncHubMainRoom that a new game has started, and it needs to update its count for both online users and games
        if (input.getSocketRoomId().equals(UUID.fromString("e615ee39-c350-4f50-ba2c-baf6b30900e7"))) {
            var pingMessageToExIncHubGamingRoom = SocketMessage.builder()
                    .content("New game instantiated")
                    .senderUsername(UserType.SYSTEM.toString())
                    .senderSocketId(null)
                    .socketRoomId(input.getSocketRoomId())
                    .type(MessageType.JOIN)
                    .socketRoomCount(upsertSocketSessionResponse.getSocketRoomCount())
                    .exIncHubGamingRoomCount(upsertSocketSessionResponse.getExIncHubGamingRoomCount())
                    .build();
            this.broadcastMessage(UUID.fromString("91c4b664-1bfd-4311-b7fd-e52e63658f46"), pingMessageToExIncHubGamingRoom);
        }

        log.info("Linking socket session successful");
        return SocketSessionResponseFactory.createSuccessResponse(input.getSocketRoomId(), "Socket session linked successfully");
    }

    /**
     * This service method is used to handle the session disconnect event.
     *
     * @param event The SessionDisconnectEvent object containing the session disconnect event details.
     */
    public SocketSessionResponse unlinkSocketSession(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());

        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        if (sessionAttributes == null) {
            log.error("Unlinking socket session failed: Session attributes are null");
            return SocketSessionResponseFactory.createErrorResponse(null, "Session attributes are null");
        }

        Object socketRoomIdObj = sessionAttributes.get("socketRoomId");
        Object senderSocketIdObj = sessionAttributes.get("senderSocketId");

        if (socketRoomIdObj == null || senderSocketIdObj == null) {
            log.error("Unlinking socket session failed: Required session attributes are missing");
            return SocketSessionResponseFactory.createErrorResponse(null, "Required session attributes are missing");
        }

        UUID socketRoomId = UUID.fromString(socketRoomIdObj.toString());
        UUID senderSocketId = UUID.fromString(senderSocketIdObj.toString());

        var removeSocketSessionResponse = this.socketSessionMapper.removeSocketSession(senderSocketId, socketRoomId);

        if (removeSocketSessionResponse != null && removeSocketSessionResponse.isProcessStatus()) {
            var responseMessage = SocketMessage.builder()
                    .content("User has left the chat")
                    .senderUsername(UserType.SYSTEM.toString())
                    .senderSocketId(null)
                    .socketRoomId(socketRoomId)
                    .type(MessageType.LEAVE)
                    .socketRoomCount(removeSocketSessionResponse.getSocketRoomCount())
                    .exIncHubGamingRoomCount(removeSocketSessionResponse.getExIncHubGamingRoomCount())
                    .build();

            this.broadcastMessage(socketRoomId, responseMessage);
            log.info("Unlinking socket session successful");
            log.info("Current socket room mapping: {}", this.socketSessionMapper.getSocketSessionMapping());

            // This is telling the ExIncHubMainRoom that a game has ended, and it needs to update its count for both online users and games
            if (removeSocketSessionResponse.getSocketRoomId().equals(UUID.fromString("e615ee39-c350-4f50-ba2c-baf6b30900e7"))) {
                var pingMessageToExIncHubGamingRoom = SocketMessage.builder()
                        .content("Game left by player")
                        .senderUsername(UserType.SYSTEM.toString())
                        .senderSocketId(null)
                        .socketRoomId(removeSocketSessionResponse.getSocketRoomId())
                        .type(MessageType.LEAVE)
                        .socketRoomCount(removeSocketSessionResponse.getSocketRoomCount())
                        .exIncHubGamingRoomCount(removeSocketSessionResponse.getExIncHubGamingRoomCount())
                        .build();
                this.broadcastMessage(UUID.fromString("91c4b664-1bfd-4311-b7fd-e52e63658f46"), pingMessageToExIncHubGamingRoom);
            }
            return SocketSessionResponseFactory.createSuccessResponse(null, "Socket session unlinked successfully");
        }

        log.error("Unlinking socket session failed: Removing socket session failed");
        return SocketSessionResponseFactory.createErrorResponse(null, "Error unlinking socket session");
    }

    /**
     * This service method is used to handle the JOIN message.
     *
     * @param headerAccessor The SimpMessageHeaderAccessor object containing the message header.
     * @param senderSocketId The UUID of the sender socket.
     * @param socketRoomId The UUID of the socket room.
     * @param message The SocketMessage object containing the message details.
     */
    private void handleJoinMessage(
        SimpMessageHeaderAccessor headerAccessor,
        UUID senderSocketId,
        UUID socketRoomId,
        SocketMessage message
    )
    {
        Objects.requireNonNull(headerAccessor.getSessionAttributes()).put("socketRoomId", socketRoomId);
        headerAccessor.getSessionAttributes().put("senderSocketId", senderSocketId);
        this.broadcastMessage(socketRoomId, message);
    }

    /**
     * This service method is used to broadcast the message to the socket room.
     *
     * @param socketRoomId The UUID of the socket room.
     * @param message The SocketMessage object containing the message details.
     */
    private void broadcastMessage(UUID socketRoomId, SocketMessage message) {
        this.messagingTemplate.convertAndSend("/topic/" + socketRoomId, message);
    }

    /**
     * This service method is used to send a message to the desired socket room.
     *
     * @param input The SocketDTO object containing the socket message details.
     */
    public SocketSessionResponse sendSocketMessage(
        @Payload SocketDTO input
    ) {
        // Validate input
        if (!this.socketInputValidator.validate(input)) {
            log.error("Socket message failed: Invalid input");
            return SocketSessionResponseFactory.createBadRequestResponse(null, "Invalid input");
        }

        MessageType socketMessageType = input.getMessageType();

        MessageType[] validMessageTypes = {MessageType.MESSAGE, MessageType.PING};
        List<MessageType> validMessageTypesList = Arrays.asList(validMessageTypes);

        if (!validMessageTypesList.contains(socketMessageType)) {
            log.error("Socket message failed: Invalid message type when sending socket message");
            return SocketSessionResponseFactory.createBadRequestResponse(null, "Invalid message type when sending socket message");
        }

        if (!this.socketSessionMapper.doesSocketRoomExist(input.getSocketRoomId())) {
            log.error("Socket message failed: Socket room does not exist");
            return SocketSessionResponseFactory.createErrorResponse(null, "Socket room does not exist");
        }

        SocketMessage responseMessage = null;

        if (socketMessageType.equals(MessageType.PING)) {
            responseMessage = SocketMessage.builder()
                    .content(input.getSocketMessage())
                    .senderUsername(input.getSenderUsername())
                    .senderSocketId(input.getSenderSocketId())
                    .socketRoomId(input.getSocketRoomId())
                    .type(MessageType.PING)
                    .build();

        } else if (socketMessageType.equals(MessageType.MESSAGE)) {
            responseMessage = SocketMessage.builder()
                    .content(input.getSocketMessage())
                    .senderUsername(input.getSenderUsername())
                    .senderSocketId(input.getSenderSocketId())
                    .socketRoomId(input.getSocketRoomId())
                    .type(MessageType.MESSAGE)
                    .build();
        }

        this.broadcastMessage(input.getSocketRoomId(), responseMessage);

        log.info("Socket message sent successfully: {}", input.getSocketMessage());
        return SocketSessionResponseFactory.createSuccessResponse(null, "Socket message sent successfully");

    }

}
