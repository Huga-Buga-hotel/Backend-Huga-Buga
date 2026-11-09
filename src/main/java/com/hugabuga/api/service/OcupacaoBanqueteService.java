package com.hugabuga.api.service;

import com.hugabuga.api.dto.RestauranteDtos.HorarioRes;
import com.hugabuga.api.erro.ApiException;
import com.hugabuga.api.model.HorarioMesa;
import com.hugabuga.api.model.ReservaMesa;
import com.hugabuga.api.repository.HorarioMesaRepository;
import com.hugabuga.api.repository.ReservaMesaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Lotação do salão do Banquete (50 lugares). Uma mesa ocupa o salão por {@code duracao-mesa-minutos} a partir
 * do horário escolhido, então uma mesa das 19:00 ainda conta às 19:30 e às 20:00. Para uma nova mesa caber,
 * o salão precisa ter lugar em TODOS os horários de início dentro da janela dela (é só neles que a ocupação muda).
 *
 * Usado pela reserva avulsa (RestauranteService) e pelo combo quarto + Banquete (ReservaService).
 */
@Service
@RequiredArgsConstructor
public class OcupacaoBanqueteService {

    private final HorarioMesaRepository horarios;
    private final ReservaMesaRepository mesas;

    @Value("${hugabuga.restaurante.capacidade-pessoas:50}") private int capacidade;
    @Value("${hugabuga.restaurante.duracao-mesa-minutos:90}") private int duracaoMinutos;

    public int capacidade() { return capacidade; }

    /** Cada horário de início, LIVRE ou LOTADO para o número de pessoas pedido. */
    @Transactional(readOnly = true)
    public List<HorarioRes> horarios(LocalDate data, int pessoas) {
        List<LocalTime> inicios = inicios(horarios.findAllByOrderByHorario());
        List<ReservaMesa> doDia = mesas.ocupandoNoDia(data);
        return inicios.stream().map(h -> {
            int livres = lugaresLivres(h, doDia, inicios);
            return new HorarioRes(h, livres >= pessoas ? "LIVRE" : "LOTADO", livres);
        }).toList();
    }

    /** Pessoas no salão em cada horário de início (painel do admin). */
    @Transactional(readOnly = true)
    public Map<LocalTime, Integer> ocupacaoPorHorario(LocalDate data) {
        List<ReservaMesa> doDia = mesas.ocupandoNoDia(data);
        return inicios(horarios.findAllByOrderByHorario()).stream()
                .collect(Collectors.toMap(h -> h, h -> ocupacaoEm(h, doDia), (a, b) -> a, java.util.TreeMap::new));
    }

    /**
     * Trava o salão (todos os horários), confere se o horário existe e se cabem as pessoas. Precisa rodar dentro
     * da transação de quem vai gravar a mesa, senão a trava não serve para nada.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void garantirLugares(LocalDate data, LocalTime horario, int pessoas) {
        List<LocalTime> inicios = inicios(horarios.travarTodos());
        if (!inicios.contains(horario)) {
            throw Util.validacao("O Banquete recebe reservas nestes horários: " + inicios + ".");
        }
        int livres = lugaresLivres(horario, mesas.ocupandoNoDia(data), inicios);
        if (pessoas > livres) {
            throw new ApiException(HttpStatus.CONFLICT, "VAGAS_ESGOTADAS", "Ugh! A caverna do Banquete lotou.",
                    "Em " + data + " às " + horario + " há " + livres + " lugar(es) livre(s) e você pediu " + pessoas
                            + ". Nenhuma mesa foi reservada. Escolha outro horário.",
                    Map.of("data", data.toString(), "horario", horario.toString(), "vagasRestantes", livres));
        }
    }

    // ---------- internos ----------

    private int lugaresLivres(LocalTime horario, List<ReservaMesa> doDia, List<LocalTime> inicios) {
        int pior = inicios.stream().filter(x -> ocupa(horario, x))
                .mapToInt(x -> ocupacaoEm(x, doDia)).max().orElse(0);
        return Math.max(0, capacidade - pior);
    }

    private int ocupacaoEm(LocalTime instante, List<ReservaMesa> doDia) {
        return doDia.stream().filter(m -> ocupa(m.getHorario(), instante)).mapToInt(ReservaMesa::getPessoas).sum();
    }

    /** Uma mesa que começa em 'inicio' ainda está no salão em 'instante'? */
    private boolean ocupa(LocalTime inicio, LocalTime instante) {
        long minutos = Duration.between(inicio, instante).toMinutes();
        return minutos >= 0 && minutos < duracaoMinutos;
    }

    private static List<LocalTime> inicios(List<HorarioMesa> hs) {
        return hs.stream().map(HorarioMesa::getHorario).sorted().toList();
    }
}
