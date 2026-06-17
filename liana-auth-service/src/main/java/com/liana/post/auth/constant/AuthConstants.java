package com.liana.post.auth.constant;

public interface AuthConstants {
    String ROLE_CLERK = "CLERK";
    String ROLE_MANAGER = "MANAGER";
    String ROLE_SORTER = "SORTER";
    String ROLE_ADMIN = "ADMIN";

    String CLAIM_USER_ID = "user_id";
    String CLAIM_USERNAME = "username";
    String CLAIM_DISPLAY_NAME = "display_name";
    String CLAIM_ROLE = "role";
    String CLAIM_FACILITY_CODE = "facility_code";
    String CLAIM_PERMISSIONS = "permissions";

    String JWT_ISSUER = "Liana-Post-System";
    String JWT_SUBJECT_PREFIX = "Liana-Post-User";
}
