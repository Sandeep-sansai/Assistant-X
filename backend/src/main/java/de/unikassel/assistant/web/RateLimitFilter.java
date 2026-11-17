package de.unikassel.assistant.web;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limits how often one visitor can ask the model or send feedback, so a shared link can't be used
 * to keep the (local, slow) model busy for everyone. Counts per client address in memory.
 */
@Component
class RateLimitFilter extends OncePerRequestFilter {

	static final int PER_MINUTE = 12;

	static final int PER_DAY = 200;

	private static final Set<String> LIMITED = Set.of("/api/ask", "/api/assistant", "/api/site-feedback");

	private static final long MINUTE = 60_000;

	private static final long DAY = 24 * 60 * MINUTE;

	private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

	private final Clock clock;

	RateLimitFilter() {
		this(Clock.systemUTC());
	}

	RateLimitFilter(Clock clock) {
		this.clock = clock;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !"POST".equals(request.getMethod()) || !LIMITED.contains(request.getRequestURI());
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (!allow(client(request))) {
			response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
			response.setHeader("Retry-After", "60");
			response.setContentType("application/json");
			response.getWriter().write("{\"error\":\"too many requests\"}");
			return;
		}
		chain.doFilter(request, response);
	}

	/** Records a hit and says whether it is within the limits. */
	boolean allow(String client) {
		long now = this.clock.millis();
		Deque<Long> times = this.hits.computeIfAbsent(client, (k) -> new ArrayDeque<>());
		synchronized (times) {
			while (!times.isEmpty() && times.peekFirst() < now - DAY) {
				times.pollFirst();
			}
			long lastMinute = times.stream().filter((t) -> t >= now - MINUTE).count();
			if (lastMinute >= PER_MINUTE || times.size() >= PER_DAY) {
				return false;
			}
			times.addLast(now);
		}
		if (this.hits.size() > 10_000) {
			this.hits.entrySet().removeIf((e) -> e.getValue().isEmpty() || e.getValue().peekLast() < now - DAY);
		}
		return true;
	}

	/**
	 * The visitor's address. Behind a tunnel every request comes from this machine, so the
	 * tunnel's header names the real client; it is only trusted for requests from localhost.
	 */
	static String client(HttpServletRequest request) {
		String remote = request.getRemoteAddr();
		boolean local = "127.0.0.1".equals(remote) || "0:0:0:0:0:0:0:1".equals(remote) || "::1".equals(remote);
		if (local) {
			String cf = request.getHeader("CF-Connecting-IP");
			if (cf != null && !cf.isBlank()) {
				return cf.strip();
			}
		}
		return remote;
	}

}
