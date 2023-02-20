package fi.nls.hakunapi.core.param;

import fi.nls.hakunapi.core.FeatureServiceConfig;
import fi.nls.hakunapi.core.request.GetFeatureRequest;

public interface GetFeatureParam extends APIParam {

    public void modify(FeatureServiceConfig service, GetFeatureRequest request, String value) throws IllegalArgumentException;

    /**
     * Priority will be used to order in which GetFeatureParams for one request should be processed
     * 
     * @return priority used for ordering GetFeatureParams
     */
    public default int priority() {
        return 0;
    }

}
