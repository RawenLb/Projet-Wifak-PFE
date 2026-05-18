<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=true displayMessage=!messagesPerField.existsError('username'); section>

  <#if section = "header">&nbsp;</#if>

  <#if section = "form">

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

    <div id="wf-body">

      <#if message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
        <div class="wf-alert wf-alert-${message.type}">
          <span class="wf-alert-bar"></span>
          <span>${kcSanitize(message.summary)?no_esc}</span>
        </div>
      </#if>

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
                 class="wf-input<#if messagesPerField.existsError('username')> wf-err-input</#if>"
          />
          <#if messagesPerField.existsError('username')>
            <span class="wf-field-err">${kcSanitize(messagesPerField.getFirstError('username'))?no_esc}</span>
          </#if>
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

      <div class="wf-info-box">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10"/>
          <line x1="12" y1="16" x2="12" y2="12"/>
          <circle cx="12" cy="8" r="1" fill="currentColor"/>
        </svg>
        <span>Si aucun compte ne correspond, aucun e-mail ne sera envoyé. Contactez votre administrateur si le problème persiste.</span>
      </div>

    </div>

  </#if>

  <#if section = "info">
    <#if realm.duplicateEmailsAllowed>
      ${msg("emailInstructionUsername")}
    <#else>
      ${msg("emailInstruction")}
    </#if>
  </#if>

</@layout.registrationLayout>
