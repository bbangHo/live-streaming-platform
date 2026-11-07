package com.livestream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
public abstract class ApiTestSupport {
    @Autowired
    protected MockMvc mvc;

    protected static String uniqueName() {
        return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    protected MvcResult postJson(String url, String json) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
    }

    /** 새 사용자를 가입·로그인시키고 로그인 응답 본문을 돌려준다. */
    protected String signupAndLogin() throws Exception {
        String name = uniqueName();
        String body = "{\"username\":\"" + name + "\",\"password\":\"password123\"}";
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    protected String accessTokenOfNewUser() throws Exception {
        return JsonPath.read(signupAndLogin(), "$.accessToken");
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}
