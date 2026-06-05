package com.gong.modu.domain.dto.user;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class WithdrawRequest {

    // LOCAL 유저만 필수, 소셜 유저는 null
    private String password;
}
