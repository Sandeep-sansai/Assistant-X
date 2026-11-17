package de.unikassel.assistant.ingest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Optional;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import de.unikassel.assistant.config.AssistantProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/** Fetches pages politely: one request at a time, with a fixed delay between requests. */
@Component
public class PageFetcher {

	private static final Logger log = LoggerFactory.getLogger(PageFetcher.class);

	private static final int MAX_BODY_BYTES = 20 * 1024 * 1024;

	private final HttpClient http = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NORMAL)
		.connectTimeout(Duration.ofSeconds(15))
		.sslContext(sslContext())
		.build();

	private final String userAgent;

	private final Duration delay;

	private long lastRequestNanos;

	/**
	 * Java's own trusted roots plus the extra ones in {@code certs/*.pem}. The university's module
	 * database uses "HARICA TLS RSA Root CA 2021", which current JDKs do not ship yet (browsers and
	 * macOS do); without it every request there fails.
	 */
	static SSLContext sslContext() {
		try {
			TrustManagerFactory defaults = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			defaults.init((KeyStore) null);
			KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
			store.load(null, null);
			int i = 0;
			for (TrustManager tm : defaults.getTrustManagers()) {
				if (tm instanceof X509TrustManager x509) {
					for (X509Certificate cert : x509.getAcceptedIssuers()) {
						store.setCertificateEntry("jdk-" + i++, cert);
					}
				}
			}
			CertificateFactory factory = CertificateFactory.getInstance("X.509");
			for (Resource pem : new PathMatchingResourcePatternResolver().getResources("classpath:certs/*.pem")) {
				try (InputStream in = pem.getInputStream()) {
					for (Certificate cert : factory.generateCertificates(in)) {
						store.setCertificateEntry("extra-" + i++, cert);
					}
				}
			}
			TrustManagerFactory combined = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			combined.init(store);
			SSLContext context = SSLContext.getInstance("TLS");
			context.init(null, combined.getTrustManagers(), null);
			return context;
		}
		catch (Exception ex) {
			throw new IllegalStateException("Could not set up TLS trust", ex);
		}
	}

	PageFetcher(AssistantProperties props) {
		this.userAgent = props.crawler().userAgent();
		this.delay = props.crawler().delay();
	}

	/** Returns the page, or empty if the server answered with a non-200 status. */
	public synchronized Optional<FetchedPage> fetch(String url) throws IOException, InterruptedException {
		long waitNanos = this.lastRequestNanos + this.delay.toNanos() - System.nanoTime();
		if (waitNanos > 0) {
			Thread.sleep(Duration.ofNanos(waitNanos));
		}
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(60))
				.header("User-Agent", this.userAgent)
				.header("Accept-Language", "de,en;q=0.8")
				.GET()
				.build();
			HttpResponse<byte[]> response = this.http.send(request, HttpResponse.BodyHandlers.ofByteArray());
			if (response.statusCode() != 200) {
				log.info("Skipping {}: HTTP {}", url, response.statusCode());
				return Optional.empty();
			}
			if (response.body().length > MAX_BODY_BYTES) {
				log.info("Skipping {}: body too large ({} bytes)", url, response.body().length);
				return Optional.empty();
			}
			String contentType = response.headers().firstValue("Content-Type").orElse("");
			return Optional.of(new FetchedPage(response.uri().toString(), contentType, response.body()));
		}
		finally {
			this.lastRequestNanos = System.nanoTime();
		}
	}

	public record FetchedPage(String url, String contentType, byte[] body) {

		boolean isPdf() {
			return this.contentType.startsWith("application/pdf") || this.url.toLowerCase().endsWith(".pdf");
		}

		boolean isHtml() {
			return this.contentType.startsWith("text/html");
		}

	}

}
