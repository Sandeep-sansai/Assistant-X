package de.unikassel.assistant.rag;

import java.util.ArrayList;
import java.util.List;

import org.springframework.web.util.HtmlUtils;

/** Renders what the student told us about themselves as a {@code <student-context>} block for the prompt. */
public final class StudentContext {

	private StudentContext() {
	}

	/** Returns null when there is nothing to say. */
	public static String render(String departmentName, String programme, Integer semester) {
		List<String> attributes = new ArrayList<>();
		if (departmentName != null && !departmentName.isBlank()) {
			attributes.add(attribute("department", departmentName));
		}
		if (programme != null && !programme.isBlank()) {
			attributes.add(attribute("programme", programme.strip()));
		}
		if (semester != null) {
			attributes.add(attribute("semester", semester.toString()));
		}
		return attributes.isEmpty() ? null : "<student-context " + String.join(" ", attributes) + "/>";
	}

	private static String attribute(String name, String value) {
		return name + "=\"" + HtmlUtils.htmlEscape(value) + "\"";
	}

}
