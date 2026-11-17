package net.austizz.ultimatebankingsystem.atm;

public final class AtmLcdState {
    public enum Language { ENGLISH, SPANISH }
    public enum Status { WELCOME, CHECKING, INSERTING, INSERTED, PIN, CREATE_PIN, REPEAT_PIN, VERIFYING_PIN, AUTHENTICATED, PAY_REQUESTS, PAY_PLAYERS, PAY_AMOUNT, PAY_CONFIRM, PAY_DECLINE, PAY_SEND, SETTINGS, ACCOUNT_INFO, SECURITY, LIMITS, LIMIT_OPTIONS, LIMIT_DURATION, LIMIT_CONFIRM, LIMIT_PIN, SET_LIMIT, WITHDRAW, DEPOSIT, BILL_CHOICE, BILL_OPTIONS, PROCESSING, RESULT, DISPENSER, REMOVING }
    public enum Action { NONE, PAY_LIST, PAY_PLAYERS, PAY_SEND, PAY_ACCEPT, PAY_DECLINE, ACCOUNT_INFO, LIMITS, SET_LIMIT, SET_PRIMARY, COPY_ACCOUNT, SUBMIT_PIN, CANCEL, BALANCE, HISTORY, CHECK_WITHDRAW, WITHDRAW, DEPOSIT, OPEN_DEPOSIT, RECEIPT_YES, RECEIPT_NO, PICK_CASH }

    private Language language = Language.ENGLISH;
    private Status status = Status.WELCOME;
    private String notice = "";
    private long noticeUntil;
    private long acceptedAt;
    private String pin = "";
    private String pinMessage = "";
    private String firstPin = "";
    private boolean needsPin, changingPin;
    private String amount = "";
    private String balance = "";
    private String details = "";
    private Action pendingAction = Action.NONE;
    private boolean resultSuccess;
    private int billPage, selectedBillOption = -1;
    private int limitMode = 2, limitDurationMinutes = 60;
    private boolean limitPinSubmitting;
    public int limitMode() { return limitMode; }
    public int limitDurationMinutes() { return limitDurationMinutes; }
    public boolean limitScreen() { return status == Status.LIMIT_OPTIONS || status == Status.LIMIT_DURATION || status == Status.LIMIT_CONFIRM; }
    private AtmDispenseMotion.Phase hardwarePhase = AtmDispenseMotion.Phase.IDLE;
    private String hardwareTitle = "", hardwarePrimary = "", hardwareSecondary = "";
    private int receiptSeconds = 10;
    public int receiptSeconds() { return receiptSeconds; }
    public AtmDispenseMotion.Phase hardwarePhase() { return hardwarePhase; }
    public boolean depositFeeding() { return status == Status.DISPENSER && hardwarePhase == AtmDispenseMotion.Phase.DEPOSIT_FEEDING; }
    public boolean depositTotalVisible() { return status == Status.DISPENSER && (hardwarePhase == AtmDispenseMotion.Phase.DEPOSIT_FEEDING || hardwarePhase == AtmDispenseMotion.Phase.DEPOSIT_INSERTING); }
    public boolean depositReview() { return status == Status.DISPENSER && hardwarePhase == AtmDispenseMotion.Phase.DEPOSIT_REVIEW; }
    public boolean depositScreen() { return status == Status.DISPENSER && hardwarePhase.name().startsWith("DEPOSIT_"); }
    public boolean receiptChoice() { return hardwarePhase == AtmDispenseMotion.Phase.RECEIPT_CHOICE && status == Status.DISPENSER; }
    public boolean cashReady() { return hardwarePhase == AtmDispenseMotion.Phase.CASH_READY && status == Status.DISPENSER; }
    public void showDispenser(AtmDispenseMotion.Phase phase) {
        showDispenser(phase, 0);
    }
    public void showDispenser(AtmDispenseMotion.Phase phase, double age) {
        hardwarePhase = phase;
        receiptSeconds = Math.max(0, 10 - (int) (age / 20));
        if (phase == AtmDispenseMotion.Phase.IDLE) { if (status == Status.DISPENSER) setStatus(Status.WELCOME); return; }
        status = Status.DISPENSER;
        String[] text = switch (phase) {
            case DEPOSIT_OPENING -> new String[]{"Cash deposit", "Opening the bill feeder", "Please wait a moment."};
            case DEPOSIT_FEEDING -> new String[]{"Insert your bills", "Drag cash from your bill tray into the feeder", "Click the LCD or press Continue when you are finished."};
            case DEPOSIT_INSERTING -> new String[]{"Accepting bills", "Please leave the feeder clear", "You can insert more bills in a moment."};
            case DEPOSIT_COUNTING -> new String[]{"Checking your cash", "Counting and verifying your bills", "Your account has not been credited yet."};
            case DEPOSIT_REVIEW -> new String[]{"Cash detected", "Please confirm your deposit", ""};
            case DEPOSIT_RETURN_OPENING, DEPOSIT_RETURNING -> new String[]{"Returning your cash", "Please wait a moment", "Your account has not been credited."};
            case DEPOSIT_RETURN_READY, DEPOSIT_PICKUP -> new String[]{"Take your returned cash", "Your bills are ready at the feeder", "Click the cash to collect your bills."};
            case DEPOSIT_CLOSING -> new String[]{"Please wait", "Closing the bill feeder", ""};
            case WAIT_CARD -> new String[]{"Please wait", "Preparing your withdrawal", "Your card will be returned first."};
            case TAKE_CARD -> new String[]{"Take your card", "Please remove your card", "Click the card reader to take your card."};
            case CARD_REMOVING -> new String[]{"Returning your card", "Please take your card", "Your cash will follow shortly."};
            case WAIT_CASH -> new String[]{"Please wait", "Preparing your cash", "Please leave the cash slot clear."};
            case OPENING, DISPENSING -> new String[]{"Dispensing cash", "Please wait a moment", "The cash shutter is opening."};
            case CASH_READY -> new String[]{"Take your cash", "Your cash is ready", "Click the banknotes or press Take cash."};
            case PICKING_CASH -> new String[]{"Taking your cash", "Please collect all your bills", "Thank you."};
            case CLOSING -> new String[]{"Please wait", "Closing the cash slot", "Your withdrawal is complete."};
            case RECEIPT_CHOICE -> new String[]{"Would you like a receipt?", "Please choose Yes or No", ""};
            case PRINTING -> new String[]{"Printing receipt", "Please wait a moment", "Your receipt is being printed."};
            case RECEIPT_READY -> new String[]{"Take your receipt", "Your receipt is ready", "Click the paper at the receipt slot."};
            case PICKING_RECEIPT -> new String[]{"Thank you", "Please take your receipt", "Have a nice day."};
            default -> new String[]{"", "", ""};
        };
        hardwareTitle = text[0]; hardwarePrimary = text[1]; hardwareSecondary = text[2];
    }

