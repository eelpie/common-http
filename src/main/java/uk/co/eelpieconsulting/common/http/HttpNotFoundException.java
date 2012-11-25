package uk.co.eelpieconsulting.common.http;

public class HttpNotFoundException extends HttpFetchException {

	private static final long serialVersionUID = 1L;
	
	public HttpNotFoundException(String responseBody) {
		super(responseBody);		
	}
	
}
