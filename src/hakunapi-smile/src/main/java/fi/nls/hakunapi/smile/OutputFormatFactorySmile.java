package fi.nls.hakunapi.smile;

import java.util.Map;

import fi.nls.hakunapi.core.OutputFormat;
import fi.nls.hakunapi.core.OutputFormatFactorySpi;

public class OutputFormatFactorySmile implements OutputFormatFactorySpi {

    @Override
    public boolean canCreate(Map<String, String> params) {
        return OutputFormatSmile.ID.equals(params.get("type"));
    }

    @Override
    public OutputFormat create(Map<String, String> params) {
        return OutputFormatSmile.INSTANCE;
    }

}
