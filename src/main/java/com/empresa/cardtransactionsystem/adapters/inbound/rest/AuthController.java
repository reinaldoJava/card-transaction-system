package com.empresa.cardtransactionsystem.adapters.inbound.rest;

import com.empresa.cardtransactionsystem.adapters.inbound.rest.dto.JwtResponse;
import com.empresa.cardtransactionsystem.adapters.inbound.rest.dto.LoginRequest;
import com.empresa.cardtransactionsystem.domain.ports.input.LoginUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final LoginUseCase loginUseCase;

    public AuthController(LoginUseCase loginUseCase) {
        this.loginUseCase = loginUseCase;
    }

    @PostMapping("/login")
    public JwtResponse login(@RequestBody @Valid LoginRequest request) {
        var jwt = loginUseCase.login(request.username(), request.password());
        return new JwtResponse(jwt.value());
    }
}
