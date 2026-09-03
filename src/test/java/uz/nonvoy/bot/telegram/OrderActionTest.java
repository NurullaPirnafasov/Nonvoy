package uz.nonvoy.bot.telegram;

import org.junit.jupiter.api.Test;
import uz.nonvoy.bot.entity.enums.OrderStatus;

import static org.junit.jupiter.api.Assertions.*;

class OrderActionTest {

    @Test
    void callbackDataParsedBack() {
        String data = OrderAction.ACCEPT.callbackData(47L);

        assertEquals("ACCEPT:47", data);
        assertEquals(OrderAction.ACCEPT, OrderAction.parse(data).orElseThrow());
        assertEquals(47L, OrderAction.parseOrderId(data).orElseThrow());
    }

    @Test
    void everyActionSurvivesRoundTrip() {
        for (OrderAction action : OrderAction.values()) {
            String data = action.callbackData(1L);
            assertEquals(action, OrderAction.parse(data).orElseThrow(), data);
        }
    }

    @Test
    void actionsMapToStatuses() {
        assertEquals(OrderStatus.ACCEPTED, OrderAction.ACCEPT.getStatus());
        assertEquals(OrderStatus.READY, OrderAction.READY.getStatus());
        assertEquals(OrderStatus.CANCELLED, OrderAction.CANCEL.getStatus());
    }

    /**
     * Eski yoki begona callback data hech qachon exception otmasligi kerak - deploy'dan
     * keyin guruhda avvalgi formatdagi tugmalar qolib ketadi.
     */
    @Test
    void brokenDataIsRejectedWithoutThrowing() {
        String[] broken = {null, "", "   ", "ACCEPT", "ACCEPT:", ":47", "UNKNOWN:47", "CONFIRM:YES"};

        for (String data : broken) {
            assertTrue(OrderAction.parse(data).isEmpty(), "parse: " + data);
        }
    }

    @Test
    void nonNumericOrderIdIsRejected() {
        assertTrue(OrderAction.parseOrderId("ACCEPT:abc").isEmpty());
        assertTrue(OrderAction.parseOrderId("ACCEPT").isEmpty());
        assertTrue(OrderAction.parseOrderId(null).isEmpty());
    }
}