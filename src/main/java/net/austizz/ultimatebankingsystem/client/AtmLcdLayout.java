package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.atm.AtmLcdState;
import java.util.ArrayList;
import java.util.List;

/** Editable welcome artwork in its original 1024 x 768 design coordinates. */
final class AtmLcdLayout {
    static final int WIDTH = 1024, HEIGHT = 768;
    static final float LAYER_SPACING = .0005F;
    static final int TEXT_LAYER = 6;
    enum Typeface { REGULAR, BOLD, ITALIC }
    record Vertex(float x, float y, float z, int color) {}
    record Label(String text, float x, float baseline, float size, float width, int color,
                 Typeface typeface, float rotation, boolean wrap) {}
    record Art(List<Vertex> vertices, List<Label> labels) {}
    static final Art ART = artwork(0);
    static final Art PIN_ART = artwork(1);

    static final Art MENU_ART = artwork(3);
    static final Art PAY_INBOX_ART = artwork(13), PAY_CONFIRM_ART = artwork(6), PAY_AMOUNT_ART = artwork(14);
    static final Art LIMIT_OPTIONS_ART = artwork(15);
    static final Art SETTINGS_ART = artwork(9), SECURITY_ART = artwork(10), INFO_ART = artwork(11), LIMITS_ART = artwork(12);
    static final Art BANK_ART = artwork(2);
    static final Art BILL_CHOICE_ART = artwork(7), BILL_OPTIONS_ART = artwork(8);
    static final Art AMOUNT_ART = artwork(4);
    static final Art DISPENSE_ART = artwork(5), RECEIPT_ART = artwork(6);

    static Art art(AtmLcdState state) {
        if (state.status() == AtmLcdState.Status.DISPENSER) return state.depositReview() ? BILL_CHOICE_ART : state.receiptChoice() || state.depositFeeding() ? RECEIPT_ART : state.cashReady() ? BANK_ART : DISPENSE_ART;
        if (state.status() == AtmLcdState.Status.SETTINGS) return SETTINGS_ART;
        if (state.status() == AtmLcdState.Status.SECURITY) return SECURITY_ART;
        if (state.status() == AtmLcdState.Status.ACCOUNT_INFO) return INFO_ART;
        if (state.status() == AtmLcdState.Status.LIMITS) return LIMITS_ART;
        if(state.status()==AtmLcdState.Status.LIMIT_OPTIONS) return LIMIT_OPTIONS_ART;
        if(state.status()==AtmLcdState.Status.LIMIT_DURATION) return SETTINGS_ART;
        if(state.status()==AtmLcdState.Status.LIMIT_CONFIRM) return RECEIPT_ART;
        if(state.status()==AtmLcdState.Status.PAY_REQUESTS) return PAY_INBOX_ART;
        if(state.status()==AtmLcdState.Status.PAY_PLAYERS) return BILL_OPTIONS_ART;
        if(state.status()==AtmLcdState.Status.PAY_AMOUNT) return PAY_AMOUNT_ART;
        if(state.payScreen()) return PAY_CONFIRM_ART;
        if (state.billChoice()) return BILL_CHOICE_ART;
        if (state.billOptionsScreen()) return BILL_OPTIONS_ART;
        if (state.status() == AtmLcdState.Status.AUTHENTICATED) return MENU_ART;
        if (state.amountScreen()) return AMOUNT_ART;
        if (state.bankScreen()) return BANK_ART;
        return state.pinScreen() ? PIN_ART : ART;
    }
    private AtmLcdLayout() {}

