package com.zhwesh.apitest;

import com.zhwesh.apitest.support.ApiClient;
import com.zhwesh.apitest.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用户接口测试
 */
class UserApiTest extends ApiTestBase {

    @Test
    void sendCode() {
        JsonNode response = api.sendUserCode(PHONE);
        assertThat(response.path("code").asInt()).isEqualTo(1);
        assertThat(redisTemplate.opsForValue().get("sms:code:" + PHONE)).isNotBlank();
    }

    @Test
    void register() {
        String phone = "13900000001";
        String code = sendAndReadUserCode(phone);
        JsonNode response = api.register(ApiClient.body(
            "phone", phone, "code", code, "username", "apitester",
            "password", "123456", "name", "接口测试", "idNumber", "110101199001011234"));
        assertThat(response.path("code").asInt()).isEqualTo(1);
        assertThat(response.path("data").path("token").asString()).isNotBlank();
        assertThat(response.path("data").path("username").asString()).isEqualTo("apitester");
    }

    @Test
    void loginUsername() {
        JsonNode response = api.loginUsername("admin", "123456");
        assertThat(response.path("code").asInt()).isEqualTo(1);
        assertThat(response.path("data").path("token").asString()).isNotBlank();

        JsonNode wrong = api.loginUsername("admin", "bad-password");
        assertThat(wrong.path("code").asInt()).isEqualTo(0);
    }

    @Test
    void loginPhone() {
        String code = sendAndReadUserCode(PHONE);
        JsonNode response = api.loginPhone(PHONE, code);
        assertThat(response.path("code").asInt()).isEqualTo(1);

        JsonNode reused = api.loginPhone(PHONE, code);
        assertThat(reused.path("code").asInt()).isEqualTo(0);
    }

    @Test
    void currentUser() {
        JsonNode response = api.currentUser();
        assertThat(response.path("code").asInt()).isEqualTo(1);
        assertThat(response.path("data").path("username").asString()).isEqualTo("admin");
        assertThat(response.path("data").path("phone").asString()).isEqualTo(PHONE);
    }

    @Test
    void updateUser() {
        String code = sendAndReadUserCode(PHONE);
        JsonNode response = api.updateUser(ApiClient.body(
            "phone", PHONE, "code", code, "image", "https://example.com/avatar.png"));
        assertThat(response.path("code").asInt()).isEqualTo(1);
        assertThat(api.currentUser().path("data").path("image").asString())
            .contains("avatar.png");
    }

    @Test
    void uploadAvatar() {
        JsonNode response = api.uploadAvatar(AVATAR_PNG);
        org.junit.jupiter.api.Assumptions.assumeTrue(
            response.path("code").asInt() == 1,
            "OSS 上传失败，跳过头像断言：" + response);
        assertThat(response.path("data").asString()).startsWith("https://");
    }

    @Test
    void resetPassword() {
        String code = sendAndReadUserCode(PHONE);
        JsonNode response = api.resetPassword(ApiClient.body(
            "phone", PHONE, "code", code, "newPassword", "123456"));
        assertThat(response.path("code").asInt()).isEqualTo(1);
        assertThat(api.loginUsername("admin", "123456").path("code").asInt()).isEqualTo(1);
    }
}
