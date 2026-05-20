<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false showAnotherWayIfPresent=true>
<!DOCTYPE html>
<html lang="fr" class="${properties.kcHtmlClass!}">
<head>
  <meta charset="UTF-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
  <title>${msg("loginTitle",(realm.displayName!''))}</title>
  <link rel="icon" href="${url.resourcesPath}/img/bct-logo.png"/>
  <#if properties.styles?has_content>
    <#list properties.styles?split(' ') as style>
      <link href="${url.resourcesPath}/${style}" rel="stylesheet"/>
    </#list>
  </#if>
  <style>
    .pwa-install-banner {
      position: fixed;
      bottom: 20px;
      left: 50%;
      transform: translateX(-50%);
      background: #1e1e1e;
      color: white;
      padding: 12px 16px;
      z-index: 99999;
      box-shadow: 0 4px 24px rgba(0,0,0,0.5);
      border-radius: 14px;
      min-width: 340px;
      max-width: 480px;
      animation: slideUp 0.4s ease-out;
      border: 1px solid rgba(255,255,255,0.08);
    }
    @keyframes slideUp {
      from { transform: translateX(-50%) translateY(100px); opacity: 0; }
      to   { transform: translateX(-50%) translateY(0);     opacity: 1; }
    }
    .pwa-install-content {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .pwa-icon {
      width: 42px;
      height: 42px;
      border-radius: 10px;
      flex-shrink: 0;
      background: white;
      padding: 3px;
    }
    .pwa-text {
      flex: 1;
      display: flex;
      flex-direction: column;
    }
    .pwa-text strong {
      font-size: 14px;
      font-weight: 700;
      color: white;
    }
    .pwa-text span {
      font-size: 11px;
      color: rgba(255,255,255,0.6);
      margin-top: 2px;
    }
    .pwa-actions {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-shrink: 0;
    }
    .pwa-btn-install {
      background: #1a3a5c;
      color: white;
      border: none;
      border-radius: 8px;
      padding: 8px 16px;
      font-size: 13px;
      font-weight: 700;
      cursor: pointer;
      transition: background 0.15s;
      white-space: nowrap;
    }
    .pwa-btn-install:hover {
      background: #2563a8;
    }
    .pwa-btn-copy {
      display: flex;
      align-items: center;
      gap: 5px;
      background: rgba(255,255,255,0.1);
      border: 1px solid rgba(255,255,255,0.2);
      color: white;
      border-radius: 8px;
      padding: 7px 12px;
      font-size: 12px;
      cursor: pointer;
      transition: background 0.15s;
      white-space: nowrap;
    }
    .pwa-btn-copy:hover {
      background: rgba(255,255,255,0.2);
    }
    .pwa-btn-close {
      background: transparent;
      border: none;
      color: rgba(255,255,255,0.5);
      cursor: pointer;
      font-size: 18px;
      padding: 4px;
      line-height: 1;
      flex-shrink: 0;
    }
    .pwa-btn-close:hover {
      color: white;
    }
    body { padding-top: 0 !important; }
  </style>
</head>
<body class="login-pf">

<!-- PWA Install Banner — bottom center -->
<div class="pwa-install-banner" id="pwaBanner">
  <div class="pwa-install-content">
    <img src="${url.resourcesPath}/img/bct-logo.png" alt="Wifak BCT" class="pwa-icon"/>
    <div class="pwa-text">
      <strong>Installer l'application</strong>
      <span>Accès rapide depuis votre écran d'accueil</span>
    </div>
    <div class="pwa-actions">
      <button class="pwa-btn-install" id="pwaInstallBtn" onclick="pwaInstall()">Installer</button>
      <button class="pwa-btn-copy" onclick="pwaCopy()">📋 Copier</button>
      <button class="pwa-btn-close" onclick="document.getElementById('pwaBanner').style.display='none'">✕</button>
    </div>
  </div>
</div>

<script>
  var deferredPrompt = null;
  window.addEventListener('beforeinstallprompt', function(e) {
    e.preventDefault();
    deferredPrompt = e;
    // Mettre en évidence le bouton quand le prompt est disponible
    var btn = document.getElementById('pwaInstallBtn');
    btn.style.background = 'white';
    btn.style.color = '#1a3a5c';
    btn.style.fontWeight = '700';
  });
  function pwaInstall() {
    if (deferredPrompt) {
      deferredPrompt.prompt();
      deferredPrompt.userChoice.then(function(choice) {
        if (choice.outcome === 'accepted') {
          document.getElementById('pwaBanner').style.display = 'none';
        }
        deferredPrompt = null;
      });
    }
  }
  function pwaCopy() {
    var link = window.location.origin.replace('8081', '4200').replace('/realms/bct-realm/protocol/openid-connect/auth', '');
    if (link.indexOf('4200') === -1) link = 'http://localhost:4200';
    navigator.clipboard.writeText(link).then(function() {
      var btn = document.querySelector('.pwa-copy');
      btn.textContent = '✅ Copié !';
      setTimeout(function() { btn.textContent = '📋 Copier le lien'; }, 2000);
    });
  }
</script>

<div class="login-pf-page">
  <div class="container">
    <div id="kc-content">

      <#nested "header">

      <#if displayMessage && message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
        <#-- messages handled inside each template -->
      </#if>

      <#nested "form">

      <#if displayInfo>
        <div id="kc-info">
          <div id="kc-info-wrapper">
            <#nested "info">
          </div>
        </div>
      </#if>

    </div>
  </div>
</div>

</body>
</html>
</#macro>