    static List<Label> labels(AtmLcdState state, long now) {
        var labels = new ArrayList<>(art(state).labels);
        if (state.bankScreen()) {
            boolean menuHeading = state.limitScreen() || state.payScreen() || state.settingsScreen() || state.status() == AtmLcdState.Status.AUTHENTICATED || state.billChoice() || state.billOptionsScreen() || state.depositReview();
            labels.add(label(state.title(), 62, menuHeading ? 165 : 200, 60, 900, 0xFF245B40, Typeface.BOLD));
            labels.add(label(state.primary(now), 68, state.depositTotalVisible() ? 280 : menuHeading ? 200 : 255, state.depositTotalVisible() ? 52 : 30, 880, 0xFF294339, state.depositTotalVisible() ? Typeface.BOLD : Typeface.REGULAR));
            if (state.status() == AtmLcdState.Status.AUTHENTICATED) {
                labels.add(label(state.spanish() ? "Configuración  ›" : "Settings  ›", 592, 262, 30, 380, 0xFF29533B, Typeface.BOLD));
                labels.add(label(state.spanish() ? "Solicitudes de pago  ›" : "Pay requests  ›",592,388,30,380,0xFF29533B,Typeface.BOLD));
            }
            if(state.limitScreen()) {
                if(state.status()==AtmLcdState.Status.LIMIT_OPTIONS || state.status()==AtmLcdState.Status.LIMIT_DURATION) {
                    String[] options=state.status()==AtmLcdState.Status.LIMIT_OPTIONS
                            ?state.spanish()?new String[]{"Por retirada","Límite diario","Límite temporal","Restablecer límites"}:new String[]{"Per-withdrawal cap","Daily withdrawal cap","Temporary cap","Reset to bank defaults"}
                            :state.spanish()?new String[]{"15 minutos","1 hora","24 horas"}:new String[]{"15 minutes","1 hour","24 hours"};
                    for(int i=0;i<options.length;i++) labels.add(label("‹  "+options[i],44,262+i*126,28,395,0xFF29533B,Typeface.BOLD));
                    if(state.status()==AtmLcdState.Status.LIMIT_DURATION) labels.add(label("$"+state.amount(),592,350,48,380,0xFF245B40,Typeface.BOLD));
                    else {
                        labels.add(label(state.spanish()?"Los máximos del banco":"Bank/server maximums",592,320,27,380,0xFF5B7064,Typeface.REGULAR));
                        labels.add(label(state.spanish()?"no se pueden cambiar aquí.":"cannot be changed here.",592,359,27,380,0xFF5B7064,Typeface.REGULAR));
                    }
                } else {
                    labels.add(label(state.limitMode()==3?(state.spanish()?"Máximos del banco":"Bank maximums remain in force"):"$"+state.amount(),68,350,state.limitMode()==3?36:62,880,0xFF245B40,Typeface.BOLD));
                    labels.add(label(state.secondary(now),68,450,28,880,0xFF5B7064,Typeface.REGULAR));
                    labels.add(label(state.spanish()?"‹  Confirmar":"‹  Confirm",44,638,30,395,0xFF29533B,Typeface.BOLD));
                }
                labels.add(label(state.spanish()?"Volver  ›":"Back  ›",592,638,30,380,0xFF29533B,Typeface.BOLD));
            } else if(state.payScreen()) {
                var page=state.payPage(); var row=state.payEntry();
                boolean inbox=state.status()==AtmLcdState.Status.PAY_REQUESTS, players=state.status()==AtmLcdState.Status.PAY_PLAYERS;
                if(inbox) {
                    if(row!=null) {
                        labels.add(label((state.spanish()?"De: ":"From: ")+row.name(),68,280,30,420,0xFF294339,Typeface.REGULAR));
                        labels.add(label("$"+row.amount(),68,353,52,420,0xFF245B40,Typeface.BOLD));
                        labels.add(label((state.spanish()?"Creada: ":"Received: ")+row.created(),68,415,24,420,0xFF5B7064,Typeface.REGULAR));
                    }
                    labels.add(label(state.spanish()?"‹  Pagar":"‹  Pay request",44,514,29,395,row==null?0xFF9CAA9E:0xFF29533B,Typeface.BOLD));
                    labels.add(label(state.spanish()?"‹  Rechazar":"‹  Decline",44,638,29,395,row==null?0xFF9CAA9E:0xFF87504A,Typeface.BOLD));
                    labels.add(label(state.spanish()?"Nueva solicitud  ›":"New request  ›",592,262,29,380,0xFF29533B,Typeface.BOLD));
                    labels.add(label(state.spanish()?"ENTER: actualizar":"ENTER: refresh",68,570,22,395,0xFF5B7064,Typeface.REGULAR));
                } else if(players) {
                    for(int i=0;i<page.entries().size();i++) labels.add(label("‹  "+page.entries().get(i).name(),44,262+i*126,30,395,0xFF29533B,Typeface.BOLD));
                } else {
                    String amount=state.status()==AtmLcdState.Status.PAY_AMOUNT?state.amount():state.status()==AtmLcdState.Status.PAY_SEND?state.amount():row==null?"":row.amount();
                    labels.add(label("$ "+(amount.isEmpty()?"0":amount),state.status()==AtmLcdState.Status.PAY_AMOUNT?95:68,state.status()==AtmLcdState.Status.PAY_AMOUNT?410:350,62,820,0xFF245B40,Typeface.BOLD));
                    labels.add(new Label(state.secondary(now),68,510,28,880,0xFF5B7064,Typeface.REGULAR,0,true));
                    labels.add(label(state.status()==AtmLcdState.Status.PAY_AMOUNT?(state.spanish()?"‹  Punto decimal":"‹  Decimal point"):state.status()==AtmLcdState.Status.PAY_CONFIRM?(state.spanish()?"‹  Confirmar pago":"‹  Confirm payment"):state.status()==AtmLcdState.Status.PAY_DECLINE?(state.spanish()?"‹  Rechazar solicitud":"‹  Decline request"):(state.spanish()?"‹  Enviar solicitud":"‹  Send request"),44,638,27,395,0xFF29533B,Typeface.BOLD));
                }
                if(inbox || players) {
                    labels.add(label(state.spanish()?"Anterior  ›":"Previous  ›",592,inbox?388:262,27,380,state.billPage()>0?0xFF29533B:0xFF9CAA9E,Typeface.BOLD));
                    labels.add(label(state.spanish()?"Siguiente  ›":"Next  ›",592,inbox?514:388,27,380,state.billPage()+1<page.pages()?0xFF29533B:0xFF9CAA9E,Typeface.BOLD));
                }
                labels.add(label(state.spanish()?"Volver  ›":"Back  ›",592,638,30,380,0xFF29533B,Typeface.BOLD));
            } else if (state.settingsScreen()) {
                if (state.status() == AtmLcdState.Status.SETTINGS) {
                    String[] options = state.spanish() ? new String[]{"Información", "Seguridad", "Límites"} : new String[]{"Account information", "Security", "Withdrawal limits"};
                    for (int i=0;i<3;i++) labels.add(label("‹  " + options[i],44,262+i*126,30,395,0xFF29533B,Typeface.BOLD));
                } else if (state.status() == AtmLcdState.Status.SECURITY) {
                    labels.add(label(state.spanish() ? "‹  Cambiar PIN" : "‹  Change PIN",44,262,30,395,0xFF29533B,Typeface.BOLD));
                    labels.add(new Label(state.spanish() ? "Elija y repita un PIN de cuatro dígitos con el teclado." : "Choose and repeat a four-digit PIN using the keypad below.",68,372,28,860,0xFF5B7064,Typeface.REGULAR,0,true));
                } else {
                    String[] rows=state.details().split("\n",10);
                    for(int i=0;i<rows.length;i++) {
                        boolean remaining=rows[i].startsWith("Daily remaining:");
                        labels.add(label(rows[i],68,state.status()==AtmLcdState.Status.LIMITS?270+i*33:318+i*38,remaining?28:23,890,0xFF294339,remaining?Typeface.BOLD:Typeface.REGULAR));
                    }
                    if(state.status()==AtmLcdState.Status.ACCOUNT_INFO) {
                        labels.add(label(state.spanish()?"Copiar ID  ›":"Copy account ID  ›",592,262,28,380,0xFF29533B,Typeface.BOLD));
                        labels.add(label(state.spanish()?"‹  Cambiar cuenta principal":"‹  Toggle primary account",44,638,25,395,0xFF29533B,Typeface.BOLD));
                    } else labels.add(label(state.spanish()?"‹  Cambiar límites":"‹  Change limits",44,638,28,395,0xFF29533B,Typeface.BOLD));
                }
                labels.add(label(state.spanish()?"Volver  ›":"Back  ›",592,638,30,380,0xFF29533B,Typeface.BOLD));
                if(!state.notice(now).isEmpty()) labels.add(label(state.notice(now),68,575,23,880,0xFF29533B,Typeface.REGULAR));
            } else if (state.depositReview()) {
                String[] notes = state.secondary(now).split("\n");
                for (int i = 0; i < notes.length; i++) labels.add(label(notes[i], i < 4 ? 90 : 590, 292 + i % 4 * 62, 32, 350, 0xFF294339, Typeface.REGULAR));
            } else if (state.billChoice()) {
                labels.add(label(state.spanish() ? "¿Desea elegir una combinación de billetes?" : "Would you like to choose the bill combination?", 68, 320, 30, 880, 0xFF294339, Typeface.REGULAR));
                labels.add(label(state.secondary(now), 68, 375, 28, 880, 0xFF5B7064, Typeface.REGULAR));
                labels.add(label(state.spanish() ? "Cambiar importe  ›" : "Change amount  ›", 592, 512, 28, 380, 0xFF29533B, Typeface.BOLD));
                labels.add(label(state.spanish() ? "‹  Sí, elegir billetes" : "‹  Yes, choose bills", 44, 638, 30, 395, 0xFF29533B, Typeface.BOLD));
                labels.add(label(state.spanish() ? "No, automático  ›" : "No, automatic  ›", 592, 638, 30, 380, 0xFF29533B, Typeface.BOLD));
            } else if (state.billOptionsScreen()) {
                var options = state.billOptions();
                for (int row = 0; row < 3; row++) {
                    int index = state.billPage() * 3 + row;
                    if (index >= options.size()) break;
                    var option = options.get(index);
                    String heading = index == 0 ? state.spanish() ? "Menos billetes" : "Fewest bills" : "$" + option.largest() + (state.spanish() ? " o menores" : " or smaller");
                    labels.add(label("‹  " + heading + " · " + option.billCount() + (state.spanish() ? " billetes" : " bills"), 44, 262 + row * 126, 26, 395, 0xFF29533B, Typeface.BOLD));
                    labels.add(new Label(option.breakdown(), 48, 288 + row * 126, 18, 395, 0xFF294339, Typeface.REGULAR, 0, true));
                }
                labels.add(label(state.spanish() ? "Anterior  ›" : "Previous  ›", 592, 262, 24, 380, state.billPage() > 0 ? 0xFF29533B : 0xFF9CAA9E, Typeface.BOLD));
                labels.add(label(state.spanish() ? "Siguiente  ›" : "Next  ›", 592, 388, 24, 380, state.billPage() + 1 < state.billPages() ? 0xFF29533B : 0xFF9CAA9E, Typeface.BOLD));
                labels.add(label(state.spanish() ? "Volver  ›" : "Back  ›", 592, 638, 30, 380, 0xFF29533B, Typeface.BOLD));
            } else if (state.status() == AtmLcdState.Status.AUTHENTICATED) {
                String[] options = state.spanish() ? new String[]{"Retirar efectivo", "Depositar efectivo", "Consultar saldo", "Movimientos recientes"}
                        : new String[]{"Withdraw cash", "Deposit cash", "Balance inquiry", "Recent transactions"};
                for (int i = 0; i < 4; i++) labels.add(label("‹  " + options[i], 44, 262 + i * 126, 30, 395, 0xFF29533B, Typeface.BOLD));
            } else if (state.amountScreen()) {
                labels.add(label("$ " + (state.amount().isEmpty() ? "0" : state.amount()), 95, 410, 62, 820, 0xFF245B40, Typeface.BOLD));
                labels.add(label(state.secondary(now), 68, 510, 28, 880, 0xFF5B7064, Typeface.REGULAR));
            } else {
                String[] lines = state.secondary(now).split("\n", 5);
                for (int i = 0; i < Math.min(4, lines.length); i++) labels.add(label(lines[i], 68, 345 + i * 58, 28, 880, 0xFF5B7064, Typeface.REGULAR));
            }
            if (state.depositScreen() && !state.notice(now).isEmpty())
                labels.add(new Label(state.notice(now), 68, 555, 22, 880, 0xFF87504A, Typeface.REGULAR, 0, true));
            if (state.depositFeeding()) {
                labels.add(label(state.spanish() ? "‹  Cancelar depósito" : "‹  Return cash", 44, 638, 30, 395, 0xFF29533B, Typeface.BOLD));
                labels.add(label(state.spanish() ? "Continuar  ›" : "Continue  ›", 592, 638, 30, 380, 0xFF29533B, Typeface.BOLD));
            } else if (state.depositReview()) {
                labels.add(label(state.spanish() ? "Volver a insertar  ›" : "Back to feeding  ›", 592, 512, 28, 380, 0xFF29533B, Typeface.BOLD));
                labels.add(label(state.spanish() ? "‹  Confirmar depósito" : "‹  Confirm deposit", 44, 638, 30, 395, 0xFF29533B, Typeface.BOLD));
                labels.add(label(state.spanish() ? "Devolver efectivo  ›" : "Return cash  ›", 592, 638, 30, 380, 0xFF29533B, Typeface.BOLD));
            } else if (state.receiptChoice()) {
                labels.add(label("‹  Yes, print receipt", 44, 638, 30, 395, 0xFF29533B, Typeface.BOLD));
                labels.add(label("No, thank you (" + state.receiptSeconds() + "s)  ›", 592, 638, 30, 380, 0xFF29533B, Typeface.BOLD));
            } else if (state.cashReady()) {
                labels.add(label(state.spanish() ? "Tomar efectivo  ›" : "Take cash  ›", 592, 638, 30, 380, 0xFF29533B, Typeface.BOLD));
            } else if (!state.limitScreen() && !state.payScreen() && !state.settingsScreen() && state.status() != AtmLcdState.Status.DISPENSER && !state.billChoice() && !state.billOptionsScreen()) labels.add(label(state.status() == AtmLcdState.Status.AUTHENTICATED
                    ? state.spanish() ? "Devolver tarjeta  ›" : "Return card  ›"
                    : state.spanish() ? "Menú principal  ›" : "Main menu  ›", 592, 638, 30, 380, 0xFF29533B, Typeface.BOLD));
        } else if (state.pinScreen()) {
            labels.add(label(state.title(), 62, 218, 64, 900, 0xFF245B40, Typeface.BOLD));
            labels.add(label(state.primary(now), 68, 285, 34, 880, 0xFF294339, Typeface.REGULAR));
            labels.add(label(state.secondary(now), 68, 500, 28, 880, 0xFF5B7064, Typeface.REGULAR));
            for (int i = 0; i < state.maskedPin().length(); i++) {
                labels.add(label("•", 350 + i * 100, 405, 62, 60, 0xFF245B40, Typeface.BOLD));
            }
            labels.add(label((state.changingPin() || state.status()==AtmLcdState.Status.LIMIT_PIN) ? state.spanish() ? "CANCEL: volver" : "CANCEL: back" : state.spanish() ? "CANCEL: devolver tarjeta" : "CANCEL: return card", 64, 572, 24, 310, 0xFF87504A, Typeface.REGULAR));
            labels.add(label(state.spanish() ? "CLEAR: borrar" : "CLEAR: erase", 415, 572, 24, 240, 0xFF77612D, Typeface.REGULAR));
            labels.add(label(state.spanish() ? "ENTER: confirmar" : "ENTER: confirm", 720, 572, 24, 255, 0xFF29533B, Typeface.REGULAR));
        } else {
        labels.add(label(state.title(), 62, 216, 84, 550, 0xFF245B40, Typeface.BOLD));
        labels.add(label(state.primary(now), 68, 300, 40, 550, 0xFF294339, Typeface.REGULAR));
        labels.add(new Label(state.secondary(now), 70, 350, 28, 550, 0xFF5B7064, Typeface.REGULAR, 0, true));
        labels.add(label(state.spanish() ? "Fácil. Seguro. Siempre aquí." : "Easy. Secure. Always here.",
                69, 458, 26, 550, 0xFF5B7064, Typeface.REGULAR));
        labels.add(label(state.spanish() ? "Inserte la tarjeta en el lector" : "Insert card into the reader",
                690, 570, 18, 226, 0xFF526D56, Typeface.REGULAR));
        }
        labels.add(label(state.spanish() ? "SERVICIOS BANCARIOS" : "SELF-SERVICE BANKING",
                704, 55, 24, 278, 0xFFE3EBDD, Typeface.REGULAR));
        labels.add(label(state.spanish() ? "Banca las 24 horas" : "24-hour banking",
                31, 740, 23, 330, 0xFFE3EBDD, Typeface.REGULAR));
        return labels;
    }

