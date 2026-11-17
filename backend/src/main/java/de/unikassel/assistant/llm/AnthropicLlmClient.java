package de.unikassel.assistant.llm;

import java.util.Optional;
import java.util.stream.Collectors;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import de.unikassel.assistant.config.AssistantProperties;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Claude through the official Anthropic Java SDK. Reads ANTHROPIC_API_KEY from the environment. */
@Component
@ConditionalOnProperty(name = "assistant.llm.provider", havingValue = "anthropic")
class AnthropicLlmClient implements LlmClient {

	private final AnthropicClient claude = AnthropicOkHttpClient.fromEnv();

	private final AssistantProperties.Claude config;

	AnthropicLlmClient(AssistantProperties props) {
		this.config = props.claude();
	}

	@Override
	public Optional<String> complete(String system, String user) {
		MessageCreateParams.Builder params = MessageCreateParams.builder()
			.model(this.config.model())
			.maxTokens(this.config.maxTokens())
			.system(system)
			.outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.of(this.config.effort())).build())
			.addUserMessage(user);
		if (this.config.fallbacks()) {
			// If the model declines, the API retries on a fallback model it picks by refusal category.
			params.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
				.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
		}
		Message response = this.claude.messages().create(params.build());
		if (response.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
			return Optional.empty();
		}
		String text = response.content()
			.stream()
			.flatMap((block) -> block.text().stream())
			.map(TextBlock::text)
			.collect(Collectors.joining());
		return text.isBlank() ? Optional.empty() : Optional.of(text);
	}

	@Override
	public String model() {
		return this.config.model();
	}

}
