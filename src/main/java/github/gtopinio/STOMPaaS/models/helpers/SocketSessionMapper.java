package github.gtopinio.STOMPaaS.models.helpers;

import github.gtopinio.STOMPaaS.models.classes.SocketSessionEntry;
import github.gtopinio.STOMPaaS.models.classes.SocketUser;
import github.gtopinio.STOMPaaS.models.response.SocketMappingResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@Slf4j
public class SocketSessionMapper {
    /**
     * This map is used to store the socket session mapping.
     * The key is the UUID of the socket room, which says that the chat is active if it exists.
     */
    private final Map<UUID, SocketSessionEntry> socketSessionMapping;

    public SocketSessionMapper() {
        this.socketSessionMapping = new ConcurrentHashMap<>();
    }

    /**
     * This method is used to get the socket session mapping.
     */

    public Map<UUID, SocketSessionEntry> getSocketSessionMapping() {
        return this.socketSessionMapping;
    }


    /**
     * This method is used to check if a socket room exists.
     *
     * @param socketRoomId The UUID of the socket room.
     */
    public boolean doesSocketRoomExist(UUID socketRoomId) {
        return this.socketSessionMapping.containsKey(socketRoomId);
    }

    /**
     * This method is used to create a new socket session entry.
     *
     * @param senderSocketId The UUID of the sender socket.
     * @param organizationId The UUID of the organization.
     */
    public SocketUser createSocketUser(UUID senderSocketId, UUID organizationId) {
        return SocketUser.builder()
                .senderSocketId(senderSocketId)
                .organizationId(organizationId)
                .build();
    }

    /**
     * This method is used to create a new socket session entry.
     *
     * @param categories The list of categories.
     * @param isMultipleUsers The boolean value indicating if the session is for multiple users.
     */
    public SocketSessionEntry createSocketSessionEntry(
            List<String> categories,
            Boolean isMultipleUsers
    ) {
        return SocketSessionEntry.builder()
                .socketUserList(new CopyOnWriteArrayList<>())
                .socketRoomCategoryList(categories)
                .isForMultipleUsers(isMultipleUsers)
                .build();
    }

    /**
     * This method is used for socket sessions that are trying to JOIN a room.
     * If the return value is true, the user is successfully added to the room.
     * If the return value is false, the user is already in the room.
     *
     * @param senderSocketId The UUID of the sender socket.
     * @param organizationId The UUID of the organization.
     * @param categories The list of categories.
     * @param socketRoomId The UUID of the socket room.
     * @param isMultipleUsers The boolean value indicating if the session is for multiple users.
     */
    public SocketMappingResponse upsertSocketSession(
            UUID senderSocketId,
            UUID organizationId,
            List<String> categories,
            UUID socketRoomId,
            Boolean isMultipleUsers
    ) {
        if (!categories.isEmpty()) {
            UUID existingRoomId = findExistingRoomByCategories(categories, senderSocketId, isMultipleUsers, organizationId);
            if (existingRoomId != null) {
                return buildSocketMappingResponse(existingRoomId, true);
            } else {
                return handleRoomCreationOrUpdate(socketRoomId, categories, senderSocketId, organizationId, isMultipleUsers);
            }
        }

        return handleRoomCreationOrUpdate(socketRoomId, categories, senderSocketId, organizationId, isMultipleUsers);
    }

    private SocketMappingResponse handleRoomCreationOrUpdate(
            UUID socketRoomId,
            List<String> categories,
            UUID senderSocketId,
            UUID organizationId,
            Boolean isMultipleUsers
    ) {
        if (this.doesSocketRoomExist(socketRoomId)) {
            UUID activeRoomId = this.handleExistingRoom(socketRoomId, senderSocketId, organizationId, isMultipleUsers);
            if (activeRoomId != null) {
                return buildSocketMappingResponse(activeRoomId, true);
            } else {
                return buildSocketMappingResponse(null, false);
            }
        } else {
            UUID newRoomId = this.createNewRoom(socketRoomId, categories, senderSocketId, organizationId, isMultipleUsers);
            if (newRoomId != null) {
                return buildSocketMappingResponse(newRoomId, true);
            } else {
                return buildSocketMappingResponse(null, false);
            }
        }
    }

    private SocketMappingResponse buildSocketMappingResponse(UUID roomId, boolean status) {
        int roomCount = (roomId != null) ? this.socketSessionMapping.get(roomId).getSocketUserList().size() : 0;
        return SocketMappingResponse.builder()
                .socketRoomId(roomId)
                .socketRoomCount(roomCount)
                .processStatus(status)
                .build();
    }

