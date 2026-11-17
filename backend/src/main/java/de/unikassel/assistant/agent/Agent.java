package de.unikassel.assistant.agent;

/**
 * A specialist behind the one assistant students talk to. The {@link Concierge} asks every agent
 * how relevant a message is and hands it to the best match (docs/decisions.md #17).
 */
public interface Agent {

	/** Stable id, shown to the student as the agent's name. */
	String id();

	/** 0 when the message is not for this agent, up to 1 when it clearly is. */
	double relevance(AgentRequest request);

	AgentReply handle(AgentRequest request);

}
