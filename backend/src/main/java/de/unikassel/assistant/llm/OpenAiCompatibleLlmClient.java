package de.unikassel.assistant.llm;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.unikassel.assistant.config.AssistantProperties;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Any server that speaks the OpenAI chat completions API: a local Ollama (the default, free and
 * offline) or a hosted service with such an endpoint.
 */
@Component
@ConditionalOnProperty(name = "assistant.llm.provider", havingValue = "openai-compatible")
class OpenAiCompatibleLlmClient implements LlmClient {

	private final AssistantProperties.OpenAiCompatible config;

	private final RestClient http;

	OpenAiCompatibleLlmClient(AssistantProperties props) {
		this.config = props.openaiCompatible();
		var requestFactory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
		requestFactory.setReadTimeout(this.config.timeout());
		RestClient.Builder builder = RestClient.builder().baseUrl(this.config.baseUrl()).requestFactory(requestFactory);
		if (this.config.apiKey() != null && !this.config.apiKey().isBlank()) {
			builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + this.config.apiKey());
		}
		this.http = builder.build();
	}

	@Override
	public Optional<String> complete(String system, String user) {
		Map<String, Object> body = Map.of("model", this.config.model(), "max_tokens", this.config.maxTokens(),
				"messages", List.of(Map.of("role", "system", "content", system), Map.of("role", "user", "content", user)));
		ChatResponse response = this.http.post()
			.uri("/chat/completions")
			.contentType(MediaType.APPLICATION_JSON)
			.body(body)
			.retrieve()
			.body(ChatResponse.class);
		if (response == null || response.choices() == null || response.choices().isEmpty()) {
			return Optional.empty();
		}
		ChatMessage message = response.choices().getFirst().message();
		String text = (message != null) ? message.content() : null;
		return (text == null || text.isBlank()) ? Optional.empty() : Optional.of(text.strip());
	}

	@Override
	public String model() {
		return this.config.model();
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ChatResponse(List<Choice> choices) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Choice(ChatMessage message) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ChatMessage(String content) {
	}

}