    private UUID findExistingRoomByCategories(List<String> categories, UUID senderSocketId, Boolean isMultipleUsers, UUID organizationId) {
        for (Map.Entry<UUID, SocketSessionEntry> entry : this.socketSessionMapping.entrySet()) {
            SocketSessionEntry socketSessionEntry = entry.getValue();
            if (socketSessionEntry.getSocketRoomCategoryList().equals(categories)) {
                if (isUserInRoom(socketSessionEntry, senderSocketId) || isUserTypeMismatch(socketSessionEntry, isMultipleUsers)) {
                    return null;
                }
                this.addUserToRoom(socketSessionEntry, senderSocketId, organizationId);
                this.socketSessionMapping.put(entry.getKey(), socketSessionEntry);
                log.info("[Category] Socket room updated: {}", entry.getKey());
                log.info("[Category] Updated Socket room mapping: {}", this.socketSessionMapping);
                return entry.getKey();
            }
        }
        return null;
    }

    private UUID handleExistingRoom(UUID socketRoomId, UUID senderSocketId, UUID organizationId, Boolean isMultipleUsers) {
        SocketSessionEntry socketSessionEntry = this.socketSessionMapping.get(socketRoomId);
        if (isUserInRoom(socketSessionEntry, senderSocketId) || isUserTypeMismatch(socketSessionEntry, isMultipleUsers)) {
            return null;
        }
        addUserToRoom(socketSessionEntry, senderSocketId, organizationId);
        this.socketSessionMapping.put(socketRoomId, socketSessionEntry);
        log.info("[UUID] Socket room updated: {}", socketRoomId);
        log.info("[UUID] Updated Socket room mapping: {}", this.socketSessionMapping);
        return socketRoomId;
    }

    private UUID createNewRoom(UUID socketRoomId, List<String> categories, UUID senderSocketId, UUID organizationId, Boolean isMultipleUsers) {
        SocketSessionEntry socketSessionEntry = this.createSocketSessionEntry(categories, isMultipleUsers);
        socketSessionEntry.getSocketUserList().add(this.createSocketUser(senderSocketId, organizationId));
        this.socketSessionMapping.put(socketRoomId, socketSessionEntry);
        log.info("Socket room created: {}", socketRoomId);
        log.info("Current Socket room mapping: {}", this.socketSessionMapping);
        return socketRoomId;
    }

    private boolean isUserInRoom(SocketSessionEntry socketSessionEntry, UUID senderSocketId) {
        for (SocketUser socketUser : socketSessionEntry.getSocketUserList()) {
            if (socketUser.getSenderSocketId().equals(senderSocketId)) {
                return true;
            }
        }
        return false;
    }

    private boolean isUserTypeMismatch(SocketSessionEntry socketSessionEntry, Boolean isMultipleUsers) {
        return (socketSessionEntry.getIsForMultipleUsers() && !isMultipleUsers) ||
                (!socketSessionEntry.getIsForMultipleUsers() && isMultipleUsers);
    }

    private void addUserToRoom(SocketSessionEntry socketSessionEntry, UUID senderSocketId, UUID organizationId) {
        socketSessionEntry.getSocketUserList().add(this.createSocketUser(senderSocketId, organizationId));
    }

    public SocketMappingResponse removeSocketSession(
        UUID senderSocketId,
        UUID socketRoomId
    ) {
        if (this.doesSocketRoomExist(socketRoomId)) {
            SocketSessionEntry socketSessionEntry = this.socketSessionMapping.get(socketRoomId);
            List<SocketUser> socketUserList = socketSessionEntry.getSocketUserList();

            for (SocketUser socketUser : socketUserList) {
                if (socketUser.getSenderSocketId().equals(senderSocketId)) {
                    socketUserList.remove(socketUser);
                    socketSessionEntry.setSocketUserList(socketUserList);
                    this.socketSessionMapping.put(socketRoomId, socketSessionEntry);

                    this.cleanUpSocketRoom(socketRoomId);

                    return SocketMappingResponse.builder()
                            .socketRoomId(socketRoomId)
                            .socketRoomCount(socketUserList.size())
                            .processStatus(true)
                            .build();
                }
            }
        }
        return SocketMappingResponse.builder()
                .socketRoomId(null)
                .socketRoomCount(0)
                .processStatus(false)
                .build();
    }

    private void cleanUpSocketRoom(UUID socketRoomId) {
        if (this.doesSocketRoomExist(socketRoomId)) {
            SocketSessionEntry socketSessionEntry = this.socketSessionMapping.get(socketRoomId);
            if (socketSessionEntry.getSocketUserList().isEmpty()) {
                log.info("Socket room removed: {}", socketRoomId);
                this.socketSessionMapping.remove(socketRoomId);
            }
        }
    }

}
