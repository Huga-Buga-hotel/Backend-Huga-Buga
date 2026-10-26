package com.hugabuga.api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Dois jobs: expiração (a cada 1 min) e conclusão (diária, 03:00). Exige @EnableScheduling na aplicação. */
@Slf4j
@Service
@RequiredArgsConstructor
public class TarefasAgendadas {

    private final ReservaService reservaService;
    private final PagamentoService pagamentoService;

    /** PENDENTE com expiraEm vencido e Pix vencido viram EXPIRADA (a unidade volta a ficar livre). */
    @Scheduled(fixedRate = 60_000)
    public void expirar() {
        try {
            int reservas = reservaService.expirarVencidas();
            int pix = pagamentoService.expirarPixVencidos();
            if (reservas + pix > 0) log.info("Expiração: {} reserva(s) pendente(s), {} Pix vencido(s)", reservas, pix);
        } catch (Exception e) { // um erro não pode derrubar o agendador
            log.error("Falha no job de expiração", e);
        }
    }

    /** CONFIRMADA com check-out passado vira CONCLUIDA e credita os ossos. */
    @Scheduled(cron = "0 0 3 * * *", zone = "America/Sao_Paulo")
    public void concluir() {
        try {
            int n = reservaService.concluirEstadias();
            log.info("Conclusão: {} estadia(s) concluída(s)", n);
        } catch (Exception e) {
            log.error("Falha no job de conclusão", e);
        }
    }
}
