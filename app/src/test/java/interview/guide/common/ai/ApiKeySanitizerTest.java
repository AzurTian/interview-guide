package interview.guide.common.ai;

import interview.guide.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("API Key sanitizer")
class ApiKeySanitizerTest {

  @Test
  @DisplayName("normalize removes accidental Bearer prefix")
  void normalizeRemovesBearerPrefix() {
    assertThat(ApiKeySanitizer.normalize("Bearer sk-test")).isEqualTo("sk-test");
    assertThat(ApiKeySanitizer.normalize("bearer   sk-test")).isEqualTo("sk-test");
    assertThat(ApiKeySanitizer.normalize(" sk-test ")).isEqualTo("sk-test");
  }

  @Test
  @DisplayName("requirePresent rejects blank keys")
  void requirePresentRejectsBlankKeys() {
    assertThatThrownBy(() -> ApiKeySanitizer.requirePresent(" ", "Provider"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("API Key 为空");
  }
}
