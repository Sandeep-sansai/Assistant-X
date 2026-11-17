package de.unikassel.assistant.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("assistant")
public record AssistantProperties(String adminToken, Llm llm, Claude claude, OpenAiCompatible openaiCompatible,
		Retrieval retrieval, Crawler crawler) {

	/** @param provider "anthropic" or "openai-compatible" */
	public record Llm(String provider) {
	}

	public record Claude(String model, String effort, long maxTokens, boolean fallbacks) {
	}

	public record OpenAiCompatible(String baseUrl, String apiKey, String model, int maxTokens, Duration timeout) {
	}

	public record Retrieval(int topK) {
	}

	public record Crawler(String userAgent, Duration delay, int maxPages, int maxDepth, List<String> sitemaps,
			List<String> includePrefixes, List<String> seeds, List<String> followPatterns) {

		public Crawler {
			sitemaps = sitemaps == null ? List.of() : sitemaps;
			includePrefixes = includePrefixes == null ? List.of() : includePrefixes;
			seeds = seeds == null ? List.of() : seeds;
			followPatterns = followPatterns == null ? List.of() : followPatterns;
		}

	}

}
