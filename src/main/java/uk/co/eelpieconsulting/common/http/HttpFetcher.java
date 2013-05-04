package uk.co.eelpieconsulting.common.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.zip.GZIPInputStream;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

public class HttpFetcher {
	
	private static final Logger log = Logger.getLogger(HttpFetcher.class);
	
	private static final String UTF_8 = "UTF-8";
	private static final String GZIP = "gzip";
	private static final String ACCEPT_ENCODING = "Accept-Encoding";
	private static final int HTTP_TIMEOUT = 15000;
	
	private HttpParams params;
	private ClientConnectionManager connectionManager;

	public HttpFetcher() {
		params = new BasicHttpParams();
	    SchemeRegistry registry = new SchemeRegistry();
	    registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
	    SSLSocketFactory sslSocketFactory = SSLSocketFactory.getSocketFactory();
	    sslSocketFactory.setHostnameVerifier(new AllowAllHostnameVerifier());
		registry.register(new Scheme("https",sslSocketFactory, 443));
	    
	    connectionManager = new ThreadSafeClientConnManager(params, registry);
	}
	
	public String get(String url) throws HttpNotFoundException, HttpBadRequestException, HttpForbiddenException, HttpFetchException {
		log.info("Executing GET to: " + url);
		return executeRequestAndReadResponseBody(new HttpGet(url));
	}
	
	public String post(HttpPost post) throws HttpNotFoundException, HttpBadRequestException, HttpForbiddenException, HttpFetchException {
		log.info("Executing POST to: " + post.getURI());
		return executeRequestAndReadResponseBody(post);		
	}
	
	public byte[] getBytes(String url) throws HttpNotFoundException, HttpBadRequestException, HttpForbiddenException, HttpFetchException {
		log.info("Executing GET to: " + url);
		return executeRequestAndReadBytes(new HttpGet(url));
	}
	
	private String executeRequestAndReadResponseBody(final HttpRequestBase get) throws HttpNotFoundException, HttpBadRequestException, HttpForbiddenException, HttpFetchException {		
		final byte[] responseBytes = executeRequestAndReadBytes(get);
		try {
			return new String(responseBytes, UTF_8);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}
	
	private byte[] executeRequestAndReadBytes(final HttpRequestBase request) throws HttpNotFoundException, HttpBadRequestException, HttpForbiddenException, HttpFetchException  {
		try {
			final HttpResponse response = executeRequest(request);
			final int statusCode = response.getStatusLine().getStatusCode();
			log.debug("Http response status code is: " + statusCode);

			if (statusCode == HttpStatus.SC_OK) {
				final byte[] byteArray = EntityUtils.toByteArray(response.getEntity());
				EntityUtils.consume(response.getEntity());
				return byteArray;
			}
			
			final String responseBody = EntityUtils.toString(response.getEntity());
			if (statusCode == HttpStatus.SC_NOT_FOUND) {
				EntityUtils.consume(response.getEntity());
				throw new HttpNotFoundException(responseBody);
				
			} else if (statusCode == HttpStatus.SC_BAD_REQUEST) {
				EntityUtils.consume(response.getEntity());
				throw new HttpBadRequestException(responseBody);
				
			} else if (statusCode == HttpStatus.SC_FORBIDDEN) {
				EntityUtils.consume(response.getEntity());
				throw new HttpForbiddenException(responseBody);
			}
			
			EntityUtils.consume(response.getEntity());
			throw new HttpFetchException(responseBody);
			
		} catch (IOException e) {
			log.debug("Throwing general http fetch io exception", e);
			throw new HttpFetchException(e);
		}
	}
	
	private HttpResponse executeRequest(HttpRequestBase request) throws IOException, ClientProtocolException {
		request.addHeader(new BasicHeader(ACCEPT_ENCODING, GZIP));
		return setupHttpClient().execute(request);
	}
	
	private HttpClient setupHttpClient() {
	    HttpClient client = new DefaultHttpClient(connectionManager, params);	    
		((AbstractHttpClient) client)
				.addRequestInterceptor(new HttpRequestInterceptor() {
					public void process(final HttpRequest request,
							final HttpContext context) throws HttpException,
							IOException {
						if (!request.containsHeader(ACCEPT_ENCODING)) {
							request.addHeader(ACCEPT_ENCODING, GZIP);
						}
					}
				});
		
		((AbstractHttpClient) client).addResponseInterceptor(new HttpResponseInterceptor() {
			public void process(final HttpResponse response, final HttpContext context) throws HttpException, IOException {
				HttpEntity entity = response.getEntity();
				Header ceheader = entity.getContentEncoding();
				if (ceheader != null) {
					HeaderElement[] codecs = ceheader.getElements();
					for (int i = 0; i < codecs.length; i++) {
						if (codecs[i].getName().equalsIgnoreCase(GZIP)) {
							response.setEntity(new GzipDecompressingEntity(response.getEntity()));
							return;
						}
					}
				}
			}
		});
		
		client.getParams().setParameter(CoreConnectionPNames.SO_TIMEOUT, new Integer(HTTP_TIMEOUT));
		client.getParams().setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, new Integer(HTTP_TIMEOUT));
		return client;
	}

	private static class GzipDecompressingEntity extends HttpEntityWrapper {

		public GzipDecompressingEntity(final HttpEntity entity) {
			super(entity);
		}

		@Override
		public InputStream getContent() throws IOException, IllegalStateException {
			InputStream wrappedin = wrappedEntity.getContent();
			return new GZIPInputStream(wrappedin);
		}
		
		@Override
		public long getContentLength() {
			return this.wrappedEntity.getContentLength();
		}
	}

}
