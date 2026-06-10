package backendworkflow.backend.workflow.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import org.springframework.beans.factory.annotation.Value;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${app.cors.allowed-origins:http://localhost:4200}")
    private String[] allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-workflow")
                .setAllowedOrigins("http://localhost:4200", "http://localhost:8080", "https://frontend-workflow-kappa.vercel.app")
                .withSockJS(); 
        
        registry.addEndpoint("/ws-workflow")
                .setAllowedOrigins("http://localhost:4200", "http://localhost:8080", "https://frontend-workflow-kappa.vercel.app");

        // FASE 4: Endpoint para colaboración en tiempo real
        registry.addEndpoint("/ws-colaboracion")
                .setAllowedOrigins("http://localhost:4200", "http://localhost:8080", "https://frontend-workflow-kappa.vercel.app")
                .withSockJS();
    }

    @Override
    public void configureWebSocketTransport(org.springframework.web.socket.config.annotation.WebSocketTransportRegistration registry) {
        // Aumentar el límite de tamaño de los mensajes de WebSocket a 10 MB para soportar payloads grandes de Luckysheet
        registry.setMessageSizeLimit(10 * 1024 * 1024);
        registry.setSendBufferSizeLimit(10 * 1024 * 1024);
        registry.setSendTimeLimit(20000);
    }

    @org.springframework.context.annotation.Bean
    public org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean createWebSocketContainer() {
        org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean container = new org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(10 * 1024 * 1024);
        container.setMaxBinaryMessageBufferSize(10 * 1024 * 1024);
        return container;
    }
}
