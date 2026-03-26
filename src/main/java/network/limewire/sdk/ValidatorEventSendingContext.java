package network.limewire.sdk;

import network.limewire.sdk.footer.FooterSession;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpResponse;

import java.util.function.Supplier;

class ValidatorEventSendingContext {
    private SdkHttpFullRequest request;
    private SdkHttpResponse responseRef;
    private Supplier<byte[]> bodySupplier;
    private Supplier<FooterSession.ValidatorPayload> footerSupplier;
    private boolean shouldBufferResponse;

    public SdkHttpFullRequest getRequest() {
        return request;
    }

    public void setRequest(SdkHttpFullRequest request) {
        this.request = request;
    }

    public SdkHttpResponse getResponseRef() {
        return responseRef;
    }

    public void setResponseRef(SdkHttpResponse responseRef) {
        this.responseRef = responseRef;
    }

    public Supplier<byte[]> getBodySupplier() {
        return bodySupplier;
    }

    public void setBodySupplier(Supplier<byte[]> bodySupplier) {
        this.bodySupplier = bodySupplier;
    }

    public Supplier<FooterSession.ValidatorPayload> getFooterSupplier() {
        return footerSupplier;
    }

    public void setFooterSupplier(Supplier<FooterSession.ValidatorPayload> footerSupplier) {
        this.footerSupplier = footerSupplier;
    }

    public boolean isShouldBufferResponse() {
        return shouldBufferResponse;
    }

    public void setShouldBufferResponse(boolean shouldBufferResponse) {
        this.shouldBufferResponse = shouldBufferResponse;
    }
}
