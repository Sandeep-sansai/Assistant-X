package de.unikassel.assistant.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTests {

	@Test
	void allowsAFewQuestionsAMinutePerVisitor() {
		RateLimitFilter filter = new RateLimitFilter(new MutableClock());

		for (int i = 0; i < RateLimitFilter.PER_MINUTE; i++) {
			assertThat(filter.allow("a")).isTrue();
		}
		assertThat(filter.allow("a")).isFalse();
		assertThat(filter.allow("b")).isTrue();
	}

	@Test
	void letsTheVisitorAskAgainAfterAMinute() {
		MutableClock clock = new MutableClock();
		RateLimitFilter filter = new RateLimitFilter(clock);
		for (int i = 0; i < RateLimitFilter.PER_MINUTE; i++) {
			filter.allow("a");
		}

		clock.advance(Duration.ofSeconds(61));

		assertThat(filter.allow("a")).isTrue();
	}

	@Test
	void trustsTheTunnelHeaderOnlyFromThisMachine() {
		MockHttpServletRequest viaTunnel = new MockHttpServletRequest();
		viaTunnel.setRemoteAddr("127.0.0.1");
		viaTunnel.addHeader("CF-Connecting-IP", "203.0.113.7");
		MockHttpServletRequest direct = new MockHttpServletRequest();
		direct.setRemoteAddr("198.51.100.2");
		direct.addHeader("CF-Connecting-IP", "203.0.113.7");

		assertThat(RateLimitFilter.client(viaTunnel)).isEqualTo("203.0.113.7");
		assertThat(RateLimitFilter.client(direct)).isEqualTo("198.51.100.2");
	}

	static final class MutableClock extends Clock {

		private Instant now = Instant.parse("2026-10-17T10:00:00Z");

		void advance(Duration d) {
			this.now = this.now.plus(d);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return this.now;
		}

	}

}
