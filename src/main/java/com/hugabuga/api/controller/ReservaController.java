package com.hugabuga.api.controller;

import com.hugabuga.api.config.OpenApiConfig;
import com.hugabuga.api.config.SecurityConfig;
import com.hugabuga.api.dto.CavernaDtos.RelatoRes;
import com.hugabuga.api.dto.ReservaDtos.*;
import com.hugabuga.api.service.RelatoService;
import com.hugabuga.api.service.ReservaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Checkout e gestão da reserva. O parâmetro Solicitante (usuário logado e/ou header X-Responsavel-Email)
 * é conferido pelo service: se a reserva não for dele, 403 SEM_PERMISSAO. ADMIN pode agir em qualquer reserva.
 */
@Tag(name = "Reservas", description = "Checkout (quem vem, extras, combo, cupom), voucher e gestão da estadia")
@SecurityRequirement(name = OpenApiConfig.BEARER)
@SecurityRequirement(name = OpenApiConfig.EMAIL)
@RestController
@RequestMapping(SecurityConfig.API)
@RequiredArgsConstructor
public class ReservaController {

    private final ReservaService reservaService;
    private final RelatoService relatoService;

    // ---------- passo 1: criar e quem vem ----------

    @Operation(summary = "Cria a reserva e segura a toca por 15 min", description = """
            Livre (serve para quem não tem conta: informe 'email'). Valida o tempo de estadia (mínimo/máximo de
            noites) e a disponibilidade. 409 SEM_DISPONIBILIDADE traz datas sugeridas; 409 PRECO_ALTERADO se o
            preço mudou desde a busca.""")
    @PostMapping("/reservas")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservaRes criar(@Valid @RequestBody CriarReservaReq req, Solicitante s) {
        return reservaService.criar(req, s);
    }

    @Operation(summary = "Quem vem pra caverna?", description = "Responsável e hóspedes (passo 1). Obrigatório antes de pagar. Recalcula o total.")
    @PutMapping("/reservas/{codigo}/hospedes")
    public ReservaRes hospedes(@PathVariable String codigo, @Valid @RequestBody HospedesReq req, Solicitante s) {
        return reservaService.salvarHospedes(codigo, req, s);
    }

    @Operation(summary = "+10 minutos de reserva", description = "Uma única vez por reserva.")
    @PostMapping("/reservas/{codigo}/prorrogar")
    public ReservaRes prorrogar(@PathVariable String codigo, Solicitante s) {
        return reservaService.prorrogar(codigo, s);
    }

    // ---------- passo 2: extras e combo ----------

    @Operation(summary = "Catálogo de extras", description = "\"Turbine a estadia da tribo\".")
    @SecurityRequirements // rota livre
    @GetMapping("/extras")
    public List<ExtraRes> extras() {
        return reservaService.listarExtras();
    }

    @Operation(summary = "Salva os extras", description = "Lista COMPLETA; lista vazia = \"Pular extras\".")
    @PutMapping("/reservas/{codigo}/extras")
    public ReservaRes salvarExtras(@PathVariable String codigo, @Valid @RequestBody ExtrasReq req, Solicitante s) {
        return reservaService.salvarExtras(codigo, req, s);
    }

    @Operation(summary = "Escolhe o combo quarto + Banquete", description = """
            Jantar no Banquete todas as noites da estadia, no horário escolhido, para a tribo inteira (adulto e
            criança de 6 a 12 têm preços próprios; até 5 anos não paga). Exige o passo 1 feito. As mesas são
            reservadas na hora (seguram lugar no salão de 50 pessoas) e confirmadas no pagamento.
            409 VAGAS_ESGOTADAS se alguma noite estiver lotada nesse horário.""")
    @PutMapping("/reservas/{codigo}/combo")
    public ReservaRes definirCombo(@PathVariable String codigo, @Valid @RequestBody ComboReq req, Solicitante s) {
        return reservaService.definirCombo(codigo, req, s);
    }

    @Operation(summary = "Tira o combo", description = "Libera as mesas e recalcula o total.")
    @DeleteMapping("/reservas/{codigo}/combo")
    public ReservaRes removerCombo(@PathVariable String codigo, Solicitante s) {
        return reservaService.removerCombo(codigo, s);
    }

    // ---------- passo 3: cupom ----------

