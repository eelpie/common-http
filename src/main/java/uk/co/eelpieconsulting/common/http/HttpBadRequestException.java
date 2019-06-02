package uk.co.eelpieconsulting.common.http;

public class HttpBadRequestException extends HttpFetchException {

    private static final long serialVersionUID = 1L;

    public HttpBadRequestException(String responseBody) {
        super(responseBody);
    }

}
