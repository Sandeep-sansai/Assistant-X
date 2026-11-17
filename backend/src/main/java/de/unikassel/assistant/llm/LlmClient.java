package de.unikassel.assistant.llm;

import java.util.Optional;

/**
 * A chat model that answers one system + user prompt. Which implementation is active is chosen
 * with {@code assistant.llm.provider} (see docs/decisions.md #11).
 */
public interface LlmClient {

	/** Returns the model's reply, or empty if the model declined or returned no text. */
	Optional<String> complete(String system, String user);

	/** The model name, recorded in the question log. */
	String model();

}
