<!DOCTYPE html>
<html lang="fr">
<head>
  <meta charset="UTF-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
  <title>Mot de passe oublié — Wifak Bank</title>
  <link rel="stylesheet" href="${url.resourcesPath}/css/bct-login.css"/>
  <style>
    /* ── Reset page overrides ── */
    .wf-reset-icon {
      width: 64px; height: 64px;
      background: linear-gradient(135deg, #EBF0FD 0%, #dce6fb 100%);
      border-radius: 18px;
      display: flex; align-items: center; justify-content: center;
      margin: 0 auto 20px;
      color: var(--navy);
      animation: fadeIn 0.4s ease 0.15s both;
    }
    .wf-reset-actions {
      display: flex; align-items: center;
      justify-content: space-between;
      gap: 12px; margin-top: 8px;
      animation: fadeIn 0.4s ease 0.5s both;
    }
    .wf-back-link {
      display: inline-flex; align-items: center; gap: 6px;
      font-size: 13.5px; font-weight: 500;
      color: var(--g600) !important; text-decoration: none !important;
      transition: color var(--t);
      white-space: nowrap;
    }
    .wf-back-link:hover { color: var(--navy) !important; }
    .wf-back-link svg { flex-shrink: 0; transition: transform var(--t); }
    .wf-back-link:hover svg { transform: translateX(-3px); }
    .wf-reset-actions #kc-login {
      width: auto; padding: 13px 28px; flex-shrink: 0;
    }
    .wf-info-box {
      display: flex; align-items: flex-start; gap: 10px;
      margin-top: 24px; padding: 14px 16px;
      background: #EFF6FF; border-radius: var(--r);
      border-left: 3px solid #3B82F6;
      font-size: 12.5px; color: #1D4ED8; line-height: 1.5;
      animation: fadeIn 0.4s ease 0.6s both;
    }
    .wf-info-box svg { flex-shrink: 0; margin-top: 1px; color: #3B82F6; }
  </style>
</head>
<body class="login-pf">

<div class="login-pf-page">
  <div class="container">
    <div id="kc-content">

      <!-- ── Header navy ── -->
      <div id="wf-top-bar">
        <div class="wf-brand">
          <div class="wf-logo-box">
            <img src="${url.resourcesPath}/img/bct-logo.png" alt="WIFAK BANK"/>
          </div>
          <div class="wf-brand-text">
            <strong>WIFAK BANK</strong>
            <span>Portail d'Administration BCT</span>
          </div>
        </div>
      </div>

      <!-- ── Body ── -->
      <div id="wf-body">

        <#if message?has_content>
          <div class="wf-alert wf-alert-${message.type}">
            <span class="wf-alert-bar"></span>
            <span>${kcSanitize(message.summary)?no_esc}</span>
          </div>
        </#if>

        <!-- Icon -->
        <div class="wf-reset-icon">
          <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
            <rect x="3" y="11" width="18" height="11" rx="2"/>
            <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
            <circle cx="12" cy="16" r="1.2" fill="currentColor"/>
          </svg>
        </div>

        <h2 class="wf-title">Mot de passe <em>oublié ?</em></h2>
        <p class="wf-desc">
          Saisissez votre identifiant ou adresse e-mail. Nous vous enverrons un lien pour réinitialiser votre mot de passe.
        </p>

        <form id="kc-reset-password-form" action="${url.loginAction}" method="post">

          <div class="wf-field">
            <label for="username">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                <circle cx="12" cy="7" r="4"/>
              </svg>
              <#if !realm.loginWithEmailAllowed>Identifiant
              <#elseif !realm.registrationEmailAsUsername>Identifiant ou e-mail
              <#else>Adresse e-mail
              </#if>
            </label>
            <input tabindex="1" id="username" name="username" type="text"
                   autofocus
                   value="${(auth.attemptedUsername!'')}"
                   placeholder="votre@email.com"
                   class="wf-input"
            />
          </div>

          <div class="wf-reset-actions">
            <a href="${url.loginUrl}" class="wf-back-link" tabindex="3">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <path d="M19 12H5M12 5l-7 7 7 7"/>
              </svg>
              Retour à la connexion
            </a>
            <button tabindex="2" type="submit" id="kc-login" name="login">
              <span>Envoyer le lien</span>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" class="wf-arrow">
                <line x1="22" y1="2" x2="11" y2="13"/>
                <polygon points="22 2 15 22 11 13 2 9 22 2"/>
              </svg>
            </button>
          </div>

        </form>

        <!-- Info box -->
        <div class="wf-info-box">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <line x1="12" y1="16" x2="12" y2="12"/>
            <circle cx="12" cy="8" r="1" fill="currentColor"/>
          </svg>
          <span>Si aucun compte ne correspond à cet identifiant, aucun e-mail ne sera envoyé. Contactez votre administrateur si le problème persiste.</span>
        </div>

      </div>
      <!-- /#wf-body -->

    </div>
    <!-- /#kc-content -->
  </div>
</div>

</body>
</html>