    public void showDepositDetails(long cents, int[] bills) {
        if (!depositScreen()) return;
        String total = "$" + java.math.BigDecimal.valueOf(cents, 2).toPlainString();
        if (depositTotalVisible()) hardwarePrimary = (spanish() ? "Aceptado: " : "Accepted so far: ") + total;
        if (depositReview()) {
            hardwarePrimary = (spanish() ? "Se abonará: " : "Will be credited: ") + total;
            var lines = new java.util.ArrayList<String>();
            for (int i = 0; i < 7; i++) if (bills[i] > 0) lines.add("$" + net.austizz.ultimatebankingsystem.item.BillDenominations.DOLLARS_DESC[i] + " × " + bills[i]);
            hardwareSecondary = String.join("\n", lines);
        }
    }
    public record Display(Status status, Language language, int pinLength, String pinMessage,
                          String amount, String balance, String details, Action action, boolean success, String notice, int billPage, int limitMode, int limitDurationMinutes) {}
    public Display publicSnapshot(long now) {
        return new Display(status, language, pin.length(), pinMessage, amount, balance, details, pendingAction, resultSuccess, notice(now), billPage, limitMode, limitDurationMinutes);
    }
    public void readPublic(Display display) {
        limitMode=Math.clamp(display.limitMode,0,3); limitDurationMinutes=display.limitDurationMinutes;
        status = display.status; language = display.language; billPage = Math.max(0, display.billPage); pendingAction = display.action;
        // Only the number of masked dots crosses the network, never either PIN entry.
        pin = "0".repeat(Math.clamp(display.pinLength, 0, 4)); firstPin = "";
        pinMessage = display.pinMessage; amount = display.amount; balance = display.balance;
        details = display.details; resultSuccess = display.success;
        notice = display.notice; noticeUntil = System.nanoTime() + 4_000_000_000L;
    }

