package com.ifi.aws.shortener;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ifi.aws.shortener.config.ProductionSafetyValidator;
import com.ifi.aws.shortener.config.ShortLinkProperties;

class ProductionSafetyValidatorTest {

	private static final String KEY = "k".repeat(32);

	private ShortLinkProperties props(String base, List<String> hosts, String key) {
		return new ShortLinkProperties(base, 8, hosts, key, java.util.Map.of(), false, 30, 30, 0, 20, false);
	}

	@Test
	void acceptsASafeConfiguration() {
		assertThatCode(() -> ProductionSafetyValidator.validateForProduction(
				props("https://go.workroo.click", List.of("workroo.click"), KEY))).doesNotThrowAnyException();
	}

	@Test
	void refusesAnEmptyAllowList() {
		assertThatThrownBy(() -> ProductionSafetyValidator.validateForProduction(
				props("https://go.workroo.click", List.of(), KEY)))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("allowed-hosts");
	}

	@Test
	void refusesAMissingOrShortApiKey() {
		assertThatThrownBy(() -> ProductionSafetyValidator.validateForProduction(
				props("https://go.workroo.click", List.of("workroo.click"), "")))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("api-key");
		assertThatThrownBy(() -> ProductionSafetyValidator.validateForProduction(
				props("https://go.workroo.click", List.of("workroo.click"), "short")))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void refusesShortCodes() {
		assertThatThrownBy(() -> ProductionSafetyValidator.validateForProduction(
				new ShortLinkProperties("https://go.workroo.click", 7, List.of("workroo.click"), KEY, java.util.Map.of(), false, 30, 30, 0, 20, false)))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("code-length");
	}


	@Test
	void detectsPublicDomainsAccurately() {
		org.assertj.core.api.Assertions.assertThat(ProductionSafetyValidator.isPublicDomain("http://localhost:8090")).isFalse();
		org.assertj.core.api.Assertions.assertThat(ProductionSafetyValidator.isPublicDomain("http://127.0.0.1:8090")).isFalse();
		org.assertj.core.api.Assertions.assertThat(ProductionSafetyValidator.isPublicDomain("http://myservice.local:8090")).isFalse();
		org.assertj.core.api.Assertions.assertThat(ProductionSafetyValidator.isPublicDomain("https://go.anuradha.indevs.in")).isTrue();
		org.assertj.core.api.Assertions.assertThat(ProductionSafetyValidator.isPublicDomain("https://go.workroo.click")).isTrue();
	}

	@Test
	void refusesPlainHttpBaseUrl() {
		assertThatThrownBy(() -> ProductionSafetyValidator.validateForProduction(
				props("http://go.workroo.click", List.of("workroo.click"), KEY)))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("https");
	}
}
