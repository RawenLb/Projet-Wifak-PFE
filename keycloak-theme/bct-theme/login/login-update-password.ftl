<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('password','password-confirm'); section>

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

      <!-- Icon -->
      <div class="wf-reset-icon">
        <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
          <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
          <polyline points="9 12 11 14 15 10"/>
        </svg>
      </div>

      <h2 class="wf-title">Créer votre <em>mot de passe</em></h2>
      <p class="wf-desc">
        Bienvenue sur la plateforme BCT. Définissez un mot de passe sécurisé pour accéder à votre espace.
      </p>

      <form id="kc-passwd-update-form" action="${url.loginAction}" method="post">

        <input type="text" id="username" name="username" value="${username}" autocomplete="username" readonly style="display:none;"/>

        <!-- Nouveau mot de passe -->
        <div class="wf-field">
          <label for="password-new">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
              <rect x="3" y="11" width="18" height="11" rx="2"/>
              <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
            </svg>
            Nouveau mot de passe
          </label>
          <div class="wf-pwd-wrap">
            <input tabindex="1" id="password-new" name="password-new" type="password"
                   autocomplete="new-password"
                   placeholder="••••••••"
                   class="wf-input wf-input-pwd<#if messagesPerField.existsError('password','password-confirm')> wf-err-input</#if>"
            />
            <button type="button" class="wf-eye" onclick="wfEye('password-new','wf-eye-1')" aria-label="Voir le mot de passe">
              <svg id="wf-eye-1" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                <circle cx="12" cy="12" r="3"/>
              </svg>
            </button>
          </div>
          <#if messagesPerField.existsError('password')>
            <span class="wf-field-err">${kcSanitize(messagesPerField.getFirstError('password'))?no_esc}</span>
          </#if>
        </div>

        <!-- Confirmer mot de passe -->
        <div class="wf-field">
          <label for="password-confirm">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
              <rect x="3" y="11" width="18" height="11" rx="2"/>
              <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
              <polyline points="9 16 11 18 15 14"/>
            </svg>
            Confirmer le mot de passe
          </label>
          <div class="wf-pwd-wrap">
            <input tabindex="2" id="password-confirm" name="password-confirm" type="password"
                   autocomplete="new-password"
                   placeholder="••••••••"
                   class="wf-input wf-input-pwd<#if messagesPerField.existsError('password-confirm')> wf-err-input</#if>"
            />
            <button type="button" class="wf-eye" onclick="wfEye('password-confirm','wf-eye-2')" aria-label="Voir le mot de passe">
              <svg id="wf-eye-2" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                <circle cx="12" cy="12" r="3"/>
              </svg>
            </button>
          </div>
          <#if messagesPerField.existsError('password-confirm')>
            <span class="wf-field-err">${kcSanitize(messagesPerField.getFirstError('password-confirm'))?no_esc}</span>
          </#if>
        </div>

        <!-- Indicateur de force -->
        <div class="wf-strength-wrap">
          <div class="wf-strength-bar">
            <div class="wf-strength-fill" id="wf-strength-fill"></div>
          </div>
          <span class="wf-strength-label" id="wf-strength-label">Saisissez un mot de passe</span>
        </div>

        <!-- Règles -->
        <div class="wf-rules">
          <div class="wf-rule" id="rule-len">
            <span class="wf-rule-dot"></span>
            <span>Au moins 8 caractères</span>
          </div>
          <div class="wf-rule" id="rule-upper">
            <span class="wf-rule-dot"></span>
            <span>Une lettre majuscule</span>
          </div>
          <div class="wf-rule" id="rule-num">
            <span class="wf-rule-dot"></span>
            <span>Un chiffre</span>
          </div>
          <div class="wf-rule" id="rule-match">
            <span class="wf-rule-dot"></span>
            <span>Les mots de passe correspondent</span>
          </div>
        </div>

        <#if isAppInitiatedAction??>
          <div class="wf-row" style="margin-bottom:20px;">
            <label class="wf-check">
              <input tabindex="4" type="checkbox" id="logout-sessions" name="logout-sessions" value="on" checked/>
              <span class="wf-checkmark"></span>
              <span>Déconnecter les autres sessions</span>
            </label>
          </div>
        </#if>

        <button tabindex="3" type="submit" id="kc-login" name="login">
          <span>Définir le mot de passe</span>
          <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" class="wf-arrow">
            <path d="M5 12h14M12 5l7 7-7 7"/>
          </svg>
        </button>

        <#if isAppInitiatedAction??>
          <div style="text-align:center; margin-top:16px;">
            <button tabindex="5" type="submit" name="cancel-aia" value="true" class="wf-back-link" style="background:none;border:none;cursor:pointer;">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <path d="M19 12H5M12 5l-7 7 7 7"/>
              </svg>
              Annuler
            </button>
          </div>
        </#if>

      </form>

    </div>

    <script>
      function wfEye(fieldId, iconId) {
        var p = document.getElementById(fieldId);
        var i = document.getElementById(iconId);
        if (p.type === 'password') {
          p.type = 'text';
          i.innerHTML = '<path d="M17.94 17.94A10.07 10.07 0 0112 20c-7 0-11-8-11-8a18.45 18.45 0 015.06-5.94M9.9 4.24A9.12 9.12 0 0112 4c7 0 11 8 11 8a18.5 18.5 0 01-2.16 3.19M1 1l22 22" stroke="currentColor" stroke-width="2" fill="none"/>';
        } else {
          p.type = 'password';
          i.innerHTML = '<path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/>';
        }
      }

      var pwdInput    = document.getElementById('password-new');
      var confirmInput = document.getElementById('password-confirm');
      var fill        = document.getElementById('wf-strength-fill');
      var label       = document.getElementById('wf-strength-label');

      var levels = [
        { color: '#ef4444', width: '20%', text: 'Très faible' },
        { color: '#f97316', width: '40%', text: 'Faible' },
        { color: '#eab308', width: '60%', text: 'Moyen' },
        { color: '#22c55e', width: '80%', text: 'Fort' },
        { color: '#16a34a', width: '100%', text: 'Très fort' }
      ];

      function checkStrength(pwd) {
        var score = 0;
        if (pwd.length >= 8)  score++;
        if (pwd.length >= 12) score++;
        if (/[A-Z]/.test(pwd)) score++;
        if (/[0-9]/.test(pwd)) score++;
        if (/[^A-Za-z0-9]/.test(pwd)) score++;
        return Math.min(score, 5);
      }

      function updateRules() {
        var pwd = pwdInput.value;
        var conf = confirmInput.value;
        setRule('rule-len',   pwd.length >= 8);
        setRule('rule-upper', /[A-Z]/.test(pwd));
        setRule('rule-num',   /[0-9]/.test(pwd));
        setRule('rule-match', pwd.length > 0 && pwd === conf);
      }

      function setRule(id, ok) {
        var el = document.getElementById(id);
        if (!el) return;
        el.classList.toggle('wf-rule-ok', ok);
        el.classList.toggle('wf-rule-fail', !ok && document.getElementById('password-new').value.length > 0);
      }

      pwdInput.addEventListener('input', function() {
        var pwd = pwdInput.value;
        if (!pwd) {
          fill.style.width = '0'; fill.style.background = '';
          label.textContent = 'Saisissez un mot de passe';
        } else {
          var s = checkStrength(pwd) - 1;
          if (s < 0) s = 0;
          fill.style.width = levels[s].width;
          fill.style.background = levels[s].color;
          label.textContent = levels[s].text;
          label.style.color = levels[s].color;
        }
        updateRules();
      });

      confirmInput.addEventListener('input', updateRules);
    </script>

  </#if>

</@layout.registrationLayout>
