package interview.guide.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 应用配置属性
 */
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "app.resume")
public class AppConfigProperties {
    
    private String uploadDir;
    private List<String> allowedTypes;

}
