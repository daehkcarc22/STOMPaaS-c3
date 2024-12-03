package github.gtopinio.STOMPaaS.models.helpers;

import github.gtopinio.STOMPaaS.models.classes.SocketSessionEntry;
import github.gtopinio.STOMPaaS.models.classes.SocketUser;
import github.gtopinio.STOMPaaS.models.response.SocketMappingResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.*;

@Service
@Slf4j
public class SocketSessionMapper {
    /**
     * This map is used to store the socket session mapping.
     * The key is the UUID of the socket room, which says that the chat is active if it exists.
     */
    private final Map<UUID, SocketSessionEntry> socketSessionMapping;
    private static int bufferUserCountDisplayTemp;
    private static int bufferDecrementTemp;
    private static int exIncHubGamingRoomCount;
    private static int exIncHubMainRoomCount;

    public SocketSessionMapper() {
        this.socketSessionMapping = new ConcurrentHashMap<>();
        this.randomizeBufferUserCount();
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

    /**
     * This method is used to handle the room creation or update.
     *
     * @param socketRoomId The UUID of the socket room.
     * @param categories The list of categories.
     * @param senderSocketId The UUID of the sender socket.
     * @param organizationId The UUID of the organization.
     * @param isMultipleUsers The boolean value indicating if the session is for multiple users.
     */
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
                return this.buildSocketMappingResponse(activeRoomId, true);
            } else {
                return this.buildSocketMappingResponse(null, false);
            }
        } else {
            UUID newRoomId = this.createNewRoom(socketRoomId, categories, senderSocketId, organizationId, isMultipleUsers);
            if (newRoomId != null) {
                return this.buildSocketMappingResponse(newRoomId, true);
            } else {
                return this.buildSocketMappingResponse(null, false);
            }
        }
    }

    /**
     * This method is used to build the socket mapping response.
     *
     * @param roomId The UUID of the room.
     * @param status The boolean value indicating the status.
     */
    private SocketMappingResponse buildSocketMappingResponse(UUID roomId, boolean status) {
        int roomCount = (roomId != null) ? this.socketSessionMapping.get(roomId).getSocketUserList().size() : 0;
        // Special Case for ExIncHub (tell the user how many people are in the main room and the number of people in the gaming room)
        exIncHubGamingRoomCount = this.socketSessionMapping.containsKey(UUID.fromString("e615ee39-c350-4f50-ba2c-baf6b30900e7")) ? this.socketSessionMapping.get(UUID.fromString("e615ee39-c350-4f50-ba2c-baf6b30900e7")).getSocketUserList().size() : 0;
        if (roomId != null && roomId.equals(UUID.fromString("e615ee39-c350-4f50-ba2c-baf6b30900e7"))) {
            exIncHubMainRoomCount = this.socketSessionMapping.containsKey(UUID.fromString("91c4b664-1bfd-4311-b7fd-e52e63658f46")) ? this.socketSessionMapping.get(UUID.fromString("91c4b664-1bfd-4311-b7fd-e52e63658f46")).getSocketUserList().size() : 0;
            roomCount = exIncHubMainRoomCount + bufferUserCountDisplayTemp;

            return SocketMappingResponse.builder()
                    .socketRoomId(roomId)
                    .socketRoomCount(roomCount)
                    .processStatus(status)
                    .exIncHubGamingRoomCount(exIncHubGamingRoomCount + (bufferUserCountDisplayTemp - bufferDecrementTemp))
                    .build();
        } else {
            roomCount += bufferUserCountDisplayTemp;
            return SocketMappingResponse.builder()
                    .socketRoomId(roomId)
                    .socketRoomCount(roomCount)
                    .processStatus(status)
                    .exIncHubGamingRoomCount(exIncHubGamingRoomCount + (bufferUserCountDisplayTemp - bufferDecrementTemp))
                    .build();
        }
    }

    /**
     * This method is used to find an existing room by categories.
     *
     * @param categories The list of categories.
     * @param senderSocketId The UUID of the sender socket.
     * @param isMultipleUsers The boolean value indicating if the session is for multiple users.
     * @param organizationId The UUID of the organization.
     */
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

    /**
     * This method is used to handle the existing room.
     *
     * @param socketRoomId The UUID of the socket room.
     * @param senderSocketId The UUID of the sender socket.
     * @param organizationId The UUID of the organization.
     * @param isMultipleUsers The boolean value indicating if the session is for multiple users.
     */
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

    /**
     * This method is used to create a new room.
     *
     * @param socketRoomId The UUID of the socket room.
     * @param categories The list of categories.
     * @param senderSocketId The UUID of the sender socket.
     * @param organizationId The UUID of the organization.
     * @param isMultipleUsers The boolean value indicating if the session is for multiple users.
     */
    private UUID createNewRoom(UUID socketRoomId, List<String> categories, UUID senderSocketId, UUID organizationId, Boolean isMultipleUsers) {
        SocketSessionEntry socketSessionEntry = this.createSocketSessionEntry(categories, isMultipleUsers);
        socketSessionEntry.getSocketUserList().add(this.createSocketUser(senderSocketId, organizationId));
        this.socketSessionMapping.put(socketRoomId, socketSessionEntry);
        log.info("Socket room created: {}", socketRoomId);
        log.info("Current Socket room mapping: {}", this.socketSessionMapping);
        return socketRoomId;
    }

    /**
     * This method is used to check if the user is in the room.
     *
     * @param socketSessionEntry The SocketSessionEntry object containing the socket session entry details.
     * @param senderSocketId The UUID of the sender socket.
     */
    private boolean isUserInRoom(SocketSessionEntry socketSessionEntry, UUID senderSocketId) {
        for (SocketUser socketUser : socketSessionEntry.getSocketUserList()) {
            if (socketUser.getSenderSocketId().equals(senderSocketId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * This method is used to check if the user type is mismatched.
     *
     * @param socketSessionEntry The SocketSessionEntry object containing the socket session entry details.
     * @param isMultipleUsers The boolean value indicating if the session is for multiple users.
     */
    private boolean isUserTypeMismatch(SocketSessionEntry socketSessionEntry, Boolean isMultipleUsers) {
        return (socketSessionEntry.getIsForMultipleUsers() && !isMultipleUsers) ||
                (!socketSessionEntry.getIsForMultipleUsers() && isMultipleUsers);
    }

    /**
     * This method is used to add a user to the room.
     *
     * @param socketSessionEntry The SocketSessionEntry object containing the socket session entry details.
     * @param senderSocketId The UUID of the sender socket.
     * @param organizationId The UUID of the organization.
     */
    private void addUserToRoom(SocketSessionEntry socketSessionEntry, UUID senderSocketId, UUID organizationId) {
        socketSessionEntry.getSocketUserList().add(this.createSocketUser(senderSocketId, organizationId));
    }

    /**
     * This method is used for socket sessions that are trying to LEAVE a room.
     * If the return value is true, the user is successfully removed from the room.
     * If the return value is false, the user is not in the room.
     *
     * @param senderSocketId The UUID of the sender socket.
     * @param socketRoomId The UUID of the socket room.
     */
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

                    // This logic section is for ExIncHub telling the main room to update the count for both online user count and games count
                    exIncHubGamingRoomCount = this.socketSessionMapping.containsKey(UUID.fromString("e615ee39-c350-4f50-ba2c-baf6b30900e7")) ? this.socketSessionMapping.get(UUID.fromString("e615ee39-c350-4f50-ba2c-baf6b30900e7")).getSocketUserList().size() : 0;
                    if (socketRoomId != null && socketRoomId.equals(UUID.fromString("e615ee39-c350-4f50-ba2c-baf6b30900e7"))) {
                        exIncHubMainRoomCount = this.socketSessionMapping.containsKey(UUID.fromString("91c4b664-1bfd-4311-b7fd-e52e63658f46")) ? this.socketSessionMapping.get(UUID.fromString("91c4b664-1bfd-4311-b7fd-e52e63658f46")).getSocketUserList().size() : 0;
                        return SocketMappingResponse.builder()
                                .socketRoomId(socketRoomId)
                                .socketRoomCount(exIncHubMainRoomCount + bufferUserCountDisplayTemp)
                                .processStatus(true)
                                .exIncHubGamingRoomCount(exIncHubGamingRoomCount + (bufferUserCountDisplayTemp - bufferDecrementTemp))
                                .build();
                    }

                    return SocketMappingResponse.builder()
                            .socketRoomId(socketRoomId)
                            .socketRoomCount(socketUserList.size() + bufferUserCountDisplayTemp)
                            .processStatus(true)
                            .exIncHubGamingRoomCount(exIncHubGamingRoomCount + (bufferUserCountDisplayTemp - bufferDecrementTemp))
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

    /**
     * This method is used to clean up the socket room.
     *
     * @param socketRoomId The UUID of the socket room.
     */
    private void cleanUpSocketRoom(UUID socketRoomId) {
        if (this.doesSocketRoomExist(socketRoomId)) {
            SocketSessionEntry socketSessionEntry = this.socketSessionMapping.get(socketRoomId);
            if (socketSessionEntry.getSocketUserList().isEmpty()) {
                log.info("Socket room removed: {}", socketRoomId);
                this.socketSessionMapping.remove(socketRoomId);
            }
        }
    }

    /**
     * This method is used to randomize the buffer user count.
     */

    private void randomizeBufferUserCount() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        Runnable updateBufferUserCount = () -> {
            bufferUserCountDisplayTemp = getRandomNumber(60, 80);
            bufferDecrementTemp = getRandomNumber(10, 15); // The range must not be out of bounds of the buffer user count
            log.info("Buffer user count: {}", bufferUserCountDisplayTemp);
            log.info("Buffer decrement: {}", bufferDecrementTemp);
        };

        // Initial update
        updateBufferUserCount.run();

        // Schedule the update to run every minute
        scheduler.scheduleAtFixedRate(updateBufferUserCount, 1, 1, TimeUnit.MINUTES);
    }

    private static int getRandomNumber(int min, int max) {
        Random random = new Random();
        return random.nextInt((max - min) + 1) + min;
    }

}
