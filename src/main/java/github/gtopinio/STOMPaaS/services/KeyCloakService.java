package github.gtopinio.STOMPaaS.services;

import jakarta.ws.rs.core.Response;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KeyCloakService {
    @Value("${keycloak.auth-server-url}")
    private String authServerUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.resource}")
    private String clientId;

    @Value("${keycloak.credentials.secret}")
    private String clientSecret;

    private final List<Integer> successCodes = List.of(200, 201, 202, 204);

    private Keycloak getKeycloakInstance() {
        return KeycloakBuilder.builder()
                .serverUrl(authServerUrl)
                .realm(realm)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .grantType("client_credentials")
                .build();
    }

    public List<UserRepresentation> getUsers() {
        return getKeycloakInstance().realm(realm).users().list();
    }

    public void createUser(UserRepresentation user) {

        try (Response response = getKeycloakInstance().realm(realm).users().create(user)) {
            if (!successCodes.contains(response.getStatus())) {
                throw new RuntimeException("Failed to create user: " + response.getStatus());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create user: " + e.getMessage());
        }
    }
}
