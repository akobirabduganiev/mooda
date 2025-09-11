package tech.nuqta.mooda.infrastructure.service

object EmailTemplates {
    fun verificationEmailHtml(appName: String = "Mooda", actionUrl: String, buttonText: String = "Verify email"): String {
        // Lightweight, responsive HTML email with inline CSS compatible with most clients
        return """
            <!doctype html>
            <html lang=\"en\">
            <head>
              <meta charset=\"UTF-8\" />
              <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />
              <title>$appName – Verify your email</title>
              <style>
                /* Fallback styles for clients that respect <style> */
                .btn { background-color:#4F46E5; color:#ffffff !important; text-decoration:none; padding:14px 22px; border-radius:8px; display:inline-block; font-weight:600 }
                .btn:hover { background-color:#4338CA }
                .text-muted{ color:#6b7280 }
              </style>
            </head>
            <body style=\"margin:0;background-color:#f3f4f6;\">
              <table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" style=\"background-color:#f3f4f6; padding:24px 0;\">
                <tr>
                  <td align=\"center\">
                    <table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" style=\"max-width:560px; background-color:#ffffff; border-radius:12px; overflow:hidden; box-shadow:0 2px 12px rgba(0,0,0,0.05);\">
                      <tr>
                        <td style=\"padding:24px 24px 8px 24px; text-align:center;\">
                          <div style=\"font-size:22px; font-weight:700; color:#111827;\">$appName</div>
                        </td>
                      </tr>
                      <tr>
                        <td style=\"padding:0 24px 8px 24px; text-align:center;\">
                          <div style=\"font-size:18px; font-weight:600; color:#111827;\">Verify your email</div>
                        </td>
                      </tr>
                      <tr>
                        <td style=\"padding:0 24px 6px 24px; text-align:center;\">
                          <div style=\"font-size:14px; line-height:22px; color:#374151;\">
                            Hi! To activate your account, please click the button below.
                          </div>
                        </td>
                      </tr>
                      <tr>
                        <td align=\"center\" style=\"padding:20px 24px 8px 24px;\">
                          <a class=\"btn\" href=\"$actionUrl\" target=\"_blank\" style=\"background-color:#4F46E5;color:#ffffff !important;text-decoration:none;padding:14px 22px;border-radius:8px;display:inline-block;font-weight:600;\">$buttonText</a>
                        </td>
                      </tr>
                      <tr>
                        <td style=\"padding:0 24px 18px 24px; text-align:center;\">
                          <div class=\"text-muted\" style=\"font-size:12px; line-height:18px; color:#6b7280;\">
                            If the button doesn’t work, copy and paste the link below into your browser:
                          </div>
                        </td>
                      </tr>
                      <tr>
                        <td style=\"padding:0 24px 24px 24px;\">
                          <div style=\"word-break:break-all; background:#F9FAFB; border:1px solid #E5E7EB; border-radius:8px; padding:12px; font-size:12px;\">
                            <a href=\"$actionUrl\" target=\"_blank\" style=\"color:#4F46E5; text-decoration:underline;\">$actionUrl</a>
                          </div>
                        </td>
                      </tr>
                      <tr>
                        <td style=\"padding:16px 24px 22px 24px; text-align:center; border-top:1px solid #F3F4F6;\">
                          <div class=\"text-muted\" style=\"font-size:12px; color:#6b7280;\">This message was sent automatically. No reply is necessary.</div>
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
        """.trimIndent()
    }
}
