package com.hugabuga.api.service;

import com.hugabuga.api.dto.PagamentoDtos.*;
import com.hugabuga.api.dto.ReservaDtos.Solicitante;
import com.hugabuga.api.erro.ApiException;
import com.hugabuga.api.model.*;
import com.hugabuga.api.repository.PagamentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Pagamento SIMULADO. Número completo e CVV existem só na memória desta requisição:
 * nunca são gravados nem escritos em log (não registre o corpo desta requisição!).
 */
@Service
@RequiredArgsConstructor
public class PagamentoService {

    private static final int PIX_AUTO_SEGUNDOS_DEV = 20;

    private final PagamentoRepository pagamentos;
    private final ReservaService reservaService;
    private final Environment env;

    @Value("${hugabuga.regras.pix-minutos:10}")
    private int pixMinutos;

    /**
     * noRollbackFor: o cartão recusado precisa GRAVAR a tentativa (RECUSADO) mesmo lançando o 422.
     * Antes de lançar, nada mais é alterado: recusa não mexe na reserva, no cupom nem nos ossos.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public PagamentoRes pagar(String codigo, PagamentoReq req, Solicitante s) {
        Reserva r = reservaService.carregarDonoParaEscrita(codigo, s); // trava a linha: clique duplo é serializado
        reservaService.exigirStatus(r, StatusReserva.PENDENTE, StatusReserva.AGUARDANDO_PAGAMENTO);

        // Idempotência: nunca duas cobranças aprovadas / dois Pix ao mesmo tempo
        List<Pagamento> existentes = pagamentos.findByReservaId(r.getId());
        if (existentes.stream().anyMatch(p -> p.getStatus() == StatusPagamento.APROVADO)) {
            throw Util.estadoInvalido("Esta reserva já foi paga.");
        }
        Optional<Pagamento> pixAberto = existentes.stream()
                .filter(p -> p.getMetodo() == MetodoPagamento.PIX && p.getStatus() == StatusPagamento.PENDENTE
                        && p.getPixExpiraEm().isAfter(Util.agora())).findFirst();
        if (pixAberto.isPresent()) {
            if (req.metodo() == MetodoPagamento.PIX) return toRes(pixAberto.get(), r.getCodigo()); // devolve o mesmo Pix
            throw Util.estadoInvalido("Já existe um Pix aguardando pagamento. Pague-o ou espere ele expirar.");
        }
        reservaService.exigirStatus(r, StatusReserva.PENDENTE);

        return switch (req.metodo()) {
            case CARTAO -> pagarComCartao(r, req);
            case PIX -> gerarPix(r);
            case NA_CHEGADA -> pagarNaChegada(r);
        };
    }

    @Transactional
    public PagamentoRes consultar(UUID pagamentoId, Solicitante s) {
        Pagamento p = pagamentos.findById(pagamentoId).orElseThrow(() -> Util.naoEncontrado("Pagamento"));
        Reserva r = reservaService.carregarPorId(p.getReservaId());
        reservaService.verificarDono(r, s);
        // Perfil dev: o Pix simulado aprova sozinho após 20 s (o front consulta a cada 3 s)
        if (dev() && p.getMetodo() == MetodoPagamento.PIX && p.getStatus() == StatusPagamento.PENDENTE
                && p.getCriadoEm().plusSeconds(PIX_AUTO_SEGUNDOS_DEV).isBefore(Util.agora())
                && p.getPixExpiraEm().isAfter(Util.agora())) {
            aprovarPix(p, r);
        }
        return toRes(p, r.getCodigo());
    }

    /** Só no perfil dev. Fora dele a rota "não existe". */
    @Transactional
    public PagamentoRes simularConfirmacao(UUID pagamentoId, Solicitante s) {
        if (!dev()) throw Util.naoEncontrado("Rota");
        Pagamento p = pagamentos.findById(pagamentoId).orElseThrow(() -> Util.naoEncontrado("Pagamento"));
        Reserva r = reservaService.carregarPorId(p.getReservaId());
        reservaService.verificarDono(r, s);
        if (p.getMetodo() != MetodoPagamento.PIX) throw Util.estadoInvalido("Só pagamentos Pix podem ser confirmados por simulação.");
        aprovarPix(p, r);
        return toRes(p, r.getCodigo());
    }

    /** Job: Pix vencido vira EXPIRADO e a reserva em AGUARDANDO_PAGAMENTO vira EXPIRADA. */
    @Transactional
    public int expirarPixVencidos() {
        List<Pagamento> vencidos = pagamentos.findByMetodoAndStatusAndPixExpiraEmBefore(
                MetodoPagamento.PIX, StatusPagamento.PENDENTE, Util.agora());
        for (Pagamento p : vencidos) {
            p.setStatus(StatusPagamento.EXPIRADO);
            pagamentos.save(p);
            Reserva r = reservaService.carregarPorId(p.getReservaId());
            if (r.getStatus() == StatusReserva.AGUARDANDO_PAGAMENTO) reservaService.mudarStatus(r, StatusReserva.EXPIRADA);
        }
        return vencidos.size();
    }

    // ---------- métodos ----------

