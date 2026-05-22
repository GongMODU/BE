package com.gong.modu.domain.dto.anthropic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class AnthropicMessageDto {

    private String role;
    private String content;
}