    public void prepareCard(boolean needsPin) { this.needsPin = needsPin; changingPin = false; firstPin = ""; pin = ""; }
    public boolean creatingPin() { return needsPin; }
    public String takeFirstPin() { String value = firstPin; firstPin = ""; return value; }
    public String amount() { return amount; }
    public String balance() { return balance; }
    public String details() { return details; }
    public Action pendingAction() { return pendingAction; }
    public int selectedBillOption() { return selectedBillOption; }
    public int billPage() { return billPage; }
    public boolean billChoice() { return status == Status.BILL_CHOICE; }
    public boolean billOptionsScreen() { return status == Status.BILL_OPTIONS; }
    public java.util.List<AtmBillOptions.Option> billOptions() {
        return amount.matches("[1-9][0-9]{0,6}") ? AtmBillOptions.forAmount(Integer.parseInt(amount)) : java.util.List.of();
    }
    public int billPages() { return Math.max(1, (billOptions().size() + 2) / 3); }
    public boolean amountScreen() { return status == Status.WITHDRAW || status == Status.DEPOSIT || status == Status.SET_LIMIT; }
    public boolean payScreen() { return status.name().startsWith("PAY_"); }
    public AtmPayRequestPage payPage() { return AtmPayRequestPage.decode(details); }
    public AtmPayRequestPage.Entry payEntry() { return payPage().entries().isEmpty() ? null : payPage().entries().getFirst(); }
    public java.util.UUID payTarget() { var row=payEntry(); return pendingAction.name().startsWith("PAY_") && row!=null ? row.id() : new java.util.UUID(0,0); }
    public int requestOption() { return pendingAction==Action.SET_LIMIT ? limitMode : pendingAction==Action.PAY_LIST || pendingAction==Action.PAY_PLAYERS ? billPage : selectedBillOption; }
    private Action payRequest(Action action) { pendingAction=action; status=Status.PROCESSING; return action; }
    public boolean settingsScreen() { return status == Status.SETTINGS || status == Status.ACCOUNT_INFO || status == Status.SECURITY || status == Status.LIMITS; }
    public boolean changingPin() { return changingPin; }
    public String accountId() { return details.split("\n", 2)[0].replace("Account: ", ""); }
    public boolean bankScreen() { return limitScreen() || payScreen() || settingsScreen() || status == Status.DISPENSER || status == Status.AUTHENTICATED || billChoice() || billOptionsScreen() || amountScreen() || status == Status.PROCESSING || status == Status.RESULT; }
    public void actionResult(boolean success, String balance, String message) {
        if (status != Status.PROCESSING) return;
        if (pendingAction==Action.SET_LIMIT && !success && (message.equals("PIN_REQUIRED") || limitPinSubmitting)) {
            pin=""; pinMessage=message.equals("PIN_REQUIRED") ? (spanish()?"Confirme su PIN para aumentar los límites.":"Confirm your PIN to raise withdrawal limits.") : message;
            status=Status.LIMIT_PIN; limitPinSubmitting=false; return;
        }
        limitPinSubmitting=false;
        resultSuccess = success;
        this.balance = balance;
        details = message;
        if (success && (pendingAction==Action.PAY_LIST || pendingAction==Action.PAY_PLAYERS)) {
            billPage=payPage().page(); status=pendingAction==Action.PAY_LIST?Status.PAY_REQUESTS:Status.PAY_PLAYERS; return;
        }
        if (success && pendingAction == Action.CHECK_WITHDRAW) {
            status = Status.BILL_CHOICE; selectedBillOption = -1; billPage = 0; return;
        }
        amount = "";
        if (success && (pendingAction == Action.ACCOUNT_INFO || pendingAction == Action.SET_PRIMARY)) status = Status.ACCOUNT_INFO;
        else if (success && (pendingAction == Action.LIMITS || pendingAction == Action.SET_LIMIT)) status = Status.LIMITS;
        else status = Status.RESULT;
    }


    public Language language() { return language; }
    public Status status() { return status; }
    public boolean spanish() { return language == Language.SPANISH; }
    public void setStatus(Status status) {
        this.status = status;
        if (status == Status.WELCOME || status == Status.REMOVING) { billPage = 0; selectedBillOption = -1; pin = ""; firstPin = ""; pinMessage = ""; amount = ""; balance = ""; details = ""; }
    }

    public void updateCardStatus(Status incoming, long now) {
        if (incoming != Status.INSERTED) { setStatus(incoming); return; }
        if (pinScreen() || bankScreen()) return;
        if (status != Status.INSERTED) { setStatus(Status.INSERTED); acceptedAt = now; }
        if (now - acceptedAt >= 2_000_000_000L) status = needsPin ? Status.CREATE_PIN : Status.PIN;
    }

