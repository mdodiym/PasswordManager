package com.dodiy.passwordmanager;

/** Standard identity / access fields offered by "Add field". {group, label, type}. */
final class FieldCatalog {
    private FieldCatalog() {}

    static final String CUSTOM_TEXT = "Custom field (text)";
    static final String CUSTOM_HIDDEN = "Custom field (hidden)";

    static final String[][] FIELDS = {
            {"Account", "Email", Field.EMAIL},
            {"Account", "Phone number", Field.PHONE},
            {"Account", "Account ID / Customer ID", Field.TEXT},
            {"Account", "Member / Membership number", Field.TEXT},
            {"Account", "Backup email", Field.EMAIL},
            {"Account", "Recovery phone", Field.PHONE},
            {"Account", "Login URL", Field.URL},

            {"Security", "PIN", Field.PIN},
            {"Security", "Transaction PIN / TAC", Field.PIN},
            {"Security", "Secure word / security phrase", Field.TEXT},
            {"Security", "Security question", Field.TEXT},
            {"Security", "Security answer", Field.HIDDEN},
            {"Security", "2FA secret key (one-time codes)", Field.TOTP},
            {"Security", "Recovery / backup codes", Field.HIDDEN_MULTILINE},
            {"Security", "Passkey / security key name", Field.TEXT},
            {"Security", "Seed / recovery phrase", Field.HIDDEN_MULTILINE},

            {"Developer & IT access", "API key", Field.HIDDEN},
            {"Developer & IT access", "Client ID", Field.TEXT},
            {"Developer & IT access", "Client secret", Field.HIDDEN},
            {"Developer & IT access", "Tenant ID / domain", Field.TEXT},
            {"Developer & IT access", "Access token", Field.HIDDEN},
            {"Developer & IT access", "Server / host", Field.TEXT},
            {"Developer & IT access", "Port", Field.NUMBER},
            {"Developer & IT access", "Database name", Field.TEXT},
            {"Developer & IT access", "SSH private key", Field.HIDDEN_MULTILINE},
            {"Developer & IT access", "Wi-Fi network name", Field.TEXT},
            {"Developer & IT access", "Wi-Fi password", Field.HIDDEN},
            {"Developer & IT access", "Licence / product key", Field.HIDDEN},

            {"Bank & payment", "Account number", Field.NUMBER},
            {"Bank & payment", "Card number", Field.HIDDEN},
            {"Bank & payment", "Name on card", Field.TEXT},
            {"Bank & payment", "Card expiry (MM/YY)", Field.TEXT},
            {"Bank & payment", "CVV", Field.PIN},
            {"Bank & payment", "Card PIN", Field.PIN},
            {"Bank & payment", "DuitNow ID", Field.TEXT},
            {"Bank & payment", "SWIFT / branch code", Field.TEXT},

            {"Personal ID", "Full name", Field.TEXT},
            {"Personal ID", "MyKad / IC number", Field.HIDDEN},
            {"Personal ID", "Passport number", Field.HIDDEN},
            {"Personal ID", "Passport expiry", Field.DATE},
            {"Personal ID", "Date of birth", Field.DATE},
            {"Personal ID", "Driving licence number", Field.TEXT},
            {"Personal ID", "Tax number (TIN)", Field.TEXT},
            {"Personal ID", "Address", Field.MULTILINE},

            {"Other", "Note", Field.MULTILINE},
            {"Other", CUSTOM_TEXT, Field.TEXT},
            {"Other", CUSTOM_HIDDEN, Field.HIDDEN},
    };
}
