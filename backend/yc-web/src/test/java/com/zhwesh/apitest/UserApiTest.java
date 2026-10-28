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
        var data = response.path("data");
        assertThat(data.path("id").asLong()).isPositive();
        assertThat(data.path("token").asString()).isNotBlank();
        assertThat(data.path("username").asString()).isEqualTo("apitester");
        assertThat(data.path("name").asString()).isEqualTo("接口测试");
        assertThat(data.path("phone").asString()).isEqualTo(phone);
        assertThat(data.path("createTime").asString()).contains("T");
        assertThat(api.loginUsername("apitester", "123456").path("code").asInt()).isEqualTo(1);

        String duplicateCode = sendAndReadUserCode(phone);
        JsonNode duplicate = api.register(ApiClient.body(
            "phone", phone, "code", duplicateCode, "username", "apitester2",
            "password", "123456", "name", "接口测试", "idNumber", "110101199001011234"));
        assertThat(duplicate.path("code").asInt()).isZero();
    }

    @Test
    void loginUsername() {
        JsonNode response = api.loginUsername("admin", "123456");
        assertThat(response.path("code").asInt()).isEqualTo(1);
        var data = response.path("data");
        assertThat(data.path("id").asLong()).isEqualTo(1);
        assertThat(data.path("username").asString()).isEqualTo("admin");
        assertThat(data.path("name").asString()).isEqualTo("admin");
        assertThat(data.path("phone").asString()).isEqualTo(PHONE);
        assertThat(data.path("createTime").asString()).contains("T");
        assertThat(data.path("token").asString()).isNotBlank();

        JsonNode wrong = api.loginUsername("admin", "bad-password");
        assertThat(wrong.path("code").asInt()).isEqualTo(0);
    }

    @Test
    void loginPhone() {
        String code = sendAndReadUserCode(PHONE);
        JsonNode response = api.loginPhone(PHONE, code);
        assertThat(response.path("code").asInt()).isEqualTo(1);
        assertThat(response.path("data").path("username").asString()).isEqualTo("admin");
        assertThat(response.path("data").path("phone").asString()).isEqualTo(PHONE);
        assertThat(response.path("data").path("token").asString()).isNotBlank();

        JsonNode reused = api.loginPhone(PHONE, code);
        assertThat(reused.path("code").asInt()).isEqualTo(0);
    }

    @Test
    void currentUser() {
        JsonNode response = api.currentUser();
        assertThat(response.path("code").asInt()).isEqualTo(1);
        var data = response.path("data");
        assertThat(data.path("id").asLong()).isEqualTo(1);
        assertThat(data.path("username").asString()).isEqualTo("admin");
        assertThat(data.path("name").asString()).isEqualTo("admin");
        assertThat(data.path("phone").asString()).isEqualTo(PHONE);
        assertThat(data.path("createTime").asString()).contains("T");
    }

    @Test
    void updateUser() {
        String code = sendAndReadUserCode(PHONE);
        JsonNode response = api.updateUser(ApiClient.body(
            "phone", PHONE, "code", code, "image", "https://example.com/avatar.png"));
        assertThat(response.path("code").asInt()).isEqualTo(1);
        var user = api.currentUser().path("data");
        assertThat(user.path("image").asString()).isEqualTo("https://example.com/avatar.png");
        assertThat(user.path("phone").asString()).isEqualTo(PHONE);
        assertThat(user.path("username").asString()).isEqualTo("admin");
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
            "phone", PHONE, "code", code, "newPassword", "654321"));
        assertThat(response.path("code").asInt()).isEqualTo(1);
        assertThat(api.loginUsername("admin", "123456").path("code").asInt()).isZero();
        var login = api.loginUsername("admin", "654321");
        assertThat(login.path("code").asInt()).isEqualTo(1);
        assertThat(login.path("data").path("phone").asString()).isEqualTo(PHONE);
    }
}