    private static Label label(String text, float x, float baseline, float size, float width, int color, Typeface type) {
        return new Label(text, x, baseline, size, width, color, type, 0, false);
    }

    private static Art artwork(int mode) {
        var b = new Builder();
        b.quad(0, 0, WIDTH, HEIGHT, 0xFFF8FAF5, 0xFFF3F6EE, 0xFFEDF2E8, 0xFFF5F8F1);
        b.layer = 1;
        b.rect(0, 0, 1024, 96, 0xFF245B40);
        b.rect(0, 96, 1024, 6, 0xFF7DAC43);
        b.rect(176, 24, 2, 48, 0xFF7DAC43);
        b.text("NCR", 42, 63, 50, 130, 0xFFFFFFFF, Typeface.BOLD);
        if (mode == 1) {
            b.rounded(295, 325, 434, 115, 12, 0xFFE0E8DC);
            for (int i = 0; i < 4; i++) {
                b.layer = 2;
                b.rounded(318 + i * 100, 343, 88, 80, 6, 0xFFBACDB0);
                b.layer = 3;
                b.rounded(320 + i * 100, 345, 84, 76, 4, 0xFFF8FAF5);
            }
            b.layer = 1;
        } else if (mode == 0) {
        b.rect(69, 403, 67, 5, 0xFF8AB65A);
        b.rounded(658, 204, 282, 336, 25, 0xFFE0E8DC);

        b.rotation = -5; b.cx = 800; b.cy = 322;
        b.layer = 2;
        b.rounded(715, 218, 158, 208, 14, 0xFF204D36);
        b.layer = 3;
        b.rounded(717, 220, 154, 204, 12, 0xFF30694C);
        b.layer = 4;
        b.rect(717, 256, 154, 25, 0xFF88AD5D);
        b.rounded(740, 302, 38, 30, 4, 0xFFD8CD94);
        b.layer = 5;
        b.line(752, 302, 752, 332, 1, 0xFFAAA268);
        b.line(740, 317, 778, 317, 1, 0xFFAAA268);
        b.text("BANK CARD", 740, 364, 16, 114, 0xFFE7EFE1, Typeface.BOLD);
        b.text("••••  ••••  4821", 740, 394, 12, 120, 0xFFD3E5C6, Typeface.REGULAR);
        b.rotation = 0;
        b.layer = 2;
        b.rounded(685, 437, 228, 69, 13, 0xFF738A7D);
        b.layer = 3;
        b.rounded(696, 450, 206, 15, 5, 0xFF173E2B);
        b.rounded(702, 472, 194, 9, 2, 0xFF85B94C);
        b.layer = 4;
        b.rect(789, 422, 20, 16, 0xFF699F3F);
        b.triangle(779, 432, 819, 432, 799, 447, 0xFF699F3F);
        }

        if (mode >= 2) {
            if(mode==15) {
                for(int i=0;i<4;i++) {
                    b.layer=1;b.rounded(21,214+i*126,440,74,4,0xFFBACDB0);
                    b.layer=2;b.rounded(23,216+i*126,436,70,2,0xFFE0E8D9);
                }
            }
            if(mode==13) {
                for(int i=0;i<3;i++) {
                    b.layer=1; b.rounded(563,214+i*126,440,74,4,0xFFBACDB0);
                    b.layer=2; b.rounded(565,216+i*126,436,70,2,0xFFE0E8D9);
                }
                for(int i=2;i<4;i++) {
                    b.layer=1; b.rounded(21,214+i*126,440,74,4,0xFFBACDB0);
                    b.layer=2; b.rounded(23,216+i*126,436,70,2,0xFFE0E8D9);
                }
            }
            if (mode == 9 || mode == 10) {
                for(int i=0;i<(mode==9?3:1);i++) {
                    b.layer=1; b.rounded(21,214+i*126,440,74,4,0xFFBACDB0);
                    b.layer=2; b.rounded(23,216+i*126,436,70,2,0xFFE0E8D9);
                }
            }
            if(mode==11) {
                b.layer=1; b.rounded(563,214,440,74,4,0xFFBACDB0);
                b.layer=2; b.rounded(565,216,436,70,2,0xFFE0E8D9);
            }
            if(mode==11 || mode==12) {
                b.layer=1; b.rounded(21,592,440,74,4,0xFFBACDB0);
                b.layer=2; b.rounded(23,594,436,70,2,0xFFE0E8D9);
            }
            if (mode == 3) {
                b.layer = 1; b.rounded(563, 214, 440, 74, 4, 0xFFBACDB0);
                b.layer = 2; b.rounded(565, 216, 436, 70, 2, 0xFFE0E8D9);
                b.layer = 1; b.rounded(563, 340, 440, 74, 4, 0xFFBACDB0);
                b.layer = 2; b.rounded(565, 342, 436, 70, 2, 0xFFE0E8D9);
                for (int i = 0; i < 4; i++) {
                    b.layer = 1; b.rounded(21, 214 + i * 126, 440, 74, 4, 0xFFBACDB0);
                    b.layer = 2; b.rounded(23, 216 + i * 126, 436, 70, 2, 0xFFE0E8D9);
                }
            }
            if (mode == 4 || mode == 14) {
                b.layer = 1; b.rounded(62, 337, 900, 108, 8, 0xFFBACDB0);
                b.layer = 2; b.rounded(65, 340, 894, 102, 6, 0xFFF8FAF5);
            }
            if (mode == 8) {
                for (int i = 0; i < 3; i++) {
                    b.layer = 1; b.rounded(21, 214 + i * 126, 440, 116, 4, 0xFFBACDB0);
                    b.layer = 2; b.rounded(23, 216 + i * 126, 436, 112, 2, 0xFFE0E8D9);
                }
                for (int i = 0; i < 2; i++) {
                    b.layer = 1; b.rounded(563, 214 + i * 126, 440, 74, 4, 0xFFBACDB0);
                    b.layer = 2; b.rounded(565, 216 + i * 126, 436, 70, 2, 0xFFE0E8D9);
                }
            }
            if (mode == 7) {
                b.layer = 1; b.rounded(563, 466, 440, 74, 4, 0xFFBACDB0);
                b.layer = 2; b.rounded(565, 468, 436, 70, 2, 0xFFE0E8D9);
            }
            if (mode == 6 || mode == 7 || mode == 14) {
                b.layer = 1; b.rounded(21, 592, 440, 74, 4, 0xFFBACDB0);
                b.layer = 2; b.rounded(23, 594, 436, 70, 2, 0xFFE0E8D9);
            }
            if (mode != 5) {
            b.layer = 1; b.rounded(563, 592, 440, 74, 4, 0xFFBACDB0);
            b.layer = 2; b.rounded(565, 594, 436, 70, 2, 0xFFE0E8D9);
            }
        } else {
        b.layer = 1;
        b.rounded(21, 592, 306, 74, 4, 0xFFBACDB0);
        b.layer = 2;
        b.rounded(23, 594, 302, 70, 2, 0xFFE0E8D9);
        b.layer = 1;
        b.rounded(697, 592, 306, 74, 4, 0xFFBACDB0);
        b.layer = 2;
        b.rounded(699, 594, 302, 70, 2, 0xFFE0E8D9);
        b.text("English", 75, 638, 32, 230, 0xFF29533B, Typeface.BOLD);
        b.text("Español", 833, 638, 32, 125, 0xFF29533B, Typeface.BOLD);
        b.layer = 3;
        b.line(51, 617, 40, 629, 4, 0xFF59884B);
        b.line(40, 629, 51, 641, 4, 0xFF59884B);
        b.line(975, 617, 986, 629, 4, 0xFF59884B);
        b.line(986, 629, 975, 641, 4, 0xFF59884B);
        }
        b.layer = 1;
        b.rect(0, 700, 1024, 68, 0xFF245B40);
        b.text("NCR SelfServ", 855, 740, 22, 150, 0xFFBFD2B4, Typeface.ITALIC);
        return new Art(List.copyOf(b.vertices), List.copyOf(b.labels));
    }

