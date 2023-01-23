package fi.nls.hakunapi.simple.servlet.operation;

import java.util.List;

import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import fi.nls.hakunapi.core.WFS3Service;
import fi.nls.hakunapi.core.operation.OperationImpl;
import fi.nls.hakunapi.html.model.HTMLContext;
import io.swagger.v3.oas.models.OpenAPI;

@Singleton
@Path("/")
public class OpenAPI30ApiOperation {

    private final WFS3Service service;
    private final OpenAPI30Generator api;

    public OpenAPI30ApiOperation(WFS3Service service, List<OperationImpl> opToImpl) {
        this.service = service;
        this.api = new OpenAPI30Generator(service, opToImpl);
    }

    @GET
    @Path("/api")
    @Produces("application/vnd.oai.openapi+json;version=3.0")
    public OpenAPI json() {
        return api.create();
    }

    @GET
    @Path("/api.json")
    @Produces("application/vnd.oai.openapi+json;version=3.0")
    public OpenAPI jsonExt() {
        return api.create();
    }
    
    @GET
    @Path("/api")
    @Produces(MediaType.TEXT_HTML)
    public HTMLContext<OpenAPI> html() {
        return new HTMLContext<>(service, api.create());
    }
    
    @GET
    @Path("/api.html")
    @Produces(MediaType.TEXT_HTML)
    public HTMLContext<OpenAPI> htmlExt() {
        return new HTMLContext<>(service, api.create());
    }

}
