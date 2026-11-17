package pe.giova.tender;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class TenderMatchEngine {
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    public Evaluation evaluate(Candidate candidate, Profile profile) {
        String searchable = normalize(String.join(" ", value(candidate.title()), value(candidate.description()),
                value(candidate.buyerId()), value(candidate.buyerName()), value(candidate.region())));

        String excluded = firstMatching(profile.excludeKeywords(), searchable);
        if (excluded != null) {
            return new Evaluation(false, 0, List.of("Descartado por palabra excluida: " + excluded));
        }

        List<String> reasons = new ArrayList<>();
        int score = 0;
        boolean hasFilter = false;

        if (!profile.includeKeywords().isEmpty()) {
            hasFilter = true;
            String matched = firstMatching(profile.includeKeywords(), searchable);
            if (matched == null) {
                return new Evaluation(false, 0, List.of("No coincide ninguna palabra incluida"));
            }
            score += 40;
            reasons.add("Título/descripción coincide: " + matched);
        }
        if (!profile.buyers().isEmpty()) {
            hasFilter = true;
            String buyerText = normalize(value(candidate.buyerId()) + " " + value(candidate.buyerName()));
            String matched = firstMatching(profile.buyers(), buyerText);
            if (matched == null) {
                return new Evaluation(false, 0, List.of("No coincide el comprador configurado"));
            }
            score += 20;
            reasons.add("Comprador coincide: " + matched);
        }
        if (!profile.regions().isEmpty()) {
            hasFilter = true;
            String matched = firstMatching(profile.regions(), normalize(value(candidate.region())));
            if (matched == null) {
                return new Evaluation(false, 0, List.of("No coincide la región configurada"));
            }
            score += 15;
            reasons.add("Región coincide: " + matched);
        }
        if (!profile.classificationCodes().isEmpty()) {
            hasFilter = true;
            String matched = profile.classificationCodes().stream()
                    .filter(code -> candidate.classificationCodes().stream()
                            .anyMatch(actual -> normalize(actual).equals(normalize(code))))
                    .findFirst().orElse(null);
            if (matched == null) {
                return new Evaluation(false, 0, List.of("No coincide el código de clasificación"));
            }
            score += 15;
            reasons.add("Clasificación coincide: " + matched);
        }
        if (profile.minAmount() != null || profile.maxAmount() != null) {
            hasFilter = true;
            if (candidate.amount() == null
                    || profile.minAmount() != null && candidate.amount().compareTo(profile.minAmount()) < 0
                    || profile.maxAmount() != null && candidate.amount().compareTo(profile.maxAmount()) > 0) {
                return new Evaluation(false, 0, List.of("El monto está fuera del rango configurado"));
            }
            score += 5;
            reasons.add("Monto dentro del rango");
        }
        if (profile.closingFrom() != null || profile.closingTo() != null) {
            hasFilter = true;
            LocalDate closingDate = candidate.tenderEnd() == null
                    ? null : candidate.tenderEnd().atZone(ZoneOffset.UTC).toLocalDate();
            if (closingDate == null
                    || profile.closingFrom() != null && closingDate.isBefore(profile.closingFrom())
                    || profile.closingTo() != null && closingDate.isAfter(profile.closingTo())) {
                return new Evaluation(false, 0, List.of("La fecha de cierre está fuera del rango configurado"));
            }
            score += 5;
            reasons.add("Cierre dentro del rango");
        }
        if (!hasFilter) {
            reasons.add("Perfil sin filtros restrictivos");
        }
        return new Evaluation(true, Math.min(100, score), List.copyOf(reasons));
    }

    private static String firstMatching(List<String> values, String searchable) {
        for (String value : values) {
            String normalized = normalize(value);
            if (!normalized.isEmpty() && searchable.contains(normalized)) {
                return value;
            }
        }
        return null;
    }

    /**
     * Los avisos OECE llegan en mayúsculas y con tildes irregulares, así que el cotejo
     * ignora mayúsculas y diacríticos: "TÓNER" y "toner" deben coincidir.
     */
    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String lowercase = value.trim().toLowerCase(Locale.ROOT);
        return DIACRITICS.matcher(Normalizer.normalize(lowercase, Normalizer.Form.NFD)).replaceAll("");
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    public record Candidate(
            String title,
            String description,
            String buyerId,
            String buyerName,
            String region,
            List<String> classificationCodes,
            BigDecimal amount,
            Instant tenderEnd) {
        public Candidate {
            classificationCodes = classificationCodes == null ? List.of() : List.copyOf(classificationCodes);
        }
    }

    public record Profile(
            List<String> includeKeywords,
            List<String> excludeKeywords,
            List<String> buyers,
            List<String> regions,
            List<String> classificationCodes,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            LocalDate closingFrom,
            LocalDate closingTo) {
        public Profile {
            includeKeywords = includeKeywords == null ? List.of() : List.copyOf(includeKeywords);
            excludeKeywords = excludeKeywords == null ? List.of() : List.copyOf(excludeKeywords);
            buyers = buyers == null ? List.of() : List.copyOf(buyers);
            regions = regions == null ? List.of() : List.copyOf(regions);
            classificationCodes = classificationCodes == null ? List.of() : List.copyOf(classificationCodes);
        }
    }

    public record Evaluation(boolean matched, int score, List<String> reasons) {
        public Evaluation {
            reasons = List.copyOf(reasons);
        }
    }
}
