package fi.nls.hakunapi.simple.servlet.operation;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fi.nls.hakunapi.core.FeatureProducer;
import fi.nls.hakunapi.core.FeatureStream;
import fi.nls.hakunapi.core.FeatureType;
import fi.nls.hakunapi.core.FeatureWriter;
import fi.nls.hakunapi.core.OutputFormat;
import fi.nls.hakunapi.core.SingleFeatureWriter;
import fi.nls.hakunapi.core.ValueProvider;
import fi.nls.hakunapi.core.WFS3Service;
import fi.nls.hakunapi.core.filter.Filter;
import fi.nls.hakunapi.core.operation.DynamicPathOperation;
import fi.nls.hakunapi.core.operation.DynamicResponseOperation;
import fi.nls.hakunapi.core.param.APIParam;
import fi.nls.hakunapi.core.param.CrsParam;
import fi.nls.hakunapi.core.param.FParam;
import fi.nls.hakunapi.core.param.GetFeatureParam;
import fi.nls.hakunapi.core.property.HakunaProperty;
import fi.nls.hakunapi.core.request.GetFeatureCollection;
import fi.nls.hakunapi.core.request.GetFeatureRequest;
import fi.nls.hakunapi.core.schemas.Link;
import fi.nls.hakunapi.core.util.CrsUtil;
import fi.nls.hakunapi.core.util.Links;
import fi.nls.hakunapi.geojson.FeatureGeoJSON;
import fi.nls.hakunapi.simple.servlet.ResponseUtil;
import fi.nls.hakunapi.simple.servlet.operation.param.SimplePathParam;

@Path("/collections")
public class GetCollectionItemByIdOperation implements DynamicPathOperation, DynamicResponseOperation {

    protected static final Logger LOG = LoggerFactory.getLogger(GetCollectionItemByIdOperation.class);

    public static final List<GetFeatureParam> NON_DYNAMIC = Arrays.asList(
            new FParam(),
            new CrsParam()
    );

    @Inject
    protected WFS3Service service;

    @Override
    public List<String> getValidPaths(WFS3Service service) {
        Collection<FeatureType> collections = service.getCollections();
        List<String> paths = new ArrayList<>(collections.size());
        for (FeatureType collection : collections) {
            paths.add("/collections/" + collection.getName() + "/items/{featureId}");
        }
        return paths;
    }

    @Override
    public List<APIParam> getParameters(String path, WFS3Service service) {
        List<APIParam> parameters = new ArrayList<>();
        parameters.add(new SimplePathParam("featureId"));
        parameters.addAll(NON_DYNAMIC);
        return parameters;
    }

    @Override
    public Map<String, Class<?>> getResponsesByContentType(WFS3Service service) {
        Map<String, Class<?>> map = new HashMap<>();
        for (OutputFormat f : service.getOutputFormats()) {
            map.put(f.getMimeType(), FeatureGeoJSON.class);
        }
        return map;
    }

    @GET
    @Path("/{collectionId}/items/{featureId}")
    public Response handle(
            @PathParam("collectionId") String collectionId,
            @PathParam("featureId") String featureId,
            @Context UriInfo uriInfo,
            @Context Request wsRequest,
            @Context HttpHeaders headers) {
        FeatureType ft;
        GetFeatureCollection c;
        GetFeatureRequest request;
        try {
            ft = service.getCollection(collectionId);
            if (ft == null) {
                return ResponseUtil.exception(Status.BAD_REQUEST, "Unknown collection");
            }

            c = new GetFeatureCollection(ft);

            request = new GetFeatureRequest();
            request.setFormat(OperationUtil.determineOutputFormat(wsRequest, service.getOutputFormats()));
            request.addCollection(c);
            request.addPathParam("collectionId", collectionId);
            
            c.addFilter(Filter.equalTo(ft.getId(), featureId));
            request.addPathParam("featureId", featureId);

            OperationUtil.getQueryParams(service, uriInfo).forEach((k, v) -> request.addQueryParam(k, v));

            GetFeaturesUtil.modify(service, request, NON_DYNAMIC, uriInfo.getQueryParameters());

            GetCollectionItemsOperation.checkUnknownParameters(service, NON_DYNAMIC, uriInfo.getQueryParameters());
        } catch (IllegalArgumentException e) {
            return ResponseUtil.exception(Status.BAD_REQUEST, e.getMessage());
        }
        try (SingleFeatureWriter writer = request.getFormat().getSingleFeatureWriter()) {
            return getResponse(writer, ft.getFeatureProducer(), request, c);
        } catch (Exception e) {
            LOG.warn(e.getMessage(), e);
            return ResponseUtil.exception(Status.INTERNAL_SERVER_ERROR,
                    "Error occured, message: " + e.getMessage());
        }
    }

    public Response getResponse(SingleFeatureWriter writer, FeatureProducer producer,
            GetFeatureRequest request, GetFeatureCollection c) throws Exception {
        ValueProvider feature = null;
        try (FeatureStream features = producer.getFeatures(request, c)) {
            if (!features.hasNext()) {
                return ResponseUtil.exception(Status.NOT_FOUND, "Feature not found");
            }
            feature = features.next();
        }

        int srid = request.getSRID();

        // Feature response rarely needs 8kb of memory
        // Let's allocate a little less
        ByteArrayOutputStream baos = new ByteArrayOutputStream(2048);
        writer.init(baos, CrsUtil.getMaxDecimalCoordinates(srid), srid);
        if (c.getFt().getGeom() != null) {
            writer.initGeometryWriter(CrsUtil.getGeomDimensionForSrid(c.getFt().getGeomDimension(), srid));
        }

        int i = 0;
        for (HakunaProperty prop : c.getProperties()) {
            prop.write(feature, i++, writer);
        }

        writer.endFeature();
        writer.end(true, getLinks(request, writer), 1);
        writer.close();

        ResponseBuilder builder = Response.ok();
        request.getResponseHeaders().forEach((k, v) -> builder.header(k, v));
        request.getFormat().getResponseHeaders(request).forEach((k, v) -> builder.header(k, v));
        builder.entity(baos.toByteArray());
        return builder.build();
    }

    public List<Link> getLinks(GetFeatureRequest request, FeatureWriter writer) {
        String collectionId = request.getPathParam("collectionId");
        String featureId = request.getPathParam("featureId");
        String mimeType = writer.getMimeType();

        String itemsPath = Links.getItemsPath(service.getCurrentServerURL(), collectionId);
        String featurePath = itemsPath + "/" + featureId;

        return Arrays.asList(
                Links.getSelfLink(featurePath, null, mimeType),
                Links.getCollectionLink(itemsPath, null, mimeType)
        );
    }

}

