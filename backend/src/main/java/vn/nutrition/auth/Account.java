package vn.nutrition.auth;
import java.util.UUID;
public record Account(UUID id, String email, String displayName, String role, UUID sessionContext) {}
