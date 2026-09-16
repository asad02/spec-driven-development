package com.example.featuretoggle.admin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every endpoint against every role. This is the suite that catches an endpoint
 * added without a role check — a failure a UI cannot reveal, because the UI simply
 * does not render the control it never had.
 */
@DisplayName("admin API: role matrix")
class FeatureAdminRoleMatrixIT extends AbstractAdminIT {

    @Test
    @DisplayName("an administrator may read and write everything")
    void adminHasFullAccess() {
        assertThat(send("GET", FEATURES, adminToken).statusCode()).isEqualTo(200);
        assertThat(send("GET", FEATURES + "/audit", adminToken).statusCode()).isEqualTo(200);

        String uid = "matrix-" + System.nanoTime();
        assertThat(send("POST", FEATURES, adminToken,
                "{\"uid\":\"" + uid + "\",\"description\":\"d\",\"enabled\":false,\"roles\":[]}")
                .statusCode()).isEqualTo(201);
        assertThat(send("POST", FEATURES + "/" + uid + "/roles/ROLE_VIEWER", adminToken).statusCode()).isEqualTo(200);
        assertThat(send("DELETE", FEATURES + "/" + uid + "/roles/ROLE_VIEWER", adminToken).statusCode()).isEqualTo(200);
        assertThat(send("PUT", FEATURES + "/" + uid + "/properties/note", adminToken, "{\"value\":\"hello\"}")
                .statusCode()).isEqualTo(200);
        assertThat(send("DELETE", FEATURES + "/" + uid, adminToken).statusCode()).isEqualTo(204);
    }

    @Test
    @DisplayName("a read-only operator may read, and is refused every mutation")
    void viewerIsReadOnly() {
        assertThat(send("GET", FEATURES, viewerToken).statusCode()).isEqualTo(200);
        assertThat(send("GET", FEATURES + "/audit", viewerToken).statusCode()).isEqualTo(200);
        assertThat(send("GET", FEATURES + "/" + PROTECTED_FEATURE, viewerToken).statusCode()).isEqualTo(200);

        assertThat(send("POST", FEATURES, viewerToken,
                "{\"uid\":\"viewer-must-not-create\",\"description\":\"\",\"enabled\":false,\"roles\":[]}")
                .statusCode()).isEqualTo(403);
        assertThat(send("PUT", FEATURES + "/" + PROTECTED_FEATURE, viewerToken,
                "{\"uid\":\"" + PROTECTED_FEATURE + "\",\"description\":\"\",\"enabled\":false,\"roles\":[]}")
                .statusCode()).isEqualTo(403);
        assertThat(send("DELETE", FEATURES + "/" + PROTECTED_FEATURE, viewerToken).statusCode()).isEqualTo(403);
        assertThat(send("POST", FEATURES + "/" + PROTECTED_FEATURE + "/roles/ROLE_NONE", viewerToken)
                .statusCode()).isEqualTo(403);
        assertThat(send("DELETE", FEATURES + "/" + PROTECTED_FEATURE + "/roles/ROLE_VIEWER", viewerToken)
                .statusCode()).isEqualTo(403);
        assertThat(send("PUT", FEATURES + "/" + PROTECTED_FEATURE + "/properties/x", viewerToken, "{\"value\":\"y\"}")
                .statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("a refused mutation changes nothing")
    void refusedMutationIsNotApplied() {
        String before = send("GET", FEATURES + "/" + PROTECTED_FEATURE, adminToken).body();
        send("DELETE", FEATURES + "/" + PROTECTED_FEATURE + "/roles/ROLE_VIEWER", viewerToken);
        String after = send("GET", FEATURES + "/" + PROTECTED_FEATURE, adminToken).body();
        assertThat(after).isEqualTo(before);
    }

    @Test
    @DisplayName("an operator with neither role is refused even the listing")
    void noAccessIsRefusedEverything() {
        assertThat(send("GET", FEATURES, noAccessToken).statusCode()).isEqualTo(403);
        assertThat(send("GET", FEATURES + "/audit", noAccessToken).statusCode()).isEqualTo(403);
        assertThat(send("GET", FEATURES + "/" + PROTECTED_FEATURE, noAccessToken).statusCode()).isEqualTo(403);
        assertThat(send("POST", FEATURES, noAccessToken,
                "{\"uid\":\"nope\",\"description\":\"\",\"enabled\":false,\"roles\":[]}").statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("a refusal names no features, so it cannot be used to enumerate them")
    void refusalLeaksNothing() {
        String body = send("GET", FEATURES, noAccessToken).body();
        assertThat(body).doesNotContain(PROTECTED_FEATURE).doesNotContain("use-springboot-backend");
    }

    @Test
    @DisplayName("no token is unauthenticated, not forbidden — the distinction matters")
    void anonymousIsUnauthenticated() {
        assertThat(send("GET", FEATURES, null).statusCode()).isEqualTo(401);
    }
}