    public boolean pinScreen() { return status == Status.PIN || status == Status.CREATE_PIN || status == Status.REPEAT_PIN || status == Status.LIMIT_PIN || status == Status.VERIFYING_PIN; }
    public String maskedPin() { return "•".repeat(pin.length()); }
    public String takePin() { String value = pin; pin = ""; return value; }
    public void pinResult(boolean accepted, String message) {
        if (status != Status.VERIFYING_PIN) return;
        pin = "";
        pinMessage = message;
        firstPin = "";
        status = accepted ? (changingPin ? Status.SECURITY : Status.AUTHENTICATED) : needsPin ? Status.CREATE_PIN : Status.PIN;
        if (accepted) { needsPin = false; changingPin = false; }
    }

    public Action press(int button) {
        if (status==Status.LIMIT_PIN) {
            if(button==3 || button==23) {pin=""; status=Status.LIMIT_CONFIRM; return Action.NONE;}
            if(button==7) {pin=""; pinMessage="";}
            else if(button==11) {
                if(pin.length()==4) {limitPinSubmitting=true; pendingAction=Action.SET_LIMIT; status=Status.PROCESSING; return pendingAction;}
                pinMessage=spanish()?"Introduzca los 4 dígitos.":"Please enter all 4 digits.";
            } else {int digit=digit(button); if(digit>=0 && pin.length()<4) pin+=digit;}
            return Action.NONE;
        }
        if (limitScreen()) {
            if(button==3 || button==23) {
                if(status==Status.LIMIT_OPTIONS) {pendingAction=Action.LIMITS;status=Status.PROCESSING;return pendingAction;}
                status=status==Status.LIMIT_DURATION?Status.SET_LIMIT:limitMode==3?Status.LIMIT_OPTIONS:Status.SET_LIMIT; return Action.NONE;
            }
            if(status==Status.LIMIT_OPTIONS && button>=16 && button<=19) {
                limitMode=button-16; amount=""; pin=""; pinMessage="";limitPinSubmitting=false;limitDurationMinutes=60;
                status=limitMode==3?Status.LIMIT_CONFIRM:Status.SET_LIMIT;
            } else if(status==Status.LIMIT_DURATION && button>=16 && button<=18) {
                limitDurationMinutes=new int[]{15,60,1440}[button-16]; status=Status.LIMIT_CONFIRM;
            } else if(status==Status.LIMIT_CONFIRM && button==19) { pendingAction=Action.SET_LIMIT;status=Status.PROCESSING;return pendingAction; }
            return Action.NONE;
        }
        if (status == Status.DISPENSER) {
            if (cashReady() && button == 23) return Action.PICK_CASH;
            if (receiptChoice() && button == 19) return Action.RECEIPT_YES;
            if (receiptChoice() && button == 23) return Action.RECEIPT_NO;
            return Action.NONE;
        }
        if (!bankScreen()) {
            if (button == 19) language = Language.ENGLISH;
            if (button == 23) language = Language.SPANISH;
        }
        if (status == Status.PROCESSING) return Action.NONE;
        if (billChoice()) {
            if (button == 22 || button == 3) { status = Status.WITHDRAW; selectedBillOption = -1; return Action.NONE; }
            if (button == 19) { status = Status.BILL_OPTIONS; billPage = 0; return Action.NONE; }
            if (button == 23) { selectedBillOption = -1; pendingAction = Action.WITHDRAW; status = Status.PROCESSING; return pendingAction; }
            return Action.NONE;
        }
        if (billOptionsScreen()) {
            if (button == 23 || button == 3) { status = Status.BILL_CHOICE; selectedBillOption = -1; return Action.NONE; }
            if (button == 20 && billPage > 0) billPage--;
            if (button == 21 && billPage + 1 < billPages()) billPage++;
            int option = billPage * 3 + button - 16;
            if (button >= 16 && button <= 18 && option < billOptions().size()) {
                selectedBillOption = option; pendingAction = Action.WITHDRAW; status = Status.PROCESSING; return pendingAction;
            }
            return Action.NONE;
        }
        if (payScreen()) {
            if (button==23 || button==3) {
                if(status==Status.PAY_REQUESTS) { status=Status.AUTHENTICATED; return Action.NONE; }
                if(status==Status.PAY_CONFIRM || status==Status.PAY_DECLINE) { status=Status.PAY_REQUESTS; return Action.NONE; }
                if(status==Status.PAY_SEND) { status=Status.PAY_AMOUNT; return Action.NONE; }
                if(status==Status.PAY_AMOUNT) return payRequest(Action.PAY_PLAYERS);
                billPage=0; return payRequest(Action.PAY_LIST);
            }
            var page=payPage();
            if(status==Status.PAY_REQUESTS || status==Status.PAY_PLAYERS) {
                if(button==(status==Status.PAY_REQUESTS?21:20) && billPage>0) { billPage--; return payRequest(status==Status.PAY_REQUESTS?Action.PAY_LIST:Action.PAY_PLAYERS); }
                if(button==(status==Status.PAY_REQUESTS?22:21) && billPage+1<page.pages()) { billPage++; return payRequest(status==Status.PAY_REQUESTS?Action.PAY_LIST:Action.PAY_PLAYERS); }
                if(status==Status.PAY_REQUESTS) {
                    if(button==11) return payRequest(Action.PAY_LIST);
                    if(button==20) { billPage=0; return payRequest(Action.PAY_PLAYERS); }
                    if(payEntry()!=null && (button==18 || button==19)) status=button==18?Status.PAY_CONFIRM:Status.PAY_DECLINE;
                } else if(button>=16 && button<=18 && button-16<page.entries().size()) {
                    details=new AtmPayRequestPage(page.page(),page.pages(),java.util.List.of(page.entries().get(button-16))).encode();
                    amount=""; pinMessage=""; status=Status.PAY_AMOUNT;
                }
            } else if(status==Status.PAY_AMOUNT) {
                if(button==7) { amount=""; pinMessage=""; }
                else if(button==19 && !amount.contains(".")) amount=(amount.isEmpty()?"0":amount)+".";
                else if(button==11) {
                    if(amount.matches("[0-9]{1,7}(\\.[0-9]{0,2})?") && new java.math.BigDecimal(amount).signum()>0) {
                        amount=new java.math.BigDecimal(amount).setScale(2).toPlainString(); status=Status.PAY_SEND; pinMessage="";
                    } else pinMessage=spanish()?"Introduzca un importe válido.":"Enter a positive amount.";
                } else {
                    int digit=digit(button), point=amount.indexOf('.');
                    if(digit>=0 && (point<0?amount.length()<7:amount.length()-point-1<2)) { amount=amount.equals("0")?""+digit:amount+digit; pinMessage=""; }
                }
            } else if(button==19) return payRequest(status==Status.PAY_CONFIRM?Action.PAY_ACCEPT:status==Status.PAY_DECLINE?Action.PAY_DECLINE:Action.PAY_SEND);
            return Action.NONE;
        }
        if(status==Status.RESULT && pendingAction==Action.SET_LIMIT && (button==23 || button==3)) {pendingAction=Action.LIMITS;status=Status.PROCESSING;return pendingAction;}
        if(status==Status.RESULT && pendingAction.name().startsWith("PAY_") && (button==23 || button==3)) return payRequest(Action.PAY_LIST);
        if (settingsScreen()) {
            if (button == 23 || button == 3) { status = status == Status.SETTINGS ? Status.AUTHENTICATED : Status.SETTINGS; return Action.NONE; }
            if (status == Status.SETTINGS) {
                if (button == 17) { status = Status.SECURITY; return Action.NONE; }
                if (button == 16 || button == 18) { pendingAction = button == 16 ? Action.ACCOUNT_INFO : Action.LIMITS; status = Status.PROCESSING; return pendingAction; }
            } else if (status == Status.SECURITY && button == 16) {
                needsPin = true; changingPin = true; pin = ""; firstPin = ""; pinMessage = ""; status = Status.CREATE_PIN;
            } else if (status == Status.ACCOUNT_INFO) {
                if (button == 20) return Action.COPY_ACCOUNT;
                if (button == 19) { pendingAction = Action.SET_PRIMARY; status = Status.PROCESSING; return pendingAction; }
            } else if (status == Status.LIMITS && button == 19) { status = Status.LIMIT_OPTIONS; amount = ""; pinMessage = ""; }
            return Action.NONE;
        }
        if (bankScreen()) {
            if (status == Status.AUTHENTICATED) {
                if (button == 23 || button == 3) return Action.CANCEL;
                if (button == 21) { billPage=0; return payRequest(Action.PAY_LIST); }
                if (button == 20) { status = Status.SETTINGS; return Action.NONE; }
                if (button == 17) { pendingAction = Action.OPEN_DEPOSIT; status = Status.PROCESSING; return pendingAction; }
                if (button == 16) { status = Status.WITHDRAW; amount = ""; pinMessage = ""; }
                if (button == 18 || button == 19) {
                    pendingAction = button == 18 ? Action.BALANCE : Action.HISTORY;
                    status = Status.PROCESSING;
                    return pendingAction;
                }
            } else if (button == 23 || button == 3) { status = status == Status.SET_LIMIT ? Status.LIMIT_OPTIONS : Status.AUTHENTICATED; amount = ""; return Action.NONE; }
            else if (amountScreen()) {
                if (button == 7) { amount = ""; pinMessage = ""; }
                else if (button == 11) {
                    if (!amount.isEmpty() && !amount.equals("0")) {
                        if(status==Status.SET_LIMIT) { status=limitMode==2?Status.LIMIT_DURATION:Status.LIMIT_CONFIRM; return Action.NONE; }
                        pendingAction = status == Status.WITHDRAW ? Action.CHECK_WITHDRAW : Action.DEPOSIT;
                        status = Status.PROCESSING;
                        return pendingAction;
                    }
                    pinMessage = spanish() ? "Introduzca un importe." : "Please enter an amount.";
                } else {
                    int digit = digit(button);
                    if (digit >= 0 && amount.length() < 7) { amount = amount.equals("0") ? "" + digit : amount + digit; pinMessage = ""; }
                }
            }
            return Action.NONE;
        }
        if (button == 3 && changingPin && status != Status.VERIFYING_PIN) { needsPin = false; changingPin = false; pin = ""; firstPin = ""; status = Status.SECURITY; return Action.NONE; }
        if (button == 3 && (pinScreen() || status == Status.INSERTED)) return Action.CANCEL;
        if (status != Status.PIN && status != Status.CREATE_PIN && status != Status.REPEAT_PIN) return Action.NONE;
        if (button == 7) { pin = ""; pinMessage = ""; }
        else if (button == 11) {
            if (pin.length() == 4) {
                pinMessage = "";
                if (status == Status.CREATE_PIN) { firstPin = pin; pin = ""; status = Status.REPEAT_PIN; return Action.NONE; }
                if (status == Status.REPEAT_PIN && !firstPin.equals(pin)) {
                    firstPin = ""; pin = ""; status = Status.CREATE_PIN;
                    pinMessage = spanish() ? "Los PIN no coinciden. Inténtelo de nuevo." : "PINs do not match. Please start again.";
                    return Action.NONE;
                }
                status = Status.VERIFYING_PIN;
                return Action.SUBMIT_PIN;
            }
            pinMessage = spanish() ? "Introduzca los 4 dígitos." : "Please enter all 4 digits.";
        } else {
            int digit = digit(button);
            if (digit >= 0 && pin.length() < 4) { pin += digit; pinMessage = ""; }
        }
        return Action.NONE;
    }

