package com.bancoagricola.ruta.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * DTOs de la API — forma EXACTA que consume el frontend (src/api/types.ts).
 * Records anidados para tener el contrato en un solo lugar. Los campos
 * opcionales se omiten con @JsonInclude(NON_NULL). Los montos viajan como
 * número; las fechas de negocio como etiqueta (`*Label`) + ISO donde aplica.
 */
public final class Api {
  private Api() {}

  public record LoginRequest(String username, String password) {}
  public record AuthSession(String token, String customerId) {}

  public record Customer(String id, String username, String firstName, String displayName,
                         String initials, String avatarColor, String voice,
                         String cardTier, String archetype, boolean firstTimeAtRisk) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Account(String id, String type, String productName, String numberMasked,
                        String numberFull, double balanceAvailable, String currency,
                        boolean isPrimarySource) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Credit(String id, String kind, String name, String numberMasked, String currency,
                       boolean apartable, Double creditLimit, Double available, Integer usedPct,
                       Double payContado, Double installmentAmount, Integer currentDueDay,
                       String operationNumber) {}

  public record Offer(String id, String key, String title, String subtitle, boolean highlighted) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record PayFrequencyOption(String id, String label, String description) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record DateOption(String id, int day, String label, String hint, String availableFromLabel) {}

  public record CreatePaymentDateRequest(String frequencyOptionId, String dateOptionId) {}
  public record PaymentDatePlan(String creditId, int newDay, String effectiveFromLabel,
                                boolean amountUnchanged, boolean termUnchanged) {}

  public record PartInstallment(int index, String label, String dateIso, double amount) {}
  public record PartsOption(int parts, List<PartInstallment> installments, String paysOnLabel, String note) {}
  public record CreateApartadoRequest(String creditId, int parts, String sourceAccountId, boolean automatic) {}
  public record ApartadoPlan(String id, String creditId, int parts, List<PartInstallment> installments,
                             Account sourceAccount, String paysOnLabel, boolean automatic,
                             String firstFullInstallmentLabel) {}

  public record Document(String id, String title, String url) {}
  public record AccountOpeningOffer(String productName, double openingCost, double monthlyCost,
                                    List<String> conditions, List<Document> documents) {}
  public record CreateAccountOpeningRequest(boolean accept, boolean signedWithFaceId) {}

  public record AutopayConfig(String id, String creditId, String accountId, boolean active) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Ruta(boolean active, PaymentDatePlan paymentDate, ApartadoPlan apartado, AutopayConfig autopay) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Notice(String id, String kind, String title, String body, String timeLabel,
                       String dateLabel, boolean read, boolean actionable, String target) {}

  public record RecordMilestone(String label, String dateLabel) {}
  public record Sumando(String id, String label) {}
  public record PaymentRecord(int streakMonths, String nextPlusOneLabel, int progressCurrent,
                              int progressTotal, List<RecordMilestone> milestones,
                              List<Sumando> sumando, String consultsNote) {}

  public record AdvisoryTopic(String id, String productId, String label) {}
  public record AdvisorySummary(String creditId, String statusLabel) {}
  public record ShockContext(String eventLabel, String reassurance, double amount, String currency,
                             String nextDateLabel) {}
  public record Advisor(String id, String name, String sinceLabel, String agency, String initials,
                        String avatarColor) {}
  public record DayOption(String id, String label) {}
  public record TimeOption(String id, String label) {}
  public record CreateAppointmentRequest(String advisorId, String topicId, String dayId, String timeId,
                                         String source) {}
  public record Appointment(String id, String withLabel, String whenLabel, String whereLabel,
                            String aboutLabel, String confirmationNote, String status) {}

  public record ChatSession(String sessionId) {}
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record QuickReply(String id, String label, String nextStep) {}
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ChatMessage(String id, String role, String text, List<QuickReply> quickReplies) {}
  public record SendChatMessageRequest(String text) {}

  // ---- IA del formulario ----
  public record FormOption(String id, String label, Map<String, Object> meta) {}
  public record FormSuggestRequest(Map<String, Object> context, List<FormOption> options) {}
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record FormSuggestResponse(String recommendedOptionId, String rationale, Double confidence) {}
}
