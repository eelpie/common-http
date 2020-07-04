package uk.co.eelpieconsulting.common.http;

public class HttpPreconditionFailedException extends HttpFetchException {

    private static final long serialVersionUID = 1L;

    public HttpPreconditionFailedException(String responseBody) {
        super(responseBody);
    }

}
