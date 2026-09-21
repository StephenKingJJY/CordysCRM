package cn.cordys.crm.order;

import cn.cordys.crm.order.domain.Order;
import cn.cordys.crm.order.dto.request.OrderPaymentAddRequest;
import cn.cordys.crm.order.dto.response.OrderPaymentResponse;
import cn.cordys.crm.order.mapper.ExtOrderPaymentMapper;
import cn.cordys.crm.order.service.OrderPaymentService;
import cn.cordys.crm.system.service.AttachmentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderPaymentServiceTest {
    private final ExtOrderPaymentMapper mapper = mock(ExtOrderPaymentMapper.class);
    private final AttachmentService attachments = mock(AttachmentService.class);
    private final OrderPaymentService service = new OrderPaymentService(mapper, attachments);

    private OrderPaymentResponse payment(String amount, boolean voided) {
        var p = new OrderPaymentResponse(); p.setAmount(new BigDecimal(amount)); p.setVoided(voided); return p;
    }
    private OrderPaymentAddRequest request() {
        var r = new OrderPaymentAddRequest(); r.setId("0123456789abcdef0123456789abcdef");
        r.setAmount(new BigDecimal("6000.00")); r.setReceivedDate(LocalDate.now()); r.setRemark("Shared transfer, allocated to A"); return r;
    }
    private org.springframework.context.MessageSource previousMessages;
    @org.junit.jupiter.api.BeforeEach void translations() {
        previousMessages = (org.springframework.context.MessageSource) org.springframework.test.util.ReflectionTestUtils.getField(
                cn.cordys.common.util.Translator.class, "messageSource");
        new cn.cordys.common.util.Translator().setMessageSource(new org.springframework.context.support.StaticMessageSource());
    }
    @org.junit.jupiter.api.AfterEach void restoreTranslations() {
        new cn.cordys.common.util.Translator().setMessageSource(previousMessages);
    }
    @Test void rejectsMissingOrOtherOrganizationOrderBeforeMutation() {
        assertThrows(cn.cordys.common.exception.GenericException.class,
                () -> service.add("other-order", "org", "u", request(), List.of()));
        verify(mapper, never()).insert(any()); verifyNoInteractions(attachments);
    }
    @Test void cannotVoidPaymentBelongingToAnotherOrder() {
        when(mapper.lockOrder("a", "org")).thenReturn(new Order());
        var p = payment("1", false); p.setOrderId("b"); p.setOrganizationId("org"); when(mapper.get("p")).thenReturn(p);
        assertThrows(cn.cordys.common.exception.GenericException.class,
                () -> service.voidPayment("a", "p", "org", "u", "reason"));
        verify(mapper, never()).voidPayment(any(), any(), any(), any(), anyLong(), any());
    }
    @Test void cannotDownloadUnrelatedReceipt() {
        when(mapper.getOrder("a", "org")).thenReturn(new Order());
        when(mapper.receipts("p", "a", "org")).thenReturn(List.of());
        assertThrows(cn.cordys.common.exception.GenericException.class,
                () -> service.receipt("a", "p", "other-file", "org"));
        verifyNoInteractions(attachments);
    }
    @Test void validatesPaymentInput() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var r = request(); assertTrue(validator.validate(r).isEmpty());
            r.setAmount(new BigDecimal("0")); assertFalse(validator.validate(r).isEmpty());
            r.setAmount(new BigDecimal("-1")); assertFalse(validator.validate(r).isEmpty());
            r.setAmount(new BigDecimal("0.001")); assertFalse(validator.validate(r).isEmpty());
            r.setAmount(new BigDecimal("1.00")); r.setReceivedDate(LocalDate.now().plusDays(1));
            assertFalse(validator.validate(r).isEmpty());
        }
    }
    @Test void rejectsUnsupportedReceiptBeforeInsert() {
        when(mapper.lockOrder("a", "org")).thenReturn(new Order());
        var file = new org.springframework.mock.web.MockMultipartFile("files", "../bad.html", "text/html", new byte[]{1});
        assertThrows(cn.cordys.common.exception.GenericException.class,
                () -> service.add("a", "org", "u", request(), List.of(file)));
        verify(mapper, never()).insert(any()); verifyNoInteractions(attachments);
    }
    @Test void sumsInstallmentsAndExcludesVoidedEntries() {
        var s = OrderPaymentService.summarize(new BigDecimal("10000"),
                List.of(payment("3000", false), payment("5000", false), payment("999", true)));
        assertEquals(new BigDecimal("8000.00"), s.receivedAmount());
        assertEquals(new BigDecimal("2000.00"), s.remainingAmount());
        assertEquals("PARTIAL", s.status()); assertEquals(3, s.records().size());
    }
    @Test void doesNotLoseDecimalCents() {
        var s = OrderPaymentService.summarize(new BigDecimal("0.30"), List.of(payment("0.10", false), payment("0.20", false)));
        assertEquals("PAID", s.status()); assertEquals(new BigDecimal("0.30"), s.receivedAmount());
    }
    @Test void flagsOverpaymentAndNeverDisplaysNegativeOutstanding() {
        var s = OrderPaymentService.summarize(new BigDecimal("100"), List.of(payment("120", false)));
        assertEquals("OVERPAID", s.status()); assertEquals(0, s.remainingAmount().signum());
        assertEquals(new BigDecimal("20.00"), s.overpaidAmount());
    }
    @Test void allVoidedMeansUnpaid() {
        assertEquals("UNPAID", OrderPaymentService.summarize(new BigDecimal("100"), List.of(payment("100", true))).status());
    }
    @Test void retriesSameSubmissionWithoutDuplicatingPaymentOrFiles() {
        when(mapper.lockOrder("a", "org")).thenReturn(new Order());
        var request = request();
        var previous = payment("6000.00", false); previous.setId(request.getId()); previous.setOrderId("a");
        previous.setOrganizationId("org"); previous.setCreateUser("u");
        previous.setReceivedDate(request.getReceivedDate()); previous.setRemark(request.getRemark());
        when(mapper.get(request.getId())).thenReturn(previous);
        assertEquals(request.getId(), service.add("a", "org", "u", request, List.of()));
        verify(mapper, never()).insert(any()); verifyNoInteractions(attachments);
    }
    @Test void recordsOnlyAllocationAndAuditsActorWithoutUpdatingOrder() {
        when(mapper.lockOrder("a", "org")).thenReturn(new Order());
        var request = request(); service.add("a", "org", "u", request, List.of());
        var captor = ArgumentCaptor.forClass(OrderPaymentResponse.class); verify(mapper).insert(captor.capture());
        assertEquals(request.getAmount(), captor.getValue().getAmount()); assertEquals("a", captor.getValue().getOrderId());
        assertEquals("u", captor.getValue().getCreateUser()); assertNotNull(captor.getValue().getCreateTime());
        verifyNoInteractions(attachments);
    }
    @Test void voidKeepsRecordAndAddsReasonAndActor() {
        when(mapper.lockOrder("a", "org")).thenReturn(new Order());
        var p = payment("60", false); p.setOrderId("a"); p.setOrganizationId("org"); when(mapper.get("p")).thenReturn(p);
        service.voidPayment("a", "p", "org", "finance", "  Incorrect allocation  ");
        verify(mapper).voidPayment(eq("p"), eq("a"), eq("org"), eq("finance"), anyLong(), eq("Incorrect allocation"));
        verifyNoInteractions(attachments);
    }
}