    private static final class Builder {
        final List<Vertex> vertices = new ArrayList<>();
        final List<Label> labels = new ArrayList<>();
        float rotation, cx, cy;
        int layer;

        Vertex vertex(float x, float y, int color) {
            double radians = Math.toRadians(rotation), c = Math.cos(radians), s = Math.sin(radians);
            return new Vertex((float) (cx + (x - cx) * c - (y - cy) * s),
                    (float) (cy + (x - cx) * s + (y - cy) * c), layer, color);
        }

        void rect(float x, float y, float w, float h, int color) { quad(x, y, w, h, color, color, color, color); }

        void quad(float x, float y, float w, float h, int tl, int tr, int br, int bl) {
            vertices.add(vertex(x, y, tl)); vertices.add(vertex(x, y + h, bl));
            vertices.add(vertex(x + w, y + h, br)); vertices.add(vertex(x + w, y, tr));
        }

        void triangle(float ax, float ay, float bx, float by, float cx, float cy, int color) {
            vertices.add(vertex(ax, ay, color)); vertices.add(vertex(cx, cy, color));
            vertices.add(vertex(bx, by, color)); vertices.add(vertex(bx, by, color));
        }

        void rounded(float x, float y, float w, float h, float r, int color) {
            rect(x + r, y, w - 2 * r, h, color);
            rect(x, y + r, r, h - 2 * r, color);
            rect(x + w - r, y + r, r, h - 2 * r, color);
            for (int corner = 0; corner < 4; corner++) {
                float px = corner == 0 || corner == 3 ? x + w - r : x + r;
                float py = corner < 2 ? y + h - r : y + r;
                for (int segment = 0; segment < 12; segment++) {
                    double a = Math.PI * (corner * .5 + segment / 24.0);
                    double b = a + Math.PI / 24;
                    triangle(px, py, px + r * (float) Math.cos(a), py + r * (float) Math.sin(a),
                            px + r * (float) Math.cos(b), py + r * (float) Math.sin(b), color);
                }
            }
        }

        void line(float x1, float y1, float x2, float y2, float width, int color) {
            double length = Math.hypot(x2 - x1, y2 - y1);
            float dx = (float) ((y2 - y1) / length * width / 2), dy = (float) ((x1 - x2) / length * width / 2);
            vertices.add(vertex(x1 + dx, y1 + dy, color)); vertices.add(vertex(x1 - dx, y1 - dy, color));
            vertices.add(vertex(x2 - dx, y2 - dy, color)); vertices.add(vertex(x2 + dx, y2 + dy, color));
        }

        void text(String text, float x, float y, float size, float width, int color, Typeface type) {
            var anchor = vertex(x, y, color);
            labels.add(new Label(text, anchor.x, anchor.y, size, width, color, type, rotation, false));
        }
    }
}
