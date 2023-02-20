package fi.nls.hakunapi.core.param;

import fi.nls.hakunapi.core.FeatureType;
import fi.nls.hakunapi.core.FeatureServiceConfig;
import fi.nls.hakunapi.core.filter.Filter;
import fi.nls.hakunapi.core.request.GetFeatureCollection;
import fi.nls.hakunapi.core.request.GetFeatureRequest;

public abstract class GetFeatureFilterParam implements GetFeatureParam {

    public void modify(FeatureServiceConfig service, GetFeatureRequest request, String value)
            throws IllegalArgumentException {
        boolean atLeastOne = false;
        for (GetFeatureCollection c : request.getCollections()) {
            Filter f = toFilter(service, c.getFt(), value);
            if (f != null) {
                c.addFilter(f);
                atLeastOne = true;
            }
        }
        if (atLeastOne) {
            request.addQueryParam(getParamName(), value);
        }
    }

    public abstract Filter toFilter(FeatureServiceConfig service, FeatureType ft, String value) throws IllegalArgumentException;

}
