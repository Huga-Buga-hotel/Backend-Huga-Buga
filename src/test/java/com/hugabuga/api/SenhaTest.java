package com.hugabuga.api;

import com.hugabuga.api.dto.AuthDtos.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

/** "Esqueci a senha": token de uso único, resposta que não revela se a conta existe. */
class SenhaTest extends IntegrationTestBase {

    @Test
    void fluxoCompletoDeRedefinicao() {
        EsqueciSenhaRes r = authService.esqueciSenha(new EsqueciSenhaReq(EMAIL_MARINA));
        assertNotNull(r.tokenDev(), "no perfil dev o token volta para dar para testar");

        authService.redefinirSenha(new RedefinirSenhaReq(r.tokenDev(), "NovaSenha!2026"));

        assertDoesNotThrow(() -> authService.login(new LoginReq(EMAIL_MARINA, "NovaSenha!2026", false)));
        assertErro(erro(() -> authService.login(new LoginReq(EMAIL_MARINA, "Uga!Buga2026", false))),
                HttpStatus.UNAUTHORIZED, "NAO_AUTENTICADO");
        // o token é de uso único
        assertErro(erro(() -> authService.redefinirSenha(new RedefinirSenhaReq(r.tokenDev(), "OutraSenha!2026"))),
                HttpStatus.BAD_REQUEST, "VALIDACAO");
    }

    @Test
    void emailInexistenteRecebeAMesmaMensagem() {
        EsqueciSenhaRes existe = authService.esqueciSenha(new EsqueciSenhaReq(EMAIL_MARINA));
        EsqueciSenhaRes naoExiste = authService.esqueciSenha(new EsqueciSenhaReq("ninguem@example.com"));
        assertEquals(existe.mensagem(), naoExiste.mensagem());
        assertNull(naoExiste.tokenDev());
    }

    @Test
    void tokenInventadoEhRecusado() {
        assertErro(erro(() -> authService.redefinirSenha(new RedefinirSenhaReq("inventado", "NovaSenha!2026"))),
                HttpStatus.BAD_REQUEST, "VALIDACAO");
    }
}
