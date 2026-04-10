package interview.guide.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文本生成供应商配置。
 */
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiTextConfigProperties {

    private String textProvider = "spring-ai";
    private String responsesBaseUrl;
    private String responsesPath = "/v1/responses";
    private String responsesApiKey;
    private String responsesModel;
    private Double responsesTemperature = 0.2;

}
