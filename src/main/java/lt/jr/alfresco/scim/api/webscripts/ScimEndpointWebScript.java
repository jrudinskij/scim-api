package lt.jr.alfresco.scim.api.webscripts;

import de.captaingoldfish.scim.sdk.common.resources.ServiceProvider;
import de.captaingoldfish.scim.sdk.common.resources.complex.*;
import de.captaingoldfish.scim.sdk.common.resources.multicomplex.AuthenticationScheme;
import de.captaingoldfish.scim.sdk.common.response.ScimResponse;
import de.captaingoldfish.scim.sdk.server.endpoints.ResourceEndpoint;
import de.captaingoldfish.scim.sdk.server.endpoints.base.GroupEndpointDefinition;
import de.captaingoldfish.scim.sdk.server.endpoints.base.UserEndpointDefinition;
import lt.jr.alfresco.scim.api.hadlers.GroupHandler;
import lt.jr.alfresco.scim.api.hadlers.UserHandler;
import org.alfresco.repo.web.scripts.BufferedRequest;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;
import org.springframework.extensions.webscripts.servlet.WebScriptServletRequest;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public class ScimEndpointWebScript extends AbstractWebScript {
    
    private final Logger logger = LoggerFactory.getLogger(ScimEndpointWebScript.class);
    
    private static final String resource_uri = "/{resource}";
    private static final String resource_id_uri = "/{resource}/{id}";
    
    private UserHandler userHandler;
    private GroupHandler groupHandler;

    @Autowired
    public void setUserHandler(UserHandler userHandler) {
        this.userHandler = userHandler;
    }

    @Autowired
    public void setGroupHandler(GroupHandler groupHandler) {
        this.groupHandler = groupHandler;
    }

    @Override
    public void execute(WebScriptRequest req, WebScriptResponse res) throws IOException {
        handleRequest(req, res);
    }

    private void handleRequest(WebScriptRequest req, WebScriptResponse res) throws IOException {
        ResourceEndpoint resourceEndpoint = new ResourceEndpoint(getServiceProviderConfig());
        resourceEndpoint.registerEndpoint(new UserEndpointDefinition(userHandler));
        resourceEndpoint.registerEndpoint(new GroupEndpointDefinition(groupHandler));
        HttpServletRequest request = getServletRequest(req);
        String query = request.getQueryString() == null ? "" : "?" + request.getQueryString();
        logger.info("{} {}", request.getMethod(), request.getRequestURL().toString() + query);
        String body = getRequestBody(request);
        if(StringUtils.isNoneBlank(body)) {
            logger.info("Body: {}", body);
        }
        ScimResponse response = resourceEndpoint.handleRequest(request.getRequestURL().toString() + query,
                de.captaingoldfish.scim.sdk.common.constants.enums.HttpMethod.valueOf(request.getMethod()),
                body,
                getHttpHeaders(request));

        response.getHttpHeaders().entrySet().forEach(entry -> res.addHeader(entry.getKey(), entry.getValue()));
        Writer writer = res.getWriter();
        writer.write(response.toString());
    }
    
    private ServiceProvider getServiceProviderConfig() {
      AuthenticationScheme authScheme = AuthenticationScheme.builder()
                                                            .name("OAuth Bearer Token")
                                                            .description("Authentication scheme using the OAuth "
                                                                         + "Bearer Token Standard")
                                                            .specUri("http://www.rfc-editor.org/info/rfc6750")
                                                            .type("oauthbearertoken")
                                                            .build();
      return ServiceProvider.builder()
                            .filterConfig(FilterConfig.builder().supported(true).maxResults(50).build())
                            .sortConfig(SortConfig.builder().supported(true).build())
                            .changePasswordConfig(ChangePasswordConfig.builder().supported(true).build())
                            .bulkConfig(BulkConfig.builder().supported(true).maxOperations(10).build())
                            .patchConfig(PatchConfig.builder().supported(true).build())
                            .authenticationSchemes(Collections.singletonList(authScheme))
                            .eTagConfig(ETagConfig.builder().supported(true).build())
                            .build();
    }
    
    private HttpServletRequest getServletRequest(WebScriptRequest webScriptRequest) {
        if (webScriptRequest instanceof BufferedRequest) {
            return ((WebScriptServletRequest) ((BufferedRequest) webScriptRequest).getNext()).getHttpServletRequest();
        }
        return ((WebScriptServletRequest) webScriptRequest).getHttpServletRequest();
    }
    
    private String getRequestBody(HttpServletRequest request) {
      try (InputStream inputStream = request.getInputStream()) {
        return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new IllegalStateException(e.getMessage(), e);
      }
    }
    
    private Map<String, String> getHttpHeaders(HttpServletRequest request) {
      Map<String, String> httpHeaders = new HashMap<>();
      Enumeration<String> enumeration = request.getHeaderNames();
      while (enumeration != null && enumeration.hasMoreElements())
      {
        String headerName = enumeration.nextElement();
        httpHeaders.put(headerName, request.getHeader(headerName));
      }
      return httpHeaders;
    }

}
