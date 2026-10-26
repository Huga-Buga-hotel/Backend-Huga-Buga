package com.hugabuga.api.controller;

import com.hugabuga.api.config.SecurityConfig;
import com.hugabuga.api.dto.ReservaDtos.*;
import com.hugabuga.api.service.ReservaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Checkout e gestão da reserva. O parâmetro Solicitante (usuário logado e/ou header X-Responsavel-Email)
 * é conferido pelo service: se a reserva não for dele, 403 SEM_PERMISSAO.
 */
@RestController
@RequestMapping(SecurityConfig.API)
@RequiredArgsConstructor
public class ReservaController {

    private final ReservaService reservaService;

    // ---------- passo 1: criar e quem vem ----------

    /** Cria a reserva PENDENTE e segura a toca por 15 min. Livre: serve para quem não tem conta. */
    @PostMapping("/reservas")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservaRes criar(@Valid @RequestBody CriarReservaReq req, Solicitante s) {
        return reservaService.criar(req, s);
    }

    @PutMapping("/reservas/{codigo}/hospedes")
    public ReservaRes hospedes(@PathVariable String codigo, @Valid @RequestBody HospedesReq req, Solicitante s) {
        return reservaService.salvarHospedes(codigo, req, s);
    }

    /** "+10 min" (uma vez só). */
    @PostMapping("/reservas/{codigo}/prorrogar")
    public ReservaRes prorrogar(@PathVariable String codigo, Solicitante s) {
        return reservaService.prorrogar(codigo, s);
    }

    // ---------- passo 2: extras ----------

    @GetMapping("/extras")
    public List<ExtraRes> extras() {
        return reservaService.listarExtras();
    }

    /** Lista COMPLETA de extras; lista vazia = "Pular extras". */
    @PutMapping("/reservas/{codigo}/extras")
    public ReservaRes salvarExtras(@PathVariable String codigo, @Valid @RequestBody ExtrasReq req, Solicitante s) {
        return reservaService.salvarExtras(codigo, req, s);
    }

    // ---------- passo 3: cupom e ossos ----------

    @PostMapping("/reservas/{codigo}/cupom")
    public ReservaRes aplicarCupom(@PathVariable String codigo, @Valid @RequestBody CupomReq req, Solicitante s) {
        return reservaService.aplicarCupom(codigo, req, s);
    }

    @DeleteMapping("/reservas/{codigo}/cupom")
    public ReservaRes removerCupom(@PathVariable String codigo, Solicitante s) {
        return reservaService.removerCupom(codigo, s);
    }

    /** Exige login (SecurityConfig). */
    @PostMapping("/reservas/{codigo}/ossos")
    public ReservaRes usarOssos(@PathVariable String codigo, @Valid @RequestBody OssosReq req, Solicitante s) {
        return reservaService.usarOssos(codigo, req.quantidade(), s);
    }

    @DeleteMapping("/reservas/{codigo}/ossos")
    public ReservaRes desfazerOssos(@PathVariable String codigo, Solicitante s) {
        return reservaService.desfazerOssos(codigo, s);
    }

    // ---------- consulta ----------

    /** "Reservou sem conta?": exige código E e-mail (query param, como no guia). */
    @GetMapping("/reservas/buscar")
    public ReservaRes buscar(@RequestParam String codigo, @RequestParam String email) {
        return reservaService.buscarSemConta(codigo, email);
    }

    /** Voucher (resumo completo). */
    @GetMapping("/reservas/{codigo}")
    public ReservaRes voucher(@PathVariable String codigo, Solicitante s) {
        return reservaService.voucher(codigo, s);
    }

    /** "Baixar tábua (PDF)". */
    @GetMapping("/reservas/{codigo}/voucher.pdf")
    public ResponseEntity<byte[]> voucherPdf(@PathVariable String codigo, Solicitante s) {
        byte[] pdf = reservaService.voucherPdf(codigo, s); // só chega abaixo se a reserva existe e é do solicitante
        String nome = "tabua-" + codigo.replaceAll("[^A-Za-z0-9-]", "") + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nome).build().toString())
                .body(pdf);
    }

    // ---------- gestão ----------

    @PatchMapping("/reservas/{codigo}/datas")
    public AlterarDatasRes alterarDatas(@PathVariable String codigo, @Valid @RequestBody AlterarDatasReq req, Solicitante s) {
        return reservaService.alterarDatas(codigo, req, s);
    }

    @PostMapping("/reservas/{codigo}/cancelar")
    public CancelarRes cancelar(@PathVariable String codigo, Solicitante s) {
        return reservaService.cancelar(codigo, s);
    }

    @PostMapping("/reservas/{codigo}/checkin")
    public ReservaRes checkin(@PathVariable String codigo, Solicitante s) {
        return reservaService.checkinOnline(codigo, s);
    }

    /** Nota 1 a 5 + texto, só depois da estadia. */
    @PostMapping("/reservas/{codigo}/relato")
    @ResponseStatus(HttpStatus.CREATED)
    public void relato(@PathVariable String codigo, @Valid @RequestBody RelatoReq req, Solicitante s) {
        reservaService.registrarRelato(codigo, req, s);
    }
}