    private PagamentoRes pagarComCartao(Reserva r, PagamentoReq req) {
        if (!req.aceitoRegras()) throw Util.validacao("É preciso aceitar as regras da estadia para pagar com cartão.");
        int parcelas = req.parcelas() == null ? 1 : req.parcelas();
        if (parcelas < 1 || parcelas > 6) throw Util.validacao("O parcelamento vai de 1 a 6 vezes.");
        String numero = req.numeroCartao() == null ? "" : req.numeroCartao().replaceAll("\\D", "");
        if (numero.length() < 13 || numero.length() > 19) throw Util.validacao("O número do cartão é inválido.");
        if (req.cvv() == null || !req.cvv().matches("\\d{3,4}")) throw Util.validacao("O CVV é inválido.");

        String fin = numero.substring(numero.length() - 4);
        // Simulador: finais 0002 e 0069 recusam; os demais precisam passar no Luhn.
        String motivo = switch (fin) {
            case "0002" -> "SALDO_INSUFICIENTE";
            case "0069" -> "CARTAO_EXPIRADO";
            default -> null;
        };
        if (motivo == null && !luhn(numero)) throw Util.validacao("O número do cartão é inválido.");

        Pagamento p = novo(r, MetodoPagamento.CARTAO, r.getTotal());
        p.setParcelas(parcelas);
        p.setCartaoFinal(fin);            // só os 4 últimos dígitos
        p.setCartaoBandeira(bandeira(numero));

        if (motivo != null) {
            p.setStatus(StatusPagamento.RECUSADO);
            p.setMotivoRecusa(motivo);
            pagamentos.save(p);
            String porque = motivo.equals("SALDO_INSUFICIENTE") ? "saldo insuficiente" : "cartão vencido";
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PAGAMENTO_RECUSADO", "Ugh! O pagamento não passou.",
                    "O banco recusou o cartão final " + fin + " (" + porque + "). Nada foi cobrado e a sua toca continua segurada. Tente outro cartão ou escolha Pix.",
                    Map.of("motivo", motivo, "cartaoFinal", fin));
        }
        reservaService.confirmar(r); // valida cupom/ossos antes de gravar qualquer coisa
        p.setStatus(StatusPagamento.APROVADO);
        p.setAprovadoEm(Util.agora());
        return toRes(pagamentos.save(p), r.getCodigo());
    }

    private PagamentoRes gerarPix(Reserva r) {
        Pagamento p = novo(r, MetodoPagamento.PIX, reservaService.valorComPix(r.getTotal())); // −5% só aqui, por último
        p.setPixCopiaCola("00020126HUGABUGA" + r.getCodigo().replace("-", "") + UUID.randomUUID().toString().replace("-", ""));
        p.setPixExpiraEm(Util.agora().plusMinutes(pixMinutos));
        pagamentos.save(p);
        reservaService.mudarStatus(r, StatusReserva.AGUARDANDO_PAGAMENTO);
        return toRes(p, r.getCodigo());
    }

    /** Não cobra nada: registra a intenção e confirma a reserva com pago = false. */
    private PagamentoRes pagarNaChegada(Reserva r) {
        Pagamento p = novo(r, MetodoPagamento.NA_CHEGADA, r.getTotal());
        reservaService.confirmar(r);
        return toRes(pagamentos.save(p), r.getCodigo());
    }

    private void aprovarPix(Pagamento p, Reserva r) {
        if (p.getStatus() != StatusPagamento.PENDENTE) throw Util.estadoInvalido("Este Pix não está mais aguardando pagamento.");
        if (p.getPixExpiraEm().isBefore(Util.agora())) throw Util.estadoInvalido("Este Pix já expirou.");
        reservaService.confirmar(r); // AGUARDANDO_PAGAMENTO -> CONFIRMADA
        p.setStatus(StatusPagamento.APROVADO);
        p.setAprovadoEm(Util.agora());
        pagamentos.save(p);
    }

    // ---------- apoio ----------

    private Pagamento novo(Reserva r, MetodoPagamento metodo, int valor) {
        Pagamento p = new Pagamento();
        p.setReservaId(r.getId());
        p.setMetodo(metodo);
        p.setStatus(StatusPagamento.PENDENTE);
        p.setValorCentavos(valor);
        p.setCriadoEm(Util.agora());
        return p;
    }

    private PagamentoRes toRes(Pagamento p, String codigoReserva) {
        Integer parcela = (p.getMetodo() == MetodoPagamento.CARTAO && p.getParcelas() != null)
                ? reservaService.valorParcela(p.getValorCentavos(), p.getParcelas()) : null;
        return new PagamentoRes(p.getId(), codigoReserva, p.getMetodo(), p.getStatus(), p.getValorCentavos(),
                p.getParcelas(), parcela, p.getCartaoFinal(), p.getCartaoBandeira(), p.getPixCopiaCola(),
                p.getPixExpiraEm(), p.getMotivoRecusa());
    }

    private boolean dev() { return env.acceptsProfiles(Profiles.of("dev")); }

    private static boolean luhn(String n) {
        int soma = 0;
        boolean dobrar = false;
        for (int i = n.length() - 1; i >= 0; i--) {
            int d = n.charAt(i) - '0';
            if (dobrar) { d *= 2; if (d > 9) d -= 9; }
            soma += d;
            dobrar = !dobrar;
        }
        return soma % 10 == 0;
    }

    private static String bandeira(String n) {
        if (n.startsWith("4")) return "VISA";
        if (n.startsWith("34") || n.startsWith("37")) return "AMEX";
        if (n.startsWith("5") || n.startsWith("2")) return "MASTERCARD";
        return "OUTRA";
    }
}
