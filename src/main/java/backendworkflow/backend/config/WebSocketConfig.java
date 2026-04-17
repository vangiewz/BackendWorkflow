package backendworkflow.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-workflow")
                .setAllowedOriginPatterns("*")
                .withSockJS(); // Fallback opcional, Angular RxStomp puede usar raw websocket también sin SockJS si no se incluye frontend adapter, pero withSockJS() no molesta si se añade config en cors.
        
        // También creamos el endpoint puro por si se conecta con native websocket
        registry.addEndpoint("/ws-workflow")
                .setAllowedOriginPatterns("*");
    }
}
