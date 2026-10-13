package com.example.proyecto2025_BE.configuration;

import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.proyecto2025_BE.constants.LLM;
import com.example.proyecto2025_BE.service.ModelCommunication;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;
import lombok.Setter;
import java.util.Set;

@Setter
@Configuration
@ConfigurationProperties(prefix = "llm")
public class LLMConfig {
	private String apiKey;
	private String modelName;
	private boolean logRequests;
	private boolean logResponses;
	private double temperature;
	
	@Bean
	public ModelCommunication modelCommunication() {
		return AiServices.builder(ModelCommunication.class)
			.chatLanguageModel(chatLanguageModel())
			// Memoria fresca por invocacion: sin estado compartido entre requests ni entre usuarios
			.chatMemoryProvider(chatMemoryId -> MessageWindowChatMemory.withMaxMessages(10))
			.systemMessageProvider(chatMemoryId -> LLM.TEMPLATE_SYSTEM_PROMPT)
			.build();
	}
	
	private OpenAiChatModel chatLanguageModel() {
		return OpenAiChatModel.builder()
			.apiKey(apiKey)
			.modelName(modelName)
			.logRequests(logRequests)
			.logResponses(logResponses)
			.timeout(LLM.TIMEOUT)
			.temperature(temperature)
			.supportedCapabilities(Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA))
			.strictJsonSchema(true)
			.build();
	}
}
