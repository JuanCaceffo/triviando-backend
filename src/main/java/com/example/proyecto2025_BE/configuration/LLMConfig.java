package com.example.proyecto2025_BE.configuration;

import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.proyecto2025_BE.constants.LLM;
import com.example.proyecto2025_BE.service.DocumentSummarizer;
import com.example.proyecto2025_BE.service.ModelCommunication;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;
import lombok.Setter;
import java.util.Set;

@Setter
@Configuration
@ConfigurationProperties(prefix = "llm")
public class LLMConfig {
	private static final double SUMMARIZER_TEMPERATURE = 0.2;
	private static final String NO_REASONING = "none";

	private String apiKey;
	private String modelName;
	private String reasoningEffort = "medium";
	private String summarizerReasoningEffort = NO_REASONING;
	private boolean logRequests;
	private boolean logResponses;

	@Bean
	public OpenAiChatModel generationChatModel() {
		return chatModel(reasoningEffort, null, true);
	}

	@Bean
	public OpenAiChatModel summarizerChatModel() {
		return chatModel(summarizerReasoningEffort, SUMMARIZER_TEMPERATURE, false);
	}

	@Bean
	public ModelCommunication modelCommunication() {
		return AiServices.builder(ModelCommunication.class)
			.chatModel(generationChatModel())
			// Memoria fresca por invocacion: sin estado compartido entre requests ni entre usuarios
			.chatMemoryProvider(chatMemoryId -> MessageWindowChatMemory.withMaxMessages(10))
			.systemMessageProvider(chatMemoryId -> LLM.TEMPLATE_SYSTEM_PROMPT)
			.build();
	}

	@Bean
	public DocumentSummarizer documentSummarizer() {
		return AiServices.builder(DocumentSummarizer.class)
			.chatModel(summarizerChatModel())
			.build();
	}

	private OpenAiChatModel chatModel(String effort, Double temperature, boolean strictJsonSchema) {
		var parametersBuilder = OpenAiChatRequestParameters.builder()
			.reasoningEffort(effort);

		// OpenAI rechaza temperature cuando reasoning_effort es distinto de none
		if (NO_REASONING.equals(effort)) {
			parametersBuilder.temperature(temperature);
		}

		var builder = OpenAiChatModel.builder()
			.apiKey(apiKey)
			.modelName(modelName)
			.defaultRequestParameters(parametersBuilder.build())
			.logRequests(logRequests)
			.logResponses(logResponses)
			.timeout(LLM.TIMEOUT);

		if (strictJsonSchema) {
			builder.supportedCapabilities(Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA))
				.strictJsonSchema(true);
		}

		return builder.build();
	}
}
