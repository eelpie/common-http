package uk.co.eelpieconsulting.common.http;

import java.io.IOException;
import java.io.InputStream;
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
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

public class HttpFetcher {
	
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
	    connectionManager = new ThreadSafeClientConnManager(params, registry);
	}
	
	public String get(String url) throws HttpFetchException {
		final HttpGet get = new HttpGet(url);
		return executeRequestAndReadResponseBody(get);
	}
	
	public String post(HttpPost post) throws HttpFetchException {
		return executeRequestAndReadResponseBody(post);		
	}
	
	private String executeRequestAndReadResponseBody(final HttpRequestBase get) throws HttpFetchException {
		try {
			get.addHeader(new BasicHeader(ACCEPT_ENCODING, GZIP));

			final HttpResponse response = executeRequest(get);
			final int statusCode = response.getStatusLine().getStatusCode();
			if (statusCode == HttpStatus.SC_OK) {
				return EntityUtils.toString(response.getEntity(), UTF_8);
			}
			
			EntityUtils.consume(response.getEntity());
			throw new HttpFetchException(new RuntimeException("Non 200 http response code: " + statusCode));
			
		} catch (Exception e) {
			throw new HttpFetchException(e);
		}
	}
	
	private HttpResponse executeRequest(HttpRequestBase request) throws IOException, ClientProtocolException {
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
		
		client.getParams().setParameter("http.socket.timeout", new Integer(HTTP_TIMEOUT));	// TODO Is there an apache http constant for this?
		client.getParams().setParameter("http.connection.timeout", new Integer(HTTP_TIMEOUT));
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