    @Operation(summary = "Aplica cupom", description = "422 CUPOM_INVALIDO se não existe, venceu ou esgotou.")
    @PostMapping("/reservas/{codigo}/cupom")
    public ReservaRes aplicarCupom(@PathVariable String codigo, @Valid @RequestBody CupomReq req, Solicitante s) {
        return reservaService.aplicarCupom(codigo, req, s);
    }

    @Operation(summary = "Remove o cupom")
    @DeleteMapping("/reservas/{codigo}/cupom")
    public ReservaRes removerCupom(@PathVariable String codigo, Solicitante s) {
        return reservaService.removerCupom(codigo, s);
    }

    // ---------- consulta ----------

    @Operation(summary = "\"Reservou sem conta?\"", description = "Exige código E e-mail. Qualquer divergência é 404.")
    @SecurityRequirements // rota livre
    @GetMapping("/reservas/buscar")
    public ReservaRes buscar(@RequestParam String codigo, @RequestParam String email) {
        return reservaService.buscarSemConta(codigo, email);
    }

    @Operation(summary = "Voucher (resumo completo da reserva)")
    @GetMapping("/reservas/{codigo}")
    public ReservaRes voucher(@PathVariable String codigo, Solicitante s) {
        return reservaService.voucher(codigo, s);
    }

    @Operation(summary = "Baixar tábua (PDF)")
    @GetMapping(value = "/reservas/{codigo}/voucher.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> voucherPdf(@PathVariable String codigo, Solicitante s) {
        byte[] pdf = reservaService.voucherPdf(codigo, s); // só chega abaixo se a reserva existe e é do solicitante
        String nome = "tabua-" + codigo.replaceAll("[^A-Za-z0-9-]", "") + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nome).build().toString())
                .body(pdf);
    }

    @Operation(summary = "Adicionar à agenda (.ics)", description = "Evento com entrada às 14h e saída às 12h (15h com late check-out).")
    @GetMapping(value = "/reservas/{codigo}/agenda.ics", produces = "text/calendar")
    public ResponseEntity<byte[]> agendaIcs(@PathVariable String codigo, Solicitante s) {
        byte[] ics = reservaService.agendaIcs(codigo, s).getBytes(StandardCharsets.UTF_8);
        String nome = "huga-buga-" + codigo.replaceAll("[^A-Za-z0-9-]", "") + ".ics";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "calendar", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nome).build().toString())
                .body(ics);
    }

    // ---------- gestão ----------

    @Operation(summary = "Altera as datas", description = """
            Até 2 dias antes do check-in, na mesma toca. A diferença positiva vira um novo pagamento; o combo
            acompanha as novas noites.""")
    @PatchMapping("/reservas/{codigo}/datas")
    public AlterarDatasRes alterarDatas(@PathVariable String codigo, @Valid @RequestBody AlterarDatasReq req, Solicitante s) {
        return reservaService.alterarDatas(codigo, req, s);
    }

    @Operation(summary = "Cancela a reserva", description = """
            Com 2 dias ou mais de antecedência: reembolso integral. Fora do prazo: sem reembolso; se o pagamento
            seria na chegada, multa de 1 diária. Atividades e mesas (inclusive do combo) são canceladas junto.""")
    @PostMapping("/reservas/{codigo}/cancelar")
    public CancelarRes cancelar(@PathVariable String codigo, Solicitante s) {
        return reservaService.cancelar(codigo, s);
    }

    @Operation(summary = "Check-in online", description = "Abre 3 dias antes da chegada.")
    @PostMapping("/reservas/{codigo}/checkin")
    public ReservaRes checkin(@PathVariable String codigo, Solicitante s) {
        return reservaService.checkinOnline(codigo, s);
    }

    @Operation(tags = "Avaliações", summary = "Avalia a estadia (nota, comentário e fotos)", description = """
            multipart/form-data. Só a conta que reservou, depois da estadia (reserva CONCLUIDA), uma vez.
            Até 5 fotos JPG, PNG ou WEBP de até 25 MB cada (413 ARQUIVO_GRANDE acima disso).""",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER))
    @PostMapping(value = "/reservas/{codigo}/relato", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public RelatoRes relato(@PathVariable String codigo,
                            @Parameter(description = "1 a 5") @RequestParam int nota,
                            @Parameter(description = "Comentário (até 2000 caracteres)") @RequestParam(required = false) String texto,
                            @RequestPart(name = "fotos", required = false) List<MultipartFile> fotos,
                            Solicitante s) {
        return relatoService.registrar(codigo, nota, texto, fotos, s);
    }
}