    private static int digit(int button) {
        return switch (button) {
            case 0, 1, 2 -> button + 1;
            case 4, 5, 6 -> button;
            case 8, 9, 10 -> button - 1;
            case 13 -> 0;
            default -> -1;
        };
    }

    public void showNotice(String message, long now) {
        notice = message == null ? "" : message;
        noticeUntil = now + 4_000_000_000L;
    }

    public void clearNotice() { notice = ""; }
    public String notice(long now) { return now < noticeUntil ? notice : ""; }

    public String title() {
        return switch (status) {
            case DISPENSER -> hardwareTitle;
            case INSERTED -> spanish() ? "Tarjeta aceptada" : "Card accepted";
            case REMOVING -> spanish() ? "Un momento" : "Please wait";
            case PIN -> spanish() ? "Introduzca su PIN" : "Enter your PIN";
            case VERIFYING_PIN -> spanish() ? "Comprobando PIN" : "Checking PIN";
            case CREATE_PIN -> spanish() ? "Cree su PIN" : "Create your PIN";
            case REPEAT_PIN -> spanish() ? "Repita su PIN" : "Repeat your PIN";
            case AUTHENTICATED -> spanish() ? "Menú principal" : "Main menu";
            case PAY_REQUESTS -> spanish()?"Solicitudes de pago":"Pay requests";
            case PAY_PLAYERS -> spanish()?"Elegir jugador":"Choose a player";
            case PAY_AMOUNT -> spanish()?"Solicitar dinero":"Request money";
            case PAY_CONFIRM -> spanish()?"Confirmar pago":"Confirm payment";
            case PAY_DECLINE -> spanish()?"Rechazar solicitud":"Decline request?";
            case PAY_SEND -> spanish()?"Enviar solicitud":"Send request?";
            case SETTINGS -> spanish() ? "Configuración" : "Settings";
            case ACCOUNT_INFO -> spanish() ? "Información de cuenta" : "Account information";
            case SECURITY -> spanish() ? "Seguridad" : "Security";
            case LIMITS -> spanish() ? "Límites de retirada" : "Withdrawal limits";
            case LIMIT_OPTIONS -> spanish()?"Cambiar límites":"Change limits";
            case LIMIT_DURATION -> spanish()?"Duración temporal":"Choose duration";
            case LIMIT_CONFIRM -> limitMode==3?(spanish()?"Restablecer límites":"Reset to bank defaults?"):(spanish()?"Confirmar límite":"Confirm limit" );
            case LIMIT_PIN -> spanish()?"Confirme su PIN":"Confirm your PIN";
            case SET_LIMIT -> limitMode==0?(spanish()?"Límite por retirada":"Per-withdrawal cap"):limitMode==1?(spanish()?"Límite diario":"Daily withdrawal cap"):(spanish()?"Límite temporal":"Temporary limit");
            case WITHDRAW -> spanish() ? "Retirar efectivo" : "Withdraw cash";
            case DEPOSIT -> spanish() ? "Depositar efectivo" : "Deposit cash";
            case BILL_CHOICE -> spanish() ? "¿Elegir billetes?" : "Choose your bills?";
            case BILL_OPTIONS -> spanish() ? "Seleccione sus billetes" : "Select your bills";
            case PROCESSING -> spanish() ? "Un momento" : "Please wait";
            case RESULT -> !resultSuccess ? (spanish() ? "No se pudo completar" : "Unable to complete") : switch (pendingAction) {
                case BALANCE -> spanish() ? "Su saldo" : "Your balance";
                case HISTORY -> spanish() ? "Movimientos recientes" : "Recent transactions";
                default -> spanish() ? "Operación completada" : "Transaction complete";
            };
            default -> spanish() ? "Bienvenido" : "Welcome";
        };
    }

