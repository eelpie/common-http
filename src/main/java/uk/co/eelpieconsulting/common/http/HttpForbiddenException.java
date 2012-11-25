package uk.co.eelpieconsulting.common.http;

public class HttpForbiddenException extends HttpFetchException {
	
	private static final long serialVersionUID = 1L;
	
	public HttpForbiddenException(String responseBody) {
		super(responseBody);
	}
	
}
