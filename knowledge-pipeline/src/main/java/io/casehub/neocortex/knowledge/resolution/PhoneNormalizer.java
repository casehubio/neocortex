package io.casehub.neocortex.knowledge.resolution;

public final class PhoneNormalizer {

    private PhoneNormalizer() {}

    public static String normalize(String phone) {
        if (phone == null || phone.isBlank()) return "";
        String digits = phone.replaceAll("[^0-9+]", "");
        if (digits.startsWith("+44")) digits = "0" + digits.substring(3);
        if (digits.startsWith("0044")) digits = "0" + digits.substring(4);
        return digits;
    }
}
