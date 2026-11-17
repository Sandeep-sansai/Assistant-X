package de.unikassel.assistant.agent;

import de.unikassel.assistant.rag.AnswerService;
import de.unikassel.assistant.rag.AnswerService.Answer;
import de.unikassel.assistant.rag.StudentContext;

import org.springframework.stereotype.Component;

/** Answers questions from the crawled university pages, with sources. Also the fallback for anything else. */
@Component
class RegulationsAgent implements Agent {

	static final String ID = "regulations";

	private final AnswerService answers;

	RegulationsAgent(AnswerService answers) {
		this.answers = answers;
	}

	@Override
	public String id() {
		return ID;
	}

	/** Never chosen on its own merits; the concierge falls back to it. */
	@Override
	public double relevance(AgentRequest request) {
		return 0;
	}

	@Override
	public AgentReply handle(AgentRequest r) {
		String context = StudentContext.render(r.departmentName(), r.programme(), r.semester());
		java.util.List<String[]> history = r.history().stream().map((t) -> new String[] { t.question(), t.answer() }).toList();
		Answer answer = this.answers.answer(r.message(), context, r.language(), history);
		return new AgentReply(ID, answer.answer(), java.util.List.of(), java.util.List.of(), answer.sources(),
				answer.questionId());
	}

}
