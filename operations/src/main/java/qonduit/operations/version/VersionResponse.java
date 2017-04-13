package qonduit.operations.version;

import qonduit.operation.OperationResponse;

public class VersionResponse extends OperationResponse {

    public static final String VERSION;
    static {
        String ver = VersionResponse.class.getPackage().getImplementationVersion();
        VERSION = (null == ver) ? "Unknown" : ver;
    }

    public VersionResponse() {
        super();
    }

    /**
     * This property exists for JSON deserialization
     */
    private String version = VERSION;

    public String getVersion() {
        return version;
    }

}
