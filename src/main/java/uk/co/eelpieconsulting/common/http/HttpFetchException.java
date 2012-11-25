package uk.co.eelpieconsulting.common.http;

public class HttpFetchException extends Exception {

	private static final long serialVersionUID = 1L;
	
	private String responseBody;
	private Exception cause;
	
	public HttpFetchException() {
	}

	public HttpFetchException(String responseBody) {
		this.responseBody = responseBody;
	}
	
	public HttpFetchException(Exception cause) {
		this.cause = cause;
	}

	public String getResponseBody() {
		return responseBody;
	}

	public Exception getCause() {
		return cause;
	}
	
}
