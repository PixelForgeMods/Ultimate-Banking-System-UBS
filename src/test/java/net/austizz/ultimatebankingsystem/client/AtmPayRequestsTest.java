package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.atm.AtmLcdState;
import net.austizz.ultimatebankingsystem.atm.AtmPayRequestPage;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AtmPayRequestsTest {
    private final UUID request=UUID.randomUUID(), player=UUID.randomUUID();
    private AtmLcdState inbox() {
        var lcd=new AtmLcdState(); lcd.setStatus(AtmLcdState.Status.AUTHENTICATED);
        assertEquals(AtmLcdState.Action.PAY_LIST,lcd.press(21));
        lcd.actionResult(true,"100.00",new AtmPayRequestPage(0,2,List.of(new AtmPayRequestPage.Entry(request,"Alice","12.34","11/08 12:00"))).encode());
        return lcd;
    }
    @Test void payingAndDecliningRequireASecondConfirmation() throws Exception {
        var lcd=inbox(); assertEquals(AtmLcdState.Status.PAY_REQUESTS,lcd.status());
        AtmLcdLayoutTest.preview(lcd,"atm-pay-inbox");
        assertEquals(AtmLcdState.Action.NONE,lcd.press(18));
        assertEquals(AtmLcdState.Status.PAY_CONFIRM,lcd.status());
        assertTrue(lcd.secondary(0).contains("linked account"));
        AtmLcdLayoutTest.preview(lcd,"atm-pay-confirm");
        lcd.press(23); assertEquals(AtmLcdState.Status.PAY_REQUESTS,lcd.status());
        lcd.press(18); assertEquals(AtmLcdState.Action.PAY_ACCEPT,lcd.press(19));
        assertEquals(request,lcd.payTarget());
        assertEquals(AtmLcdState.Action.NONE,lcd.press(19),"Pending request cannot be submitted twice");
        lcd.actionResult(true,"87.66","Payment complete");
        assertEquals(AtmLcdState.Action.PAY_LIST,lcd.press(23));
        lcd=inbox(); lcd.press(19); assertEquals(AtmLcdState.Status.PAY_DECLINE,lcd.status());
        assertEquals(AtmLcdState.Action.PAY_DECLINE,lcd.press(19)); assertEquals(request,lcd.payTarget());
    }
    @Test void playerPickerAndKeypadCreateAnExactCentsRequest() throws Exception {
        var lcd=inbox(); assertEquals(AtmLcdState.Action.PAY_PLAYERS,lcd.press(20));
        lcd.actionResult(true,"100",new AtmPayRequestPage(0,1,List.of(new AtmPayRequestPage.Entry(player,"Bob","",""))).encode());
        AtmLcdLayoutTest.preview(lcd,"atm-pay-players");
        lcd.press(16); assertEquals(AtmLcdState.Status.PAY_AMOUNT,lcd.status());
        assertEquals(AtmLcdState.Action.NONE,lcd.press(11));
        lcd.press(0); lcd.press(1); lcd.press(19); lcd.press(2); lcd.press(4); lcd.press(5);
        assertEquals("12.34",lcd.amount(),"Only two cents digits fit");
        AtmLcdLayoutTest.preview(lcd,"atm-pay-amount");
        lcd.press(11); assertEquals(AtmLcdState.Status.PAY_SEND,lcd.status());
        AtmLcdLayoutTest.preview(lcd,"atm-pay-send");
        var observer=new AtmLcdState(); observer.readPublic(lcd.publicSnapshot(0));
        assertEquals(lcd.primary(0),observer.primary(0)); assertEquals(lcd.amount(),observer.amount());
        assertEquals(AtmLcdState.Action.PAY_SEND,lcd.press(19)); assertEquals(player,lcd.payTarget());
    }
    @Test void pagesEmptyInboxAndMalformedDisplayDataStaySafe() {
        var lcd=inbox(); assertEquals(AtmLcdState.Action.PAY_LIST,lcd.press(22)); assertEquals(1,lcd.requestOption());
        lcd.actionResult(true,"100",new AtmPayRequestPage(1,2,List.of(new AtmPayRequestPage.Entry(UUID.randomUUID(),"Other","5",""))).encode());
        assertEquals(AtmLcdState.Action.PAY_LIST,lcd.press(21)); assertEquals(0,lcd.requestOption());
        lcd.actionResult(true,"100",new AtmPayRequestPage(0,1,List.of()).encode());
        assertEquals(AtmLcdState.Action.NONE,lcd.press(18));
        assertEquals(AtmLcdState.Action.NONE,lcd.press(19));
        assertEquals(AtmLcdState.Action.NONE,lcd.press(22));
        assertEquals(AtmLcdState.Action.PAY_LIST,lcd.press(11));
        assertTrue(AtmPayRequestPage.decode("bad data").entries().isEmpty());
        lcd.actionResult(true,"100",new AtmPayRequestPage(4,6,List.of(new AtmPayRequestPage.Entry(request,"Late page","5",""))).encode());
        var observer=new AtmLcdState();observer.readPublic(lcd.publicSnapshot(0));
        assertEquals(4,observer.billPage(),"Observer must not clamp request pages to withdrawal's three pages");
        var page=new AtmPayRequestPage(0,1,List.of(new AtmPayRequestPage.Entry(request,"Bad\nname\tvalue","1.00","")));
        assertEquals(page,AtmPayRequestPage.decode(page.encode()));
    }
}
