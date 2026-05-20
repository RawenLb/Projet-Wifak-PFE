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
    .pwa-top-banner {
      position: fixed; top: 0; left: 0; right: 0;
      background: linear-gradient(90deg, #1a3a5c 0%, #1e5fa8 60%, #c8102e 100%);
      color: white; padding: 9px 20px; z-index: 99999;
      display: flex; align-items: center; gap: 12px;
      box-shadow: 0 2px 12px rgba(0,0,0,0.25);
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
    }
    .pwa-top-banner img { width: 34px; height: 34px; border-radius: 7px; background: white; padding: 3px; }
    .pwa-top-banner .pwa-info { flex: 1; }
    .pwa-top-banner .pwa-info strong { display: block; font-size: 13px; font-weight: 700; }
    .pwa-top-banner .pwa-info span { font-size: 11px; opacity: 0.85; }
    .pwa-top-banner .pwa-btn {
      display: flex; align-items: center; gap: 5px;
      background: white; color: #1a3a5c; border: none;
      border-radius: 7px; padding: 6px 14px; font-size: 13px;
      font-weight: 700; cursor: pointer; text-decoration: none;
    }
    .pwa-top-banner .pwa-copy {
      background: rgba(255,255,255,0.15); border: 1px solid rgba(255,255,255,0.3);
      color: white; border-radius: 7px; padding: 6px 12px;
      font-size: 12px; cursor: pointer;
    }
    .pwa-top-banner .pwa-close {
      background: transparent; border: 1px solid rgba(255,255,255,0.3);
      color: white; border-radius: 6px; width: 28px; height: 28px;
      cursor: pointer; font-size: 13px;
    }
    body { padding-top: 52px !important; }
  </style>
</head>
<body class="login-pf">

<!-- PWA Install Banner -->
<div class="pwa-top-banner" id="pwaBanner">
  <img src="${url.resourcesPath}/img/bct-logo.png" alt="Wifak BCT"/>
  <div class="pwa-info">
    <strong>Banque Wifak BCT</strong>
    <span>Installez l'application pour un accès rapide</span>
  </div>
  <button class="pwa-btn" id="pwaInstallBtn" onclick="pwaInstall()">
    ⬇ Installer
  </button>
  <button class="pwa-copy" onclick="pwaCopy()">📋 Copier le lien</button>
  <button class="pwa-close" onclick="document.getElementById('pwaBanner').style.display='none'">✕</button>
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
