package com.oussama_chatri.productivityx.shared.email;

public final class EmailTemplates {

    private EmailTemplates() {}

    private static final String PRIMARY_COLOR = "#6366F1";
    private static final String BG_COLOR      = "#0F0F14";
    private static final String SURFACE_COLOR = "#1A1A24";
    private static final String TEXT_COLOR    = "#EEEEF5";
    private static final String DIM_COLOR     = "#888899";

    public static String verificationEmail(String firstName, String verificationUrl, String otp) {
        return base(firstName,
                "Verify your email address",
                "Enter this code in the app to verify your account:",
                """
                <div style="text-align:center;margin:24px 0;">
                  <div style="display:inline-block;background:#252533;border-radius:12px;padding:20px 40px;">
                    <p style="margin:0;font-size:13px;color:#888899;letter-spacing:2px;text-transform:uppercase;">
                      Verification Code
                    </p>
                    <p style="margin:8px 0 0;font-size:48px;font-weight:700;letter-spacing:16px;color:#6366F1;font-family:monospace;">
                      %s
                    </p>
                  </div>
                </div>
                <p style="text-align:center;font-size:13px;color:#888899;margin:0 0 8px;">
                  Or click the button below to verify automatically:
                </p>
                """.formatted(otp),
                "Verify Email",
                verificationUrl,
                "This code expires in 24 hours. If you did not create a ProductivityX account, ignore this email."
        );
    }

    /**
     * Password reset email — includes both an OTP (for the mobile app) and a magic link (for web).
     * The OTP is what the Android app expects. The link is a convenience fallback.
     */
    public static String passwordResetEmail(String firstName, String resetUrl, String otp) {
        return base(firstName,
                "Reset your password",
                "We received a request to reset the password for your ProductivityX account.",
                """
                <div style="text-align:center;margin:24px 0;">
                  <div style="display:inline-block;background:#252533;border-radius:12px;padding:20px 40px;">
                    <p style="margin:0;font-size:13px;color:#888899;letter-spacing:2px;text-transform:uppercase;">
                      Reset Code
                    </p>
                    <p style="margin:8px 0 0;font-size:48px;font-weight:700;letter-spacing:16px;color:#6366F1;font-family:monospace;">
                      %s
                    </p>
                  </div>
                </div>
                <p style="text-align:center;font-size:13px;color:#888899;margin:0 0 8px;">
                  Enter this code in the app, or click the button below to reset via link:
                </p>
                """.formatted(otp),
                "Reset Password",
                resetUrl,
                "This code expires in 1 hour. If you did not request a password reset, no action is needed — your account is safe."
        );
    }

    public static String welcomeEmail(String firstName) {
        return base(firstName,
                "Welcome to ProductivityX 🎉",
                "Your workspace is ready. Notes, tasks, calendar, Pomodoro, and AI — all connected.",
                """
                <ul style="color:%s;font-size:14px;line-height:2;padding-left:20px;">
                  <li>Write your first note</li>
                  <li>Create a task and set a due date</li>
                  <li>Start a Pomodoro focus session</li>
                  <li>Ask the AI assistant about your day</li>
                </ul>
                """.formatted(TEXT_COLOR),
                "Open ProductivityX",
                "https://productivityx.app",
                "You are receiving this because you just registered. Welcome aboard!"
        );
    }

    private static String base(String firstName, String subject, String subtitle,
                               String bodyContent, String ctaLabel, String ctaUrl, String footer) {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8"/>
              <meta name="viewport" content="width=device-width,initial-scale=1"/>
              <title>%s</title>
            </head>
            <body style="margin:0;padding:0;background-color:%s;font-family:Arial,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0">
                <tr><td align="center" style="padding:40px 16px;">
                  <table width="560" cellpadding="0" cellspacing="0"
                         style="background-color:%s;border-radius:16px;overflow:hidden;">
                    <tr>
                      <td style="background:linear-gradient(135deg,#6366F1,#8B5CF6);padding:32px;text-align:center;">
                        <span style="font-size:28px;font-weight:700;color:#fff;">PX</span>
                        <p style="margin:8px 0 0;font-size:18px;color:rgba(255,255,255,0.9);font-weight:600;">ProductivityX</p>
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:40px 32px;">
                        <p style="margin:0 0 8px;font-size:14px;color:%s;">Hi %s,</p>
                        <h1 style="margin:0 0 16px;font-size:24px;font-weight:700;color:%s;">%s</h1>
                        <p style="margin:0 0 24px;font-size:15px;line-height:1.6;color:%s;">%s</p>
                        %s
                        <div style="text-align:center;margin:32px 0;">
                          <a href="%s"
                             style="display:inline-block;padding:14px 32px;background-color:%s;
                                    color:#fff;font-size:15px;font-weight:600;border-radius:8px;
                                    text-decoration:none;">%s</a>
                        </div>
                        <p style="margin:24px 0 0;font-size:12px;color:%s;text-align:center;">%s</p>
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:20px 32px;border-top:1px solid #252533;text-align:center;">
                        <p style="margin:0;font-size:12px;color:%s;">
                          © 2026 ProductivityX · Built by Oussama Chatri
                        </p>
                      </td>
                    </tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(
                subject,
                BG_COLOR, SURFACE_COLOR,
                DIM_COLOR, firstName,
                TEXT_COLOR, subject,
                TEXT_COLOR, subtitle,
                bodyContent,
                ctaUrl, PRIMARY_COLOR, ctaLabel,
                DIM_COLOR, footer,
                DIM_COLOR
        );
    }
}