    public String primary(long now) {
        if (status == Status.WELCOME && !notice(now).isEmpty()) return spanish() ? "Inténtelo de nuevo" : "Please try again";
        return switch (status) {
            case DISPENSER -> hardwarePrimary;
            case WELCOME -> spanish() ? "Por favor, inserte su tarjeta" : "Please insert your card";
            case CHECKING -> spanish() ? "Comprobando su tarjeta" : "Checking your card";
            case INSERTING -> spanish() ? "Leyendo su tarjeta" : "Reading your card";
            case INSERTED -> spanish() ? "Su tarjeta está insertada" : "Your card is inserted";
            case REMOVING -> !notice(now).isEmpty() ? notice(now) : spanish() ? "Devolviendo su tarjeta" : "Returning your card";
            case PIN -> spanish() ? "Use el teclado de abajo" : "Use the keypad below";
            case VERIFYING_PIN -> spanish() ? "Espere un momento" : "Please wait a moment";
            case CREATE_PIN -> spanish() ? "Elija 4 dígitos en el teclado" : "Choose 4 digits on the keypad";
            case REPEAT_PIN -> spanish() ? "Introduzca el mismo PIN otra vez" : "Enter the same PIN again";
            case AUTHENTICATED -> spanish() ? "Seleccione una operación" : "Please select a transaction";
            case PAY_REQUESTS -> payEntry()==null ? (spanish()?"No hay solicitudes pendientes":"No pending requests") : (spanish()?"Solicitud ":"Request ")+(billPage+1)+" / "+payPage().pages();
            case PAY_PLAYERS -> payPage().entries().isEmpty() ? (spanish()?"No hay otros jugadores conectados":"No other players online") : (spanish()?"Página ":"Page ")+(billPage+1)+" / "+payPage().pages();
            case PAY_AMOUNT, PAY_SEND -> (spanish()?"Solicitar a: ":"Request from: ")+(payEntry()==null?"":payEntry().name());
            case PAY_CONFIRM -> (spanish()?"Pagar a: ":"Pay to: ")+(payEntry()==null?"":payEntry().name());
            case PAY_DECLINE -> (spanish()?"De: ":"From: ")+(payEntry()==null?"":payEntry().name());
            case SETTINGS -> spanish() ? "Seleccione una categoría" : "Choose a settings category";
            case ACCOUNT_INFO -> spanish() ? "Detalles de la cuenta de su tarjeta" : "Your card's linked account";
            case SECURITY -> spanish() ? "Proteja el acceso a su cuenta" : "Manage your account PIN";
            case LIMITS -> spanish() ? "Límites actuales de la cuenta" : "Current account withdrawal limits";
            case LIMIT_OPTIONS -> spanish()?"Seleccione el límite que desea cambiar":"Choose the limit you want to change";
            case LIMIT_DURATION -> spanish()?"¿Cuánto tiempo debe durar el límite?":"How long should the temporary cap apply?";
            case LIMIT_CONFIRM -> limitMode==3?(spanish()?"Se eliminarán sus límites personales.":"Remove your personal and temporary caps."):limitMode==0?(spanish()?"Límite personal por retirada":"Personal per-withdrawal cap"):limitMode==1?(spanish()?"Límite diario personal":"Personal daily cap"):(spanish()?"Límite temporal por retirada":"Temporary per-withdrawal cap");
            case LIMIT_PIN -> spanish()?"Use el teclado de abajo":"Use the keypad below";
            case SET_LIMIT -> spanish() ? "Introduzca dólares enteros" : "Enter the cap in whole dollars";
            case WITHDRAW, DEPOSIT -> spanish() ? "Introduzca dólares enteros" : "Enter an amount in whole dollars";
            case BILL_CHOICE -> (spanish() ? "Retirada: $" : "Withdrawal: $") + amount;
            case BILL_OPTIONS -> "$" + amount + (spanish() ? " · Página " : " · Page ") + (billPage + 1) + "/" + billPages();
            case PROCESSING -> spanish() ? "Procesando su solicitud" : "Processing your request";
            case RESULT -> balance.isEmpty() ? "" : (spanish() ? "Saldo: $" : "Balance: $") + balance;
        };
    }

