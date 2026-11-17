package de.unikassel.assistant.ingest;

import java.security.KeyStore;
import java.util.Arrays;

import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageFetcherTests {

	@Test
	void trustsTheExtraRootNeededForTheModuleDatabase() throws Exception {
		assertThat(PageFetcher.sslContext()).isNotNull();
		TrustManagerFactory defaults = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		defaults.init((KeyStore) null);
		long jdkRoots = Arrays.stream(defaults.getTrustManagers())
			.filter((tm) -> tm instanceof X509TrustManager)
			.mapToLong((tm) -> ((X509TrustManager) tm).getAcceptedIssuers().length)
			.sum();
		assertThat(jdkRoots).isGreaterThan(50);
		assertThat(getClass().getResource("/certs/harica-tls-rsa-root-ca-2021.pem")).isNotNull();
	}

}