    public String secondary(long now) {
        if (status == Status.WELCOME && !notice(now).isEmpty()) return notice(now);
        return switch (status) {
            case DISPENSER -> hardwareSecondary;
            case WELCOME -> spanish() ? "para comenzar su operación." : "to begin your transaction.";
            case CHECKING -> spanish() ? "Espere un momento." : "Please wait a moment.";
            case INSERTING -> spanish() ? "Deje la tarjeta en el lector." : "Please leave your card in the reader.";
            case INSERTED -> spanish() ? "Pulse el lector para retirar la tarjeta." : "Click the reader to take your card.";
            case REMOVING -> spanish() ? "Retire su tarjeta del lector." : "Take your card from the reader.";
            case PIN, CREATE_PIN, REPEAT_PIN -> !pinMessage.isEmpty() ? pinMessage : spanish() ? "Pulse ENTER para continuar." : "Press ENTER to continue.";
            case VERIFYING_PIN -> spanish() ? "Comprobando su PIN de forma segura." : "Securely checking your PIN.";
            case PAY_REQUESTS, PAY_PLAYERS -> "";
            case PAY_AMOUNT -> !pinMessage.isEmpty()?pinMessage:spanish()?"ENTER: continuar · CLEAR: borrar":"ENTER: continue · CLEAR: erase";
            case PAY_SEND -> spanish()?"El dinero llegará a la cuenta de esta tarjeta.":"Funds will arrive in this card's linked account.";
            case PAY_CONFIRM -> spanish()?"Se pagará con la cuenta de esta tarjeta.":"Payment comes from this card's linked account.";
            case PAY_DECLINE -> spanish()?"No se moverá dinero.":"No money will be transferred.";
            case AUTHENTICATED, SETTINGS, SECURITY -> "";
            case ACCOUNT_INFO, LIMITS -> details;
            case LIMIT_OPTIONS -> spanish()?"Los máximos del banco no se pueden cambiar.":"Bank/server maximums cannot be changed here.";
            case LIMIT_DURATION -> "$"+amount;
            case LIMIT_CONFIRM -> limitMode==2?(spanish()?"Duración: ":"Duration: ")+limitDurationMinutes+(spanish()?" minutos":" minutes"):limitMode==0?(spanish()?"Finaliza cualquier límite temporal por retirada.":"Any temporary per-withdrawal cap will end."):spanish()?"No cambia el importe ya retirado hoy.":"Today's withdrawn amount will stay unchanged.";
            case LIMIT_PIN -> pinMessage;
            case WITHDRAW, DEPOSIT, SET_LIMIT -> !pinMessage.isEmpty() ? pinMessage : spanish() ? "ENTER: confirmar · CLEAR: borrar" : "ENTER: confirm · CLEAR: erase";
            case BILL_CHOICE -> spanish() ? "No: usar el menor número de billetes." : "No: automatically use the fewest bills.";
            case BILL_OPTIONS -> spanish() ? "Seleccione una combinación exacta." : "Choose an exact bill combination.";
            case PROCESSING -> spanish() ? "No retire su tarjeta." : "Please leave your card inserted.";
            case RESULT -> details;
        };
    }
}